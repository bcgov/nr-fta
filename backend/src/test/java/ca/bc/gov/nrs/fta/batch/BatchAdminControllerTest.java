package ca.bc.gov.nrs.fta.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.BatchRun.Status;
import ca.bc.gov.nrs.fta.batch.BatchRun.Trigger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | BatchAdminController")
class BatchAdminControllerTest {

  private final BatchJob job = mock(BatchJob.class);
  private final BatchJobRunner runner = mock(BatchJobRunner.class);
  private final BatchRunHistory history = new BatchRunHistory();
  private final BatchAdminController controller;

  BatchAdminControllerTest() {
    when(job.name()).thenReturn("range-billing");
    when(job.legacyName()).thenReturn("fta_batch_690_tenure_approval");
    when(job.lockAtMostFor()).thenReturn(Duration.ofHours(4));
    controller = new BatchAdminController(List.of(job), runner, history,
        new MockEnvironment().withProperty("fta.batch.range-billing.cron", "0 0 2 * * *"));
  }

  @Test
  void listsJobsWithCronAndLastRun() {
    BatchRun run = new BatchRun("range-billing", Trigger.SCHEDULE, "IDIR\\SA_FTA",
        Instant.EPOCH, Instant.EPOCH, Status.SUCCEEDED, "ok");
    history.record(run);

    assertThat(controller.list()).containsExactly(new BatchAdminController.JobView(
        "range-billing", "fta_batch_690_tenure_approval", "0 0 2 * * *", run));
  }

  @Test
  void runStartsTheJobWith202() {
    BatchRun started = BatchRun.running("range-billing", Trigger.MANUAL, "", Instant.EPOCH);
    when(runner.startManual(eq(job), any())).thenReturn(Optional.of(started));

    var response = controller.run("range-billing", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isEqualTo(started);
  }

  @Test
  void runWhileRunningIs409() {
    when(runner.startManual(eq(job), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.run("range-billing", null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void unknownJobIs404() {
    assertThatThrownBy(() -> controller.get("nope"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
