package ca.bc.gov.nrs.fta.batch.cancelmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.BatchJob;
import ca.bc.gov.nrs.fta.batch.BatchJobRunner;
import ca.bc.gov.nrs.fta.batch.BatchOutcome;
import ca.bc.gov.nrs.fta.batch.LogCapture;
import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

@DisplayName("Unit Test | CancelMarkJob (legacy fta_batch_520_cancel_mark)")
class CancelMarkJobTest {

  private final CancelMarkPackage pkg = mock(CancelMarkPackage.class);
  private final BatchJobRunner runner = mock(BatchJobRunner.class);
  private final CancelMarkJob job = new CancelMarkJob(pkg, runner);

  @Test
  void runsThePackageAndLogsNoErrorOnSuccess() {
    when(pkg.mainline()).thenReturn("");

    try (LogCapture log = new LogCapture(CancelMarkJob.class)) {
      BatchOutcome outcome = job.execute();

      assertThat(outcome.clean()).isTrue();
      assertThat(log.errors()).isEmpty();
    }
  }

  @Test
  void aPackageErrorIsLoggedAtError() {
    when(pkg.mainline()).thenReturn("fta.web.usr.database.unexpected:x;");

    try (LogCapture log = new LogCapture(CancelMarkJob.class)) {
      BatchOutcome outcome = job.execute();

      assertThat(outcome.clean()).isFalse();
      assertThat(log.errors()).containsExactly("Batch error: An error occurred in the database"
          + " package FTA_520_CANCEL_MARK: fta.web.usr.database.unexpected:x;");
    }
  }

  @Test
  void theScheduleHandsOverToTheRunner() {
    job.scheduled();
    verify(runner).runScheduled(job);
  }

  @Test
  void lockNameAndCronProperty() throws Exception {
    var m = CancelMarkJob.class.getMethod("scheduled");
    assertThat(m.getAnnotation(SchedulerLock.class).name()).isEqualTo("cancel-mark");
    assertThat(m.getAnnotation(SchedulerLock.class).lockAtMostFor()).isEqualTo("PT1H");
    assertThat(m.getAnnotation(Scheduled.class).cron())
        .isEqualTo("${fta.batch.cancel-mark.cron:-}");
    assertThat(m.getAnnotation(Scheduled.class).zone()).isEqualTo(BatchJob.ZONE);
    assertThat(job.name()).isEqualTo("cancel-mark");
    assertThat(job.legacyName()).isEqualTo("fta_batch_520_cancel_mark");
    assertThat(job.lockAtMostFor()).isEqualTo(Duration.ofHours(1));
    assertThat(job.lockAtLeastFor()).isEqualTo(Duration.ofMinutes(1));
  }
}
