package ca.bc.gov.nrs.fta.batch;

import ca.bc.gov.nrs.fta.batch.BatchRun.Status;
import ca.bc.gov.nrs.fta.batch.BatchRun.Trigger;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs a {@link BatchJob}, on its schedule or on demand, and records each run.
 *
 * <ul>
 *   <li>{@link #runScheduled} — called by the job's {@code @Scheduled} + {@code @SchedulerLock}
 *       method, so the lock is already held.
 *   <li>{@link #startManual} — the admin endpoint. Takes the <em>same</em> ShedLock lock (same
 *       name, same durations) through the {@link LockProvider}; if a run holds it — on any pod —
 *       nothing starts. Otherwise the job runs on a background thread (a run can outlast an HTTP
 *       request) and the lock is released when it ends.
 * </ul>
 *
 * <p>Every run is logged at start and end with its outcome, and kept in {@link BatchRunHistory}.
 * An exception from the job is legacy's {@code DBProcTask.submit} catch: logged at ERROR as
 * legacy's "java batch application code" error, with the exception (legacy also e-mailed it).
 */
@Component
public class BatchJobRunner {

  private static final Logger LOG = LoggerFactory.getLogger(BatchJobRunner.class);

  /** Legacy's error e-mail text for an exception, as a log line: job, then the message. */
  static final String JAVA_ERROR =
      "Batch error: {} failed. An error occurred in the java batch application code: {}";

  private final LockProvider locks;
  private final BatchRunHistory history;
  private final String batchUserId;
  private final Clock clock;
  private final ExecutorService executor;

  @Autowired
  public BatchJobRunner(
      LockProvider locks,
      BatchRunHistory history,
      @Value("${fta.batch.user-id:IDIR\\SA_FTA}") String batchUserId) {
    this(locks, history, batchUserId, Clock.systemUTC(),
        Executors.newCachedThreadPool(r -> {
          Thread t = new Thread(r, "fta-batch-manual");
          t.setDaemon(true);
          return t;
        }));
  }

  BatchJobRunner(
      LockProvider locks,
      BatchRunHistory history,
      String batchUserId,
      Clock clock,
      ExecutorService executor) {
    this.locks = locks;
    this.history = history;
    this.batchUserId = batchUserId;
    this.clock = clock;
    this.executor = executor;
  }

  /** The id scheduled runs are recorded under ({@code fta.batch.user-id}). */
  public String batchUserId() {
    return batchUserId;
  }

  /** A scheduled run. Call only from a method holding the job's {@code @SchedulerLock}. */
  public BatchRun runScheduled(BatchJob job) {
    return run(job, Trigger.SCHEDULE, batchUserId);
  }

  /**
   * Starts a manual run in the background, under the job's lock.
   *
   * @return the run as started, or empty when the job is already running (the lock is held)
   */
  public Optional<BatchRun> startManual(BatchJob job, String requestedBy) {
    Instant now = clock.instant();
    Optional<SimpleLock> lock = locks.lock(new LockConfiguration(
        now, job.name(), job.lockAtMostFor(), job.lockAtLeastFor()));
    if (lock.isEmpty()) {
      LOG.info("Batch {} not started for {}: it is already running", job.name(), requestedBy);
      return Optional.empty();
    }
    BatchRun started = BatchRun.running(job.name(), Trigger.MANUAL, requestedBy, now);
    history.record(started);
    try {
      executor.execute(() -> {
        try {
          run(job, Trigger.MANUAL, requestedBy);
        } finally {
          lock.get().unlock();
        }
      });
    } catch (RejectedExecutionException e) {
      lock.get().unlock();
      throw e;
    }
    return Optional.of(started);
  }

  private BatchRun run(BatchJob job, Trigger trigger, String requestedBy) {
    Instant start = clock.instant();
    BatchRun run = BatchRun.running(job.name(), trigger, requestedBy, start);
    history.record(run);
    LOG.info("Batch {} ({}) started: {} run by {}",
        job.name(), job.legacyName(), trigger, requestedBy);
    BatchRun done;
    try {
      BatchOutcome outcome = job.execute();
      done = run.finish(clock.instant(),
          outcome.clean() ? Status.SUCCEEDED : Status.COMPLETED_WITH_ERRORS, outcome.summary());
    } catch (RuntimeException e) {
      LOG.error(JAVA_ERROR, job.name(), e.getMessage() == null ? e.toString() : e.getMessage(),
          e);
      done = run.finish(clock.instant(), Status.FAILED, String.valueOf(e.getMessage()));
    }
    history.record(done);
    LOG.info("Batch {} ended {} after {}s: {}", job.name(), done.status(),
        Duration.between(start, done.finished()).toSeconds(), done.summary());
    return done;
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);
  }
}
