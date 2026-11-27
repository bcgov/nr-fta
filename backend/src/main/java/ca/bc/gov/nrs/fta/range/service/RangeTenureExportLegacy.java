package ca.bc.gov.nrs.fta.range.service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRResultSetDataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs legacy's ten export procedures, {@code THE.FTA_001R_TENR_SRCH_CSV1..10}, and fills the
 * FTA001 workbook from their cursors, as JasperServer's PL/SQL data source did: each procedure
 * opens a REF CURSOR the matching subreport reads through a {@link JRResultSetDataSource}, so
 * column matching and type conversion are Jasper's own, as before.
 *
 * <p>The cursors stay open on one connection until the workbook is filled. The procedures
 * splice their inputs into dynamic SQL; {@link RangeTenureExportCriteria} checks and escapes
 * them first.
 */
@Component
public class RangeTenureExportLegacy {

  /** Each procedure's inputs, then its cursor. */
  private static final int INPUTS = 26;

  /**
   * The main report's names for the same inputs, in the procedures' order (legacy's
   * {@code getReportParameterNames}, less {@code p_user_id}).
   */
  static final List<String> REPORT_PARAMETERS = List.of(
      "p_forest_file_id", "p_file_type_code_1", "p_org_unit_no", "p_zone", "p_client_name",
      "p_client_number", "p_client_locn_code", "p_file_client_type", "p_file_status_st",
      "p_mgmt_unit_type", "p_mgmt_unit_id", "p_billing_period_from", "p_billing_period_to",
      "p_expiry_date_from", "p_expiry_date_to", "p_year", "p_authorized_use_from",
      "p_authorized_use_to", "p_temporary_increase_from", "p_temporary_increase_to",
      "p_billable_non_use_from", "p_billable_non_use_to", "p_non_billable_non_use_from",
      "p_non_billable_non_use_to", "p_total_annual_use_from", "p_total_annual_use_to");

  private final JdbcTemplate jdbc;

  public RangeTenureExportLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /** The call for {@code FTA_001R_TENR_SRCH_CSV<n>}: 26 inputs and the IN OUT cursor. */
  static String call(int n) {
    return "{call the.fta_001r_tenr_srch_csv" + n + "("
        + String.join(", ", Collections.nCopies(INPUTS + 1, "?")) + ")}";
  }

  /** The report's {@code p_*} parameters: the user and the procedures' inputs. */
  static Map<String, Object> reportParameters(List<String> args, String userId) {
    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", userId);
    for (int i = 0; i < INPUTS; i++) {
      params.put(REPORT_PARAMETERS.get(i), args.get(i));
    }
    return params;
  }

  /** Binds the inputs, in order, and registers the cursor. */
  static void bind(CallableStatement cs, List<String> args) throws SQLException {
    for (int i = 0; i < INPUTS; i++) {
      cs.setString(i + 1, args.get(i));
    }
    cs.registerOutParameter(INPUTS + 1, Types.REF_CURSOR);
  }

  /**
   * The workbook for these criteria.
   *
   * @param userId the requesting user, legacy's {@code p_user_id}
   */
  public byte[] xlsx(RangeTenureExportCriteria criteria, String userId) {
    List<String> args = criteria.procedureArguments();
    return jdbc.execute((ConnectionCallback<byte[]>) con -> fill(con, args, userId));
  }

  private static byte[] fill(Connection con, List<String> args, String userId)
      throws SQLException {
    List<CallableStatement> open = new ArrayList<>();
    try {
      List<JRDataSource> sheets = new ArrayList<>();
      for (int n = 1; n <= RangeTenureExportReport.SHEETS; n++) {
        CallableStatement cs = con.prepareCall(call(n));
        open.add(cs);
        bind(cs, args);
        cs.execute();
        ResultSet rs = cs.getObject(INPUTS + 1, ResultSet.class);
        rs.setFetchSize(500);
        sheets.add(new JRResultSetDataSource(rs));
      }
      return RangeTenureExportReport.xlsx(reportParameters(args, userId), sheets);
    } finally {
      for (CallableStatement cs : open) {
        try {
          cs.close();
        } catch (SQLException ignored) {
          // closing is best effort; the connection goes back to the pool either way
        }
      }
    }
  }
}
