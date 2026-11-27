package ca.bc.gov.nrs.fta.batch;

import java.time.Duration;

/**
 * One of legacy FTA's batch jobs (an Automate-run {@code ProcRunner <job>}), ported as a Spring
 * bean. See {@link ca.bc.gov.nrs.fta.batch the package documentation} for the pattern every job
 * follows.
 */
public interface BatchJob {

  /** The time zone every job's cron is read in (legacy's Automate server ran on Pacific time). */
  String ZONE = "America/Vancouver";

  /**
   * The ERROR log line for a package's {@code P_ERROR_MESSAGE} — legacy's batch error e-mail text
   * ("An error occurred in the database package &lt;PACKAGE&gt;:" and the message) on one line,
   * with a fixed "Batch error:" prefix for monitoring to alert on. Arguments: package, message.
   */
  String PACKAGE_ERROR = "Batch error: An error occurred in the database package {}: {}";

  /**
   * The job's id: its ShedLock lock name, its {@code fta.batch.<id>.*} property prefix and its
   * {@code /api/fta/admin/batch/<id>} path segment. Lower-case kebab case.
   */
  String name();

  /** The legacy job this ports ({@code ProcRunner}'s first argument), for logs and the API. */
  String legacyName();

  /** As the {@code @SchedulerLock} {@code lockAtMostFor}. */
  Duration lockAtMostFor();

  /** As the {@code @SchedulerLock} {@code lockAtLeastFor}. */
  Duration lockAtLeastFor();

  /**
   * Does the work. Called by {@link BatchJobRunner} with the job's lock held, never directly.
   * Errors legacy reported and carried on from (a package's {@code P_ERROR_MESSAGE}) are reported
   * here, logged at ERROR with {@link #PACKAGE_ERROR}, and come back as {@link
   * BatchOutcome#withErrors};
   * anything thrown is treated as legacy treated a Java exception (see {@link BatchJobRunner}).
   */
  BatchOutcome execute();
}
