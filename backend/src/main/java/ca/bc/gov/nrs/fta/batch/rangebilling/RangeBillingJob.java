package ca.bc.gov.nrs.fta.batch.rangebilling;

import ca.bc.gov.nrs.fta.batch.BatchJob;
import ca.bc.gov.nrs.fta.batch.BatchJobRunner;
import ca.bc.gov.nrs.fta.batch.BatchOutcome;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.GetResult;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.ProcessResult;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.QueuedRequest;
import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import ca.bc.gov.nrs.fta.integration.EmailSender;
import ca.bc.gov.nrs.fta.user.UserLookupClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Range billing — legacy batch {@code fta_batch_690_tenure_approval}
 * ({@code DBProcTask.processFtaBatch690TenureApproval}). Drains the
 * {@code FTA_RANGE_BILL_REQUEST} queue that FTA690 Tenure Approval Submission fills
 * ({@code BillingApprovalService}), creating each request's invoice transactions:
 *
 * <ol>
 *   <li>{@code GET} the unprocessed requests. An error is logged at ERROR.
 *   <li>For each, {@code PROCESS} it in its own transaction. With an error: roll back, log the
 *       error at ERROR, go on to the next (the package has already marked
 *       the row processed, unsuccessful, with the message). Without: commit, then e-mail the
 *       FTA411 Approval Summary PDF to the user who submitted the request.
 * </ol>
 *
 * <p>Legacy e-mailed those errors to support ({@code DBProcTask.deliverErrorEmail}); here they
 * are logged at ERROR for monitoring to alert on.
 *
 * <p>The summary went, in legacy, through the WebADE report queue ({@code Report.submit}: Crystal
 * {@code FTA/fta411-ApprovalSummary.rpt}, PDF, parameter {@code P_RANGE_BILL_REQUEST_ID}) to the
 * submitter's WebADE e-mail address. Here: the Jasper port ({@code FTA411_ApprovalSummary.jrxml})
 * e-mailed over SMTP ({@link EmailSender}), the address from nr-user-lookup-api. When there is no address, or the
 * report or e-mail fails, it is logged and the batch carries on (legacy logged a failed report
 * submission; an empty address went nowhere). Legacy's FTA410 approval report was already
 * switched off in legacy ("not required as per Ron Robb") and is not ported.
 *
 * <p>Schedule: not in any legacy file (Automate's schedule was kept on the Automate server; the
 * package calls it "the batch job ... for the night"), so {@code fta.batch.range-billing.cron}
 * defaults to disabled.
 */
@Component
public class RangeBillingJob implements BatchJob {

  private static final Logger LOG = LoggerFactory.getLogger(RangeBillingJob.class);

  static final String NAME = "range-billing";
  static final String LEGACY_NAME = "fta_batch_690_tenure_approval";
  static final String LOCK_AT_MOST = "PT4H";
  static final String LOCK_AT_LEAST = "PT1M";

  static final String TEMPLATE = "reports/FTA411_ApprovalSummary.jrxml";
  static final String ATTACHMENT_NAME = "fta411-ApprovalSummary.pdf";
  static final String SUBJECT = "FTA411 Approval Summary";

  private final RangeBillingPackage pkg;
  private final EmailSender email;
  private final UserLookupClient users;
  private final BatchJobRunner runner;

  public RangeBillingJob(
      RangeBillingPackage pkg,
      EmailSender email,
      UserLookupClient users,
      BatchJobRunner runner) {
    this.pkg = pkg;
    this.email = email;
    this.users = users;
    this.runner = runner;
  }

