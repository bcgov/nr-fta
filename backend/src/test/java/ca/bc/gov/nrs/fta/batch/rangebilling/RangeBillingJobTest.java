package ca.bc.gov.nrs.fta.batch.rangebilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.BatchJob;
import ca.bc.gov.nrs.fta.batch.BatchJobRunner;
import ca.bc.gov.nrs.fta.batch.BatchOutcome;
import ca.bc.gov.nrs.fta.batch.LogCapture;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.GetResult;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.ProcessResult;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.QueuedRequest;
import ca.bc.gov.nrs.fta.integration.EmailSender;
import ca.bc.gov.nrs.fta.user.UserLookupClient;
import ca.bc.gov.nrs.fta.user.UserLookupClient.IdirUser;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("Unit Test | range billing batch (FTA_690_TENURE_APPROVAL)")
class RangeBillingJobTest {

  private final RangeBillingPackage pkg = mock(RangeBillingPackage.class);
  private final EmailSender email = mock(EmailSender.class);
  private final UserLookupClient users = mock(UserLookupClient.class);
  private final RangeBillingJob job =
      new RangeBillingJob(pkg, email, users, mock(BatchJobRunner.class));

  private static final QueuedRequest R1 = new QueuedRequest(101L, 1840L, "IDIR\\JSMITH", 2026);
  private static final QueuedRequest R2 = new QueuedRequest(102L, 1841L, "IDIR\\ADOE", 2026);

  private static Map<String, Object> summaryRow() {
    Map<String, Object> row = new HashMap<>();
    row.put("org_unit_code", "DCK");
    row.put("submit_userid", "IDIR\\JSMITH");
    row.put("calendar_year", new BigDecimal("2026"));
    row.put("curr_time", "02:00:00");
    row.put("curr_date", Timestamp.valueOf("2026-11-26 02:00:00"));
    row.put("total_invoices", new BigDecimal("12"));
    row.put("first_invoice", "R000101");
    row.put("last_invoice", "R000112");
    row.put("invoice_total", new BigDecimal("12345.67"));
    row.put("message_text", null);
    row.put("current_server", "THEDEV");
    return row;
  }

  private void submitterHasEmail(String bareId, String address) {
    when(users.isConfigured()).thenReturn(true);
    when(users.getIdirDetail(bareId))
        .thenReturn(Optional.of(new IdirUser(bareId, "guid", "J", "Smith", address)));
  }

  @Test
  void scheduleAndLockAreWiredToTheJobsProperties() throws Exception {
    Method m = RangeBillingJob.class.getMethod("scheduled");
    Scheduled scheduled = m.getAnnotation(Scheduled.class);
    SchedulerLock lock = m.getAnnotation(SchedulerLock.class);

    assertThat(scheduled.cron()).isEqualTo("${fta.batch.range-billing.cron:-}");
    assertThat(scheduled.zone()).isEqualTo(BatchJob.ZONE);
    assertThat(lock.name()).isEqualTo("range-billing").isEqualTo(job.name());
    assertThat(Duration.parse(lock.lockAtMostFor())).isEqualTo(job.lockAtMostFor());
    assertThat(Duration.parse(lock.lockAtLeastFor())).isEqualTo(job.lockAtLeastFor());
    assertThat(job.legacyName()).isEqualTo("fta_batch_690_tenure_approval");
  }

