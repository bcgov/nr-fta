package ca.bc.gov.nrs.fta.batch;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The last run of each batch job <b>on this pod</b>, in memory: lost on restart, and another pod's
 * runs are not here. The durable record is the log (every run logs its start and outcome under
 * {@code ca.bc.gov.nrs.fta.batch}) and whatever the job's package writes (e.g. range billing's
 * {@code FTA_RANGE_BILL_REQUEST} row).
 */
@Component
public class BatchRunHistory {

  private final Map<String, BatchRun> last = new ConcurrentHashMap<>();

  void record(BatchRun run) {
    last.put(run.job(), run);
  }

  /** The job's latest run on this pod, if it ran since the pod started. */
  public Optional<BatchRun> last(String job) {
    return Optional.ofNullable(last.get(job));
  }
}
