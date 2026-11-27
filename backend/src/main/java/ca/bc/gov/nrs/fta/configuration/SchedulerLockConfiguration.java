package ca.bc.gov.nrs.fta.configuration;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ShedLock for the scheduled batch jobs (legacy FTA's Automate-run batches): every pod runs the
 * scheduler, and the lock row in {@code THE.FTA_SHEDLOCK} lets exactly one of them run a job per
 * tick. A job takes the lock with {@code @SchedulerLock(name = ..., lockAtMostFor = ...)}; the
 * lock times use the database clock, so pods with drifting clocks still agree.
 *
 * <p>Scheduling itself is enabled by {@link CodeListCacheConfiguration}'s
 * {@code @EnableScheduling}.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT2H")
public class SchedulerLockConfiguration {

  /** The lock table (nr-mof-db {@code THE.FTA_SHEDLOCK}). */
  static final String LOCK_TABLE = "THE.FTA_SHEDLOCK";

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName(LOCK_TABLE)
            .usingDbTime()
            .build());
  }
}