  @Test
  void eachQueuedRequestIsProcessedAndItsSubmitterGetsTheSummaryPdf() {
    when(pkg.get()).thenReturn(new GetResult("", List.of(R1)));
    when(pkg.process(101L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt", ""));
    when(pkg.summary(101L)).thenReturn(Optional.of(summaryRow()));
    submitterHasEmail("JSMITH", "jane.smith@example.com");

    BatchOutcome outcome;
    try (LogCapture log = new LogCapture(RangeBillingJob.class)) {
      outcome = job.execute();
      assertThat(log.errors()).isEmpty();
    }

    assertThat(outcome.clean()).isTrue();
    assertThat(outcome.summary())
        .isEqualTo("1 request(s): 1 processed, 0 failed; 1 summary report(s) e-mailed");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<EmailSender.Attachment>> files = ArgumentCaptor.forClass(List.class);
    verify(email).send(eq(RangeBillingJob.SUBJECT), any(),
        eq(List.of("jane.smith@example.com")), files.capture());
    EmailSender.Attachment pdf = files.getValue().get(0);
    assertThat(pdf.fileName()).isEqualTo("fta411-ApprovalSummary.pdf");
    assertThat(pdf.contentType()).isEqualTo("application/pdf");
    assertThat(new String(pdf.content(), 0, 5)).isEqualTo("%PDF-");
  }

  @Test
  void aProcessErrorIsLoggedAndTheNextRequestStillRuns() {
    when(pkg.get()).thenReturn(new GetResult("", List.of(R1, R2)));
    when(pkg.process(101L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt",
        "*** No instructions found for organizational unit DCK, run aborted ***"));
    when(pkg.process(102L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt", ""));
    when(pkg.summary(102L)).thenReturn(Optional.of(summaryRow()));
    submitterHasEmail("ADOE", "a.doe@example.com");

    BatchOutcome outcome;
    try (LogCapture log = new LogCapture(RangeBillingJob.class)) {
      outcome = job.execute();
      assertThat(log.errors()).containsExactly("Batch error: An error occurred in the database"
          + " package FTA_690_TENURE_APPROVAL: *** No instructions found for organizational unit"
          + " DCK, run aborted ***");
    }

    assertThat(outcome.clean()).isFalse();
    assertThat(outcome.summary())
        .isEqualTo("2 request(s): 1 processed, 1 failed; 1 summary report(s) e-mailed");
    InOrder order = inOrder(pkg);
    order.verify(pkg).process(101L);
    order.verify(pkg).process(102L);
    verify(pkg, never()).summary(101L);
    verify(email).send(any(), any(), eq(List.of("a.doe@example.com")), anyList());
  }

  @Test
  void aGetErrorIsLoggedAndNothingIsProcessed() {
    when(pkg.get()).thenReturn(new GetResult("fta.web.usr.database.unexpected:x;", List.of()));

    try (LogCapture log = new LogCapture(RangeBillingJob.class)) {
      BatchOutcome outcome = job.execute();

      assertThat(outcome.clean()).isFalse();
      assertThat(log.errors()).containsExactly("Batch error: An error occurred in the database"
          + " package FTA_690_TENURE_APPROVAL: fta.web.usr.database.unexpected:x;");
    }
    verify(pkg, never()).process(anyLong());
  }

  @Test
  void anEmptyQueueDoesNothing() {
    when(pkg.get()).thenReturn(new GetResult("", List.of()));

    assertThat(job.execute())
        .isEqualTo(BatchOutcome.ok("0 request(s): 0 processed, 0 failed; 0 summary report(s)"
            + " e-mailed"));
    verify(email, never()).send(any(), any(), anyList(), anyList());
  }

  @Test
  void noAddressForTheSubmitterSkipsTheReportButNotTheBilling() {
    when(pkg.get()).thenReturn(new GetResult("", List.of(R1)));
    when(pkg.process(101L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt", ""));
    when(users.isConfigured()).thenReturn(true);
    when(users.getIdirDetail("JSMITH")).thenReturn(Optional.empty());

    BatchOutcome outcome = job.execute();

    assertThat(outcome.clean()).isTrue();
    assertThat(outcome.summary()).endsWith("0 summary report(s) e-mailed");
    verify(email, never()).send(any(), any(), anyList(), anyList());
  }

  @Test
  void aFailedSummaryEmailIsLoggedNotRaised() {
    when(pkg.get()).thenReturn(new GetResult("", List.of(R1)));
    when(pkg.process(101L)).thenReturn(new ProcessResult("fta411-ApprovalSummary.rpt", ""));
    when(pkg.summary(101L)).thenReturn(Optional.of(summaryRow()));
    submitterHasEmail("JSMITH", "jane.smith@example.com");
    doThrow(new RuntimeException("mail relay down")).when(email).send(any(), any(), anyList(), anyList());

    try (LogCapture log = new LogCapture(RangeBillingJob.class)) {
      BatchOutcome outcome = job.execute();

      assertThat(outcome.clean()).isTrue();
      assertThat(outcome.summary()).endsWith("0 summary report(s) e-mailed");
      // A failed summary e-mail is a warning, not a batch error.
      assertThat(log.errors()).isEmpty();
    }
  }

  @Test
  void bareIdirStripsTheDirectory() {
    assertThat(RangeBillingJob.bareIdir("IDIR\\jsmith")).isEqualTo("JSMITH");
    assertThat(RangeBillingJob.bareIdir("jsmith")).isEqualTo("JSMITH");
    assertThat(RangeBillingJob.bareIdir("BCEID\\x")).isNull();
    assertThat(RangeBillingJob.bareIdir(" ")).isNull();
  }
}
