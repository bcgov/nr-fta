package ca.bc.gov.nrs.fta.batch.rangereplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.BatchJob;
import ca.bc.gov.nrs.fta.batch.BatchJobRunner;
import ca.bc.gov.nrs.fta.batch.BatchOutcome;
import ca.bc.gov.nrs.fta.batch.LogCapture;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Types;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("Unit Test | range replacement batch (FTA_102_RANGE_REPLACE)")
class RangeReplaceJobTest {

  private final RangeReplacePackage pkg = mock(RangeReplacePackage.class);
  private final RangeReplaceJob job = new RangeReplaceJob(pkg, mock(BatchJobRunner.class));

  @Test
  void scheduleAndLockAreWiredToTheJobsProperties() throws Exception {
    Method m = RangeReplaceJob.class.getMethod("scheduled");
    Scheduled scheduled = m.getAnnotation(Scheduled.class);
    SchedulerLock lock = m.getAnnotation(SchedulerLock.class);

    assertThat(scheduled.cron()).isEqualTo("${fta.batch.range-replace.cron:-}");
    assertThat(scheduled.zone()).isEqualTo(BatchJob.ZONE);
    assertThat(lock.name()).isEqualTo("range-replace").isEqualTo(job.name());
    assertThat(Duration.parse(lock.lockAtMostFor())).isEqualTo(job.lockAtMostFor());
    assertThat(Duration.parse(lock.lockAtLeastFor())).isEqualTo(job.lockAtLeastFor());
    assertThat(job.legacyName()).isEqualTo("fta_batch_102_range_replace");
  }

  @Test
  void aCleanRunLogsNoError() {
    when(pkg.mainline()).thenReturn("");

    try (LogCapture log = new LogCapture(RangeReplaceJob.class)) {
      assertThat(job.execute().clean()).isTrue();
      assertThat(log.errors()).isEmpty();
    }
  }

  @Test
  void aPackageErrorIsLoggedAtError() {
    String err = "fta.web.usr.database.record.modified:FTA_102_RANGE_REPLACE,process_replacements,"
        + "TENURE_TERM;";
    when(pkg.mainline()).thenReturn(err);

    try (LogCapture log = new LogCapture(RangeReplaceJob.class)) {
      BatchOutcome outcome = job.execute();

      assertThat(outcome).isEqualTo(BatchOutcome.withErrors(err));
      assertThat(log.errors()).containsExactly(
          "Batch error: An error occurred in the database package FTA_102_RANGE_REPLACE: " + err);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void thePackageIsCalledWithNullTraceOnAnAutoCommitCall() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CallableStatement cs = mock(CallableStatement.class);
    when(jdbc.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenAnswer(inv -> inv.<CallableStatementCallback<Object>>getArgument(1)
            .doInCallableStatement(cs));
    when(cs.getString(2)).thenReturn(null);

    assertThat(new RangeReplacePackage(jdbc).mainline()).isEmpty();
    assertThat(RangeReplacePackage.CALL)
        .contains("the.fta_102_range_replace.mainline(p_trace_ind => ?, p_error_message => ?)");
    verify(cs).setNull(1, Types.VARCHAR);
    verify(cs).setNull(2, Types.VARCHAR);
    verify(cs).registerOutParameter(2, Types.VARCHAR);
  }
}
