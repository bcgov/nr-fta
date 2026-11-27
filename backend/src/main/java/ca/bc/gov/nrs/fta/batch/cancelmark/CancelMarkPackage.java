package ca.bc.gov.nrs.fta.batch.cancelmark;

import java.sql.CallableStatement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_520_CANCEL_MARK.mainline}, as legacy batch
 * {@code fta_batch_520_cancel_mark} did ({@code Fta520CancelMarkBean}): both parameters are
 * {@code IN OUT VARCHAR2}, passed empty (NULL), so the package's trace output stays off.
 *
 * <p>The package cancels the private marks (file types B08, B09, B14) that expired more than 90
 * days ago and were not extended past that: file and mark status HX, land indexes deactivated,
 * audit user {@code FTA520}. It commits itself, and on failure rolls back and returns the error in
 * {@code P_ERROR_MESSAGE}.
 */
@Component
public class CancelMarkPackage {

  static final String CALL =
      "BEGIN the.fta_520_cancel_mark.mainline(p_trace_ind => ?, p_error_message => ?); END;";

  private final JdbcTemplate jdbc;

  public CancelMarkPackage(JdbcTemplate jdbc) {
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
