package ca.bc.gov.nrs.fta.batch;

/**
 * What a {@link BatchJob#execute} run came to: whether every step went through, and a one-line
 * summary for the log and the last-run endpoint.
 */
public record BatchOutcome(boolean clean, String summary) {

  /** Every step went through. */
  public static BatchOutcome ok(String summary) {
    return new BatchOutcome(true, summary);
  }

  /** The job ran to the end, but reported one or more errors on the way (and e-mailed them). */
  public static BatchOutcome withErrors(String summary) {
    return new BatchOutcome(false, summary);
  }
}
