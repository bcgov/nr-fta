package ca.bc.gov.nrs.fta.batch.rangereplace;

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
 * Annual range replacement — legacy batch {@code fta_batch_102_range_replace}
 * ({@code DBProcTask.processFtaBatch102RangeReplace}): runs {@link RangeReplacePackage} once and
 * logs its error, if any, at ERROR (legacy e-mailed it to support). Nothing is e-mailed.
 *
 * <p>Schedule: the package's own header says it "will be scheduled to run once per year on
 * January 1" (and tolerates a run on Dec 31 or a later rerun). No legacy file gives the time of
 * day, so, as for the other batch jobs with no recorded schedule,
 * {@code fta.batch.range-replace.cron} defaults to disabled; set it to e.g. {@code 0 0 1 1 1 *}.
 */
@Component
public class RangeReplaceJob implements BatchJob {

  private static final Logger LOG = LoggerFactory.getLogger(RangeReplaceJob.class);

  static final String NAME = "range-replace";
  static final String LEGACY_NAME = "fta_batch_102_range_replace";
  static final String LOCK_AT_MOST = "PT1H";
  static final String LOCK_AT_LEAST = "PT1M";

  private final RangeReplacePackage pkg;
  private final BatchJobRunner runner;

  public RangeReplaceJob(RangeReplacePackage pkg, BatchJobRunner runner) {
    this.pkg = pkg;
    this.runner = runner;
  }

  @Scheduled(cron = "${fta.batch.range-replace.cron:-}", zone = BatchJob.ZONE)
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
      LOG.error(BatchJob.PACKAGE_ERROR, RangeReplacePackage.PACKAGE, error);
      return BatchOutcome.withErrors(error);
    }
    return BatchOutcome.ok("Range tenure replacements applied.");
  }
}
