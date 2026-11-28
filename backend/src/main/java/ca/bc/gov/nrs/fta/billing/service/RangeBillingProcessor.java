package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.service.RangeBillingPackage.ProcessResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bills a submitted FTA690 request while the user waits — what legacy left to the nightly batch
 * {@code fta_batch_690_tenure_approval}. Production runs since 2015 take 1–8 seconds.
 *
 * <p>The one guard: a ShedLock lock per district and year ({@code range-billing-<org>-<year>},
 * {@code THE.FTA_SHEDLOCK}). {@code PROCESS} reads its request and the billing instructions
 * without locking them, so two runs for the same district and year at the same moment would both
 * invoice every tenure. (One after another is harmless: billing marks each tenure's instruction
 * "B", and the second run finds nothing to bill.) A Submit that finds the lock held is refused
 * straight away. Under the lock the request is checked again, so one already processed is left
 * alone rather than having its outcome overwritten.
 *
 * <p>A package error is logged at ERROR, as legacy e-mailed it to support; the outcome is on the
 * request row either way. The lock lasts at most {@link #LOCK_AT_MOST}, so a pod that dies
 * mid-run blocks that district and year for minutes; its unprocessed request is billed by the
 * next Submit.
 */
@Component
public class RangeBillingProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(RangeBillingProcessor.class);

  static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);

  static final String RUNNING = "Billing for this district and year is already running.";

  /** The ERROR line for a package error: request id, package, message. */
  static final String PACKAGE_ERROR =
      "Range billing error: request {} — an error occurred in the database package {}: {}";

  private final RangeBillingPackage pkg;
  private final LockProvider locks;
  private final Clock clock;

  @Autowired
  public RangeBillingProcessor(RangeBillingPackage pkg, LockProvider locks) {
    this(pkg, locks, Clock.systemUTC());
  }

  RangeBillingProcessor(RangeBillingPackage pkg, LockProvider locks, Clock clock) {
    this.pkg = pkg;
    this.locks = locks;
    this.clock = clock;
  }

  static String lockName(long orgUnitNo, String calendarYear) {
    return "range-billing-" + orgUnitNo + "-" + calendarYear;
  }

  /**
   * Bills the request now, if it is still unprocessed. Its outcome is on its row.
   *
   * @throws ResponseStatusException 409 while another run for the district and year holds the
   *     lock
   */
  public void bill(long id, long orgUnitNo, String calendarYear, String requestedBy) {
    Instant start = clock.instant();
    SimpleLock lock = locks
        .lock(new LockConfiguration(
            start, lockName(orgUnitNo, calendarYear), LOCK_AT_MOST, Duration.ZERO))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, RUNNING));
    try {
      if (!pkg.isUnprocessed(id)) {
        LOG.info("Range billing: request {} is already processed", id);
        return;
      }
      LOG.info("Range billing: processing request {} for {}", id, requestedBy);
      ProcessResult result = pkg.process(id);
      if (result.errorMessage().isEmpty()) {
        LOG.info("Range billing: request {} processed in {}s", id,
            Duration.between(start, clock.instant()).toSeconds());
      } else {
        LOG.error(PACKAGE_ERROR, id, RangeBillingPackage.PACKAGE, result.errorMessage());
      }
    } finally {
      lock.unlock();
    }
  }
}
