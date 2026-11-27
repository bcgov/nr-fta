package ca.bc.gov.nrs.fta.tenure.metrics;

import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Column;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_008_METRICS_EXTRACT.mainline}, as {@code Fta008MetricsSrchAction}
 * did for Export: {@code GET_METRICS}, then {@code GET_HOLDS}, with the same criteria.
 *
 * <p>Each action fills the global temporary table {@code FTA_008_METRICS_EXTRACT_RESULT}
 * (DELETE, then INSERT … SELECT) and opens its cursor on it. Both run on one connection, outside
 * auto-commit, and are rolled back afterwards, as legacy's {@code store.rollback()} did, so the
 * rows do not outlive the request in the pooled session. Each cursor is read in full before the
 * next action empties the table.
 *
 * <p>Every MAINLINE parameter is {@code IN OUT}; they are bound by name. The procedures splice
 * the criteria into dynamic SQL, so {@link ApplicationMetricsRules#validate} checks them first.
 */
@Component
public class ApplicationMetricsPackage {

  static final String GET_METRICS = "GET_METRICS";
  static final String GET_HOLDS = "GET_HOLDS";

  /** The rows of each sheet, and the package's messages for the two calls. */
  public record Extract(List<List<String>> metrics, List<List<String>> holds,
      String errorMessage) {}

  /**
   * MAINLINE with the action and the eleven criteria bound (1–12), its error message (13) and,
   * when the action opened one, its cursor (14). An action that failed leaves the cursor
   * unopened; an empty one stands in, so the driver never sees an unopened cursor.
   */
  static final String CALL =
      "DECLARE"
          + " v_action VARCHAR2(30) := ?;"
          + " v_org VARCHAR2(4000) := ?;"
          + " v_sub_from VARCHAR2(4000) := ?;"
          + " v_sub_to VARCHAR2(4000) := ?;"
          + " v_email_from VARCHAR2(4000) := ?;"
          + " v_email_to VARCHAR2(4000) := ?;"
          + " v_acc_from VARCHAR2(4000) := ?;"
          + " v_acc_to VARCHAR2(4000) := ?;"
          + " v_dec_from VARCHAR2(4000) := ?;"
          + " v_dec_to VARCHAR2(4000) := ?;"
          + " v_iss_from VARCHAR2(4000) := ?;"
          + " v_iss_to VARCHAR2(4000) := ?;"
          + " v_metrics_id VARCHAR2(4000);"
          + " v_err VARCHAR2(32767);"
          + " v_cur the.fta_008_metrics_extract.fta_application_metrics_ref_cur;"
          + " v_out SYS_REFCURSOR;"
          + " BEGIN"
          + " the.fta_008_metrics_extract.mainline("
          + "p_action => v_action, p_admin_org_unit_no => v_org,"
          + " p_submission_date_from => v_sub_from, p_submission_date_to => v_sub_to,"
          + " p_email_recd_date_from => v_email_from, p_email_recd_date_to => v_email_to,"
          + " p_acceptance_date_from => v_acc_from, p_acceptance_date_to => v_acc_to,"
          + " p_decision_date_from => v_dec_from, p_decision_date_to => v_dec_to,"
          + " p_issuance_date_from => v_iss_from, p_issuance_date_to => v_iss_to,"
          + " p_harv_auth_metrics_id => v_metrics_id, p_error_message => v_err,"
          + " p_metrics_rec_cursor => v_cur);"
          + " ? := v_err;"
          + " IF v_cur%ISOPEN THEN v_out := v_cur;"
          + " ELSE OPEN v_out FOR SELECT NULL FROM dual WHERE 1 = 0; END IF;"
          + " ? := v_out;"
          + " END;";

  static final int CRITERIA = 11;
  static final int ERROR_INDEX = CRITERIA + 2;
  static final int CURSOR_INDEX = CRITERIA + 3;

  private final JdbcTemplate jdbc;

  public ApplicationMetricsPackage(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * GET_METRICS then GET_HOLDS for these criteria.
   *
   * @param args {@link ApplicationMetricsRules#procedureArguments}: the org unit, then the ten
   *             dates
   */
  public Extract extract(List<String> args) {
    return jdbc.execute((ConnectionCallback<Extract>) con -> {
      boolean autoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        Sheet metrics = run(con, GET_METRICS, args, ApplicationMetricsRules.METRICS_COLUMNS);
        Sheet holds = run(con, GET_HOLDS, args, ApplicationMetricsRules.HOLDS_COLUMNS);
        String error = (metrics.error() + holds.error()).trim();
        return new Extract(metrics.rows(), holds.rows(), error);
      } finally {
        try {
          con.rollback();
        } finally {
          con.setAutoCommit(autoCommit);
        }
      }
    });
  }

  private record Sheet(List<List<String>> rows, String error) {}

  private static Sheet run(Connection con, String action, List<String> args,
      List<Column> columns) throws SQLException {
    try (CallableStatement cs = con.prepareCall(CALL)) {
      bind(cs, action, args);
      cs.execute();
      String error = cs.getString(ERROR_INDEX);
      List<List<String>> rows = new ArrayList<>();
      if (error == null || error.isBlank()) {
        try (ResultSet rs = cs.getObject(CURSOR_INDEX, ResultSet.class)) {
          while (rs != null && rs.next()) {
            List<String> row = new ArrayList<>(columns.size());
            for (Column c : columns) {
              row.add(rs.getString(c.name()));
            }
            rows.add(row);
          }
        }
      }
      return new Sheet(rows, error == null ? "" : error);
    }
  }

  /** The action, the criteria in order (blank as null), and the two outputs. */
  static void bind(CallableStatement cs, String action, List<String> args) throws SQLException {
    if (args.size() != CRITERIA) {
      throw new IllegalArgumentException("Expected " + CRITERIA + " criteria, got " + args.size());
    }
    cs.setString(1, action);
    for (int i = 0; i < CRITERIA; i++) {
      String v = args.get(i);
      cs.setString(i + 2, v == null || v.isBlank() ? null : v);
    }
    cs.registerOutParameter(ERROR_INDEX, Types.VARCHAR);
    cs.registerOutParameter(CURSOR_INDEX, Types.REF_CURSOR);
  }
}