  @Scheduled(cron = "${fta.batch.range-billing.cron:-}", zone = BatchJob.ZONE)
  @SchedulerLock(name = NAME, lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
  public void scheduled() {
    runner.runScheduled(this);
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String legacyName() {
    return LEGACY_NAME;
  }

  @Override
  public Duration lockAtMostFor() {
    return Duration.parse(LOCK_AT_MOST);
  }

  @Override
  public Duration lockAtLeastFor() {
    return Duration.parse(LOCK_AT_LEAST);
  }

  @Override
  public BatchOutcome execute() {
    GetResult queue = pkg.get();
    if (!queue.errorMessage().isEmpty()) {
      LOG.error(BatchJob.PACKAGE_ERROR, RangeBillingPackage.PACKAGE, queue.errorMessage());
      return BatchOutcome.withErrors("GET failed: " + queue.errorMessage());
    }
    int processed = 0;
    int failed = 0;
    int reports = 0;
    for (QueuedRequest request : queue.requests()) {
      LOG.info("Range billing: processing request {} (org unit {}, year {}, submitted by {})",
          request.rangeBillRequestId(), request.orgUnitNo(), request.calendarYear(),
          request.submitUserid());
      ProcessResult result = pkg.process(request.rangeBillRequestId());
      if (!result.errorMessage().isEmpty()) {
        failed++;
        LOG.warn("Range billing: request {} failed, rolled back", request.rangeBillRequestId());
        LOG.error(BatchJob.PACKAGE_ERROR, RangeBillingPackage.PACKAGE, result.errorMessage());
        continue;
      }
      processed++;
      if (sendSummary(request, result.summaryReport())) {
        reports++;
      }
    }
    String summary = "%d request(s): %d processed, %d failed; %d summary report(s) e-mailed"
        .formatted(queue.requests().size(), processed, failed, reports);
    return failed == 0 ? BatchOutcome.ok(summary) : BatchOutcome.withErrors(summary);
  }

  /** E-mails the FTA411 summary to the submitter; false (logged) when it could not be sent. */
  boolean sendSummary(QueuedRequest request, String legacyReportName) {
    long id = request.rangeBillRequestId();
    try {
      Optional<String> to = submitterEmail(request.submitUserid());
      if (to.isEmpty()) {
        LOG.warn("Range billing: no e-mail address for {}; the summary report of request {} was"
            + " not sent", request.submitUserid(), id);
        return false;
      }
      Map<String, Object> row = pkg.summary(id).orElseThrow(
          () -> new IllegalStateException("request " + id + " not found"));
      Map<String, Object> params = new HashMap<>();
      params.put("p_range_bill_request_id", Long.toString(id));
      byte[] pdf = JasperTemplates.pdf(TEMPLATE, params, List.of(row));
      email.send(SUBJECT, body(row, id), List.of(to.get()),
          List.of(new EmailSender.Attachment(ATTACHMENT_NAME, "application/pdf", pdf)));
      LOG.info("Range billing: summary report ({}) of request {} e-mailed to {}",
          legacyReportName, id, request.submitUserid());
      return true;
    } catch (RuntimeException e) {
      LOG.warn("Range billing: the summary report of request {} was not sent: {}",
          id, e.getMessage());
      return false;
    }
  }

  private Optional<String> submitterEmail(String submitUserid) {
    String bare = bareIdir(submitUserid);
    if (bare == null || !users.isConfigured()) {
      return Optional.empty();
    }
    return users.getIdirDetail(bare)
        .map(UserLookupClient.IdirUser::email)
        .filter(StringUtils::hasText);
  }

  private static String body(Map<String, Object> row, long id) {
    return "<p>The range billing batch has processed request " + id + " ("
        + HtmlUtils.htmlEscape(String.valueOf(row.get("org_unit_code"))) + ", "
        + HtmlUtils.htmlEscape(String.valueOf(row.get("calendar_year")))
        + "). The FTA411 Approval Summary is attached.</p>";
  }

  /** {@code IDIR\jsmith} or {@code jsmith} → {@code JSMITH}; null for another directory. */
  static String bareIdir(String userId) {
    if (!StringUtils.hasText(userId)) {
      return null;
    }
    String id = userId.trim();
    int slash = id.indexOf('\\');
    if (slash >= 0) {
      if (!"IDIR".equalsIgnoreCase(id.substring(0, slash).trim())) {
        return null;
      }
      id = id.substring(slash + 1).trim();
    }
    return id.isEmpty() ? null : id.toUpperCase(Locale.ROOT);
  }
}
