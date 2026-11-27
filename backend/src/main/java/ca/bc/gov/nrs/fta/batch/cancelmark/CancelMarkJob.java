package ca.bc.gov.nrs.fta.batch.cancelmark;

import ca.bc.gov.nrs.fta.batch.BatchJob;
import ca.bc.gov.nrs.fta.batch.BatchJobRunner;
import ca.bc.gov.nrs.fta.batch.BatchOutcome;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Legacy batch {@code fta_batch_520_cancel_mark} ({@code ProcRunner fta_batch_520_cancel_mark
 * BATCH_PROCESS}, Automate task {@code fta_batch_520_cancel_mark.aml};
 * {@code DBProcTask.processFtaBatch520CancelMark}): runs {@code FTA_520_CANCEL_MARK.mainline}
 * ({@link CancelMarkPackage}) and, when the package returns an error, logs it at ERROR (legacy
 * e-mailed it to support). It sends no e-mail.
 *
 * <p>No legacy file records the schedule (the Automate task's trigger lived on the Automate
 * server), so the cron defaults to {@code -} (disabled).
 */
@Component
public class CancelMarkJob implements BatchJob {

  private static final Logger LOG = LoggerFactory.getLogger(CancelMarkJob.class);

  static final String NAME = "cancel-mark";
  static final String LEGACY_NAME = "fta_batch_520_cancel_mark";
  static final String LOCK_AT_MOST = "PT1H";
  static final String LOCK_AT_LEAST = "PT1M";

  /** As legacy's error e-mail named it, and the error log line names it. */
  static final String PACKAGE = "FTA_520_CANCEL_MARK";

  private final CancelMarkPackage pkg;
  private final BatchJobRunner runner;

  public CancelMarkJob(CancelMarkPackage pkg, BatchJobRunner runner) {
    this.pkg = pkg;
    this.runner = runner;
  }

  @Scheduled(cron = "${fta.batch.cancel-mark.cron:-}", zone = BatchJob.ZONE)
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
    String error = pkg.mainline();
    if (!error.isEmpty()) {
      LOG.error(BatchJob.PACKAGE_ERROR, PACKAGE, error);
      return BatchOutcome.withErrors("FTA_520_CANCEL_MARK returned an error: " + error);
    }
    return BatchOutcome.ok("Expired private marks cancelled.");
  }
}
