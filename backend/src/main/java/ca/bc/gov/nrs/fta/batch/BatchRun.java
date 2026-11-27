package ca.bc.gov.nrs.fta.batch;

import java.time.Instant;

/**
 * One run of a batch job, as {@link BatchRunHistory} keeps it.
 *
 * @param job the job's {@link BatchJob#name}
 * @param trigger {@link Trigger#SCHEDULE} or {@link Trigger#MANUAL}
 * @param requestedBy the batch user id for a scheduled run, the admin's audit id for a manual one
 * @param started when the run started
 * @param finished when it ended; null while it runs
 * @param status how it ended
 * @param summary the job's one-line summary, or the exception message when it failed
 */
public record BatchRun(
    String job,
    Trigger trigger,
    String requestedBy,
    Instant started,
    Instant finished,
    Status status,
    String summary) {

  /** What started the run. */
  public enum Trigger { SCHEDULE, MANUAL }

  /** How the run went. */
  public enum Status {
    /** Still running (on this pod). */
    RUNNING,
    /** Every step went through. */
    SUCCEEDED,
    /** Ran to the end but reported errors (e-mailed to the batch error recipients). */
    COMPLETED_WITH_ERRORS,
    /** Stopped by an exception (e-mailed to the batch error recipients). */
    FAILED
  }

  static BatchRun running(String job, Trigger trigger, String requestedBy, Instant started) {
    return new BatchRun(job, trigger, requestedBy, started, null, Status.RUNNING, null);
  }

  BatchRun finish(Instant at, Status status, String summary) {
    return new BatchRun(job, trigger, requestedBy, started, at, status, summary);
  }
}
