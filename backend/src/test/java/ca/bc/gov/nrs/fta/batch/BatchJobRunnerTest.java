package ca.bc.gov.nrs.fta.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.BatchRun.Status;
import ca.bc.gov.nrs.fta.batch.BatchRun.Trigger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.StandardEnvironment;

@DisplayName("Unit Test | BatchJobRunner")
class BatchJobRunnerTest {

  private final LockProvider locks = mock(LockProvider.class);
  private final BatchRunHistory history = new BatchRunHistory();
  private final BatchJobRunner runner = new BatchJobRunner(
      locks, history, "IDIR\\SA_FTA",
      Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC), new DirectExecutor());

  /** A job that returns, or throws, what it is told to. */
  private static final class FakeJob implements BatchJob {
    BatchOutcome outcome = BatchOutcome.ok("done");
    RuntimeException failure;
    int runs;

    @Override public String name() {
      return "fake";
    }

    @Override public String legacyName() {
      return "fta_batch_fake";
    }

    @Override public Duration lockAtMostFor() {
      return Duration.ofHours(2);
    }

    @Override public Duration lockAtLeastFor() {
      return Duration.ofMinutes(1);
    }

    @Override public BatchOutcome execute() {
      runs++;
      if (failure != null) {
        throw failure;
      }
      return outcome;
    }
  }

  @Test
  void scheduledRunIsRecordedUnderTheBatchUser() {
    FakeJob job = new FakeJob();

    BatchRun run = runner.runScheduled(job);

    assertThat(run.status()).isEqualTo(Status.SUCCEEDED);
    assertThat(run.trigger()).isEqualTo(Trigger.SCHEDULE);
    assertThat(run.requestedBy()).isEqualTo("IDIR\\SA_FTA");
    assertThat(run.summary()).isEqualTo("done");
    assertThat(history.last("fake")).contains(run);
    verify(locks, never()).lock(any());
  }

  @Test
  void reportedErrorsMakeTheRunCompletedWithErrors() {
    FakeJob job = new FakeJob();
    job.outcome = BatchOutcome.withErrors("1 failed");

    try (LogCapture log = new LogCapture(BatchJobRunner.class)) {
      assertThat(runner.runScheduled(job).status()).isEqualTo(Status.COMPLETED_WITH_ERRORS);
      // The job logs its own package errors; the runner adds none.
      assertThat(log.errors()).isEmpty();
    }
  }

  @Test
  void anExceptionIsLoggedAsLegacysJavaError() {
    FakeJob job = new FakeJob();
    job.failure = new IllegalStateException("database down");

    try (LogCapture log = new LogCapture(BatchJobRunner.class)) {
      BatchRun run = runner.runScheduled(job);

      assertThat(run.status()).isEqualTo(Status.FAILED);
      assertThat(run.summary()).isEqualTo("database down");
      assertThat(log.errors()).containsExactly("Batch error: fake failed. An error occurred in"
          + " the java batch application code: database down");
      assertThat(log.errorEvents().get(0).getThrowableProxy().getClassName())
          .isEqualTo(IllegalStateException.class.getName());
    }
  }

  @Test
  void manualRunTakesTheJobsOwnLockAndReleasesIt() {
    FakeJob job = new FakeJob();
    SimpleLock lock = mock(SimpleLock.class);
    when(locks.lock(any())).thenReturn(Optional.of(lock));

    Optional<BatchRun> started = runner.startManual(job, "IDIR\\ADMIN");

    assertThat(started).isPresent();
    assertThat(started.get().trigger()).isEqualTo(Trigger.MANUAL);
    ArgumentCaptor<LockConfiguration> cfg = ArgumentCaptor.forClass(LockConfiguration.class);
    verify(locks).lock(cfg.capture());
    assertThat(cfg.getValue().getName()).isEqualTo("fake");
    assertThat(cfg.getValue().getLockAtMostUntil())
        .isEqualTo(Instant.parse("2026-11-26T12:00:00Z"));
    assertThat(cfg.getValue().getLockAtLeastUntil())
        .isEqualTo(Instant.parse("2026-11-26T10:01:00Z"));
    assertThat(job.runs).isEqualTo(1);
    verify(lock).unlock();
    assertThat(history.last("fake")).get()
        .extracting(BatchRun::status, BatchRun::requestedBy)
        .containsExactly(Status.SUCCEEDED, "IDIR\\ADMIN");
  }

  @Test
  void manualRunReleasesTheLockWhenTheJobThrows() {
    FakeJob job = new FakeJob();
    job.failure = new IllegalStateException("boom");
    SimpleLock lock = mock(SimpleLock.class);
    when(locks.lock(any())).thenReturn(Optional.of(lock));

    runner.startManual(job, "IDIR\\ADMIN");

    verify(lock).unlock();
    assertThat(history.last("fake")).get().extracting(BatchRun::status).isEqualTo(Status.FAILED);
  }

  @Test
  void manualRunDoesNothingWhileTheJobIsRunning() {
    FakeJob job = new FakeJob();
    when(locks.lock(any())).thenReturn(Optional.empty());

    assertThat(runner.startManual(job, "IDIR\\ADMIN")).isEmpty();
    assertThat(job.runs).isZero();
    assertThat(history.last("fake")).isEmpty();
  }

  @Test
  void theBatchUserDefaultSurvivesPlaceholderResolution() {
    assertThat(new StandardEnvironment().resolvePlaceholders("${fta.batch.nope:IDIR\\SA_FTA}"))
        .isEqualTo("IDIR\\SA_FTA");
  }

  /** Runs each task on the calling thread. */
  private static final class DirectExecutor extends AbstractExecutorService {
    private boolean shutdown;

    @Override public void execute(Runnable command) {
      command.run();
    }

    @Override public void shutdown() {
      shutdown = true;
    }

    @Override public List<Runnable> shutdownNow() {
      shutdown = true;
      return List.of();
    }

    @Override public boolean isShutdown() {
      return shutdown;
    }

    @Override public boolean isTerminated() {
      return shutdown;
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }
  }
}
