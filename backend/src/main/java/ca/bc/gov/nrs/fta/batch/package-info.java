/**
 * Legacy FTA's batch jobs, ported. Legacy ran each as {@code ProcRunner <job> BATCH_PROCESS} from
 * an Automate server ({@code fta-archive/fta/source/batch}: {@code automate/*.aml} →
 * {@code bin/*.bat} → {@code batch-java} {@code DBProcTask}), each calling one {@code THE}
 * package. Here each is a Spring bean, scheduled in every pod and serialised by ShedLock.
 *
 * <h2>The pattern (copy it for a new job)</h2>
 *
 * <ol>
 *   <li><b>A sub-package per job</b>, e.g. {@code batch.cancelmark}, holding a
 *       {@code *Package} class that calls the legacy PL/SQL over JDBC (no re-implementation) and
 *       a {@code *Job} class.
 *   <li><b>The job</b> is a {@code @Component} implementing {@link
 *       ca.bc.gov.nrs.fta.batch.BatchJob}, with its id, lock times and legacy name as
 *       constants, and one scheduled method that only hands over to the runner:
 *       <pre>{@code
 * static final String NAME = "cancel-mark";        // lock name, property prefix, URL segment
 * static final String LOCK_AT_MOST = "PT2H";
 * static final String LOCK_AT_LEAST = "PT1M";
 *
 * @Scheduled(cron = "${fta.batch.cancel-mark.cron:-}", zone = BatchJob.ZONE)
 * @SchedulerLock(name = NAME, lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
 * public void scheduled() {
 *   runner.runScheduled(this);
 * }
 *
 * public BatchOutcome execute() { ... the work ... }
 * }</pre>
 *       {@code name()}, {@code lockAtMostFor()} and {@code lockAtLeastFor()} return the same
 *       constants, so the manual run takes the very same lock.
 *   <li><b>Enabling</b>: there is no enabled flag. The cron property
 *       {@code fta.batch.<id>.cron} defaults to {@code "-"} (Spring's "disabled"); set it (env
 *       var {@code FTA_BATCH_<ID>_CRON}) on the pods to schedule the job, through the optional
 *       ConfigMap {@code nr-fta-backend-batch-schedules-<zone>}. Schedules are never kept in
 *       Git, defaults included. Crons are read in {@link
 *       ca.bc.gov.nrs.fta.batch.BatchJob#ZONE}.
 *   <li><b>Errors</b>: where legacy e-mailed {@code deliverErrorEmail(...)} to support and
 *       carried on (a package's {@code P_ERROR_MESSAGE}), log it at ERROR with {@link
 *       ca.bc.gov.nrs.fta.batch.BatchJob#PACKAGE_ERROR} ({@code LOG.error(BatchJob.PACKAGE_ERROR,
 *       PACKAGE, error)}) and return {@link ca.bc.gov.nrs.fta.batch.BatchOutcome#withErrors}.
 *       Throw for anything else: {@link ca.bc.gov.nrs.fta.batch.BatchJobRunner} logs it at ERROR,
 *       with the exception, as legacy's "java batch application code" error. Batch errors are not
 *       e-mailed; every such line starts {@code Batch error:} for monitoring to alert on.
 *   <li><b>Commits</b>: do as legacy did. A legacy {@code BeanList} / {@code BasicStore.get}
 *       call ran on an auto-commit connection (a plain {@code JdbcTemplate} call does the same);
 *       a {@code BasicStore.put} turned auto-commit off and the batch committed or rolled back
 *       itself.
 *   <li><b>Tests</b>: mock the {@code *Package}; assert the {@code @SchedulerLock} name and the {@code @Scheduled} cron property by reflection (see
 *       {@code CancelMarkJobTest}).
 * </ol>
 *
 * <h2>Shared pieces</h2>
 *
 * <ul>
 *   <li>{@link ca.bc.gov.nrs.fta.batch.BatchJobRunner} — runs a job (scheduled, or manual under
 *       the same lock on a background thread), logs start and outcome, records the run.
 *   <li>{@link ca.bc.gov.nrs.fta.batch.BatchRunHistory} — last run per job, this pod only.
 *   <li>{@link ca.bc.gov.nrs.fta.batch.BatchAdminController} — {@code GET
 *       /api/fta/admin/batch}, {@code GET /api/fta/admin/batch/<id>} and {@code POST
 *       /api/fta/admin/batch/<id>/run}, FTA_ADMIN only.
 *   <li>The lock itself: {@code configuration.SchedulerLockConfiguration} (table
 *       {@code THE.FTA_SHEDLOCK}, database time).
 * </ul>
 *
 * <p>Legacy's third job, {@code fta_batch_690_tenure_approval} (range billing), is not a batch
 * here: FTA690's Submit bills the request while the user waits ({@code
 * billing.service.RangeBillingProcessor}, a ShedLock lock per district and year) and the FTA411
 * summary downloads instead of being e-mailed.
 *
 * <p>{@code fta.batch.user-id} (legacy {@code batch.user}; default {@code IDIR\SA_FTA}) is the id
 * scheduled runs are recorded under. Legacy used it only to open its WebADE connection — none of
 * the batch packages takes a user parameter; they write their own audit ids ({@code FTA102},
 * {@code FTA520}, …) or the submitting user's.
 */
package ca.bc.gov.nrs.fta.batch;
