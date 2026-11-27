package ca.bc.gov.nrs.fta.batch.rangereplace;

import java.sql.CallableStatement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_102_RANGE_REPLACE.mainline}, as legacy batch
 * {@code fta_batch_102_range_replace} did ({@code Fta102RangeReplace}): both parameters
 * ({@code P_TRACE_IND}, {@code P_ERROR_MESSAGE}) are {@code IN OUT VARCHAR2}, passed empty (NULL),
 * so the package's {@code dbms_output} trace stays off.
 *
 * <p>The package replaces every range tenure whose term expires within two months
 * ({@code TENURE_TERM.initial_expiry_dt < ADD_MONTHS(TRUNC(SYSDATE), 2)}) and that has a pending
 * {@code RANGE_TENURE.replacement_term} (years): the new term starts the day after the old expiry
 * and runs {@code replacement_term * 12} months, the extend count goes up by one, and the
 * replacement term is cleared. Audit id {@code FTA102} (hard-coded; no user parameter). Each update
 * checks the revision count read by the cursor.
 *
 * <p>Commits: the package commits after the loop; on an unexpected error it rolls back. On a
 * revision-count clash it returns {@code fta.web.usr.database.record.modified:…} without rolling
 * back. Legacy called it through a {@code BeanList} on an auto-commit connection, which kept the
 * updates made before the clash; a plain {@link JdbcTemplate} call (auto-commit) does the same.
 */
@Component
public class RangeReplacePackage {

  /** The package, as legacy's error e-mails name it. */
  public static final String PACKAGE = "FTA_102_RANGE_REPLACE";

  static final String CALL =
      "BEGIN the.fta_102_range_replace.mainline(p_trace_ind => ?, p_error_message => ?); END;";

  private final JdbcTemplate jdbc;

  public RangeReplacePackage(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Runs the package; returns its error message, blank when it succeeded. */
  public String mainline() {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          cs.setNull(1, Types.VARCHAR);
          cs.setNull(2, Types.VARCHAR);
          cs.registerOutParameter(1, Types.VARCHAR);
          cs.registerOutParameter(2, Types.VARCHAR);
          cs.execute();
          String error = cs.getString(2);
          return error == null ? "" : error.trim();
        });
  }
}
