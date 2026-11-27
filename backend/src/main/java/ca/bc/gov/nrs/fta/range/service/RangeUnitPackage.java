package ca.bc.gov.nrs.fta.range.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls the legacy {@code THE.FTA_630_MN_RG_UN_PST.mainline} — the package behind FTA630,
 * Maintain Range Unit / Pasture — for its writes: {@code SAVE} (add or change a range unit),
 * {@code REMOVE}, {@code SAVE_ITEM} (add or change a pasture) and {@code REMOVE_ITEM}. Calling it
 * keeps legacy's id generation (RANGE_UNIT_FILE_ID_CTL), the pasture '00' it creates and renames
 * with the unit, INITCAP on names, the duplicate-pasture check and the revision-count checks.
 *
 * <p>Every parameter but the pasture cursor is {@code IN OUT VARCHAR2}; they are bound by name.
 * The cursor ({@code p_m_r_u_p_results}) is only opened by {@code GET}, which this class does not
 * run, so it is a local variable of the block and never read.
 *
 * <p>The package neither commits nor rolls back, and reports failures in
 * {@code p_error_message} rather than raising: the caller runs it in a transaction and rolls back
 * on an error, as legacy's action did ({@code store.rollback()}).
 */
@Component
public class RangeUnitPackage {

  /** {@code mainline}'s VARCHAR2 parameters, in the spec's order (FTA_630_MN_RG_UN_PST.PKS). */
  static final List<String> MAINLINE_PARAMS = List.of(
      "p_action", "p_user_org_unit", "p_range_unit", "p_ind", "p_status", "p_prev_status",
      "p_status_description", "p_as_of", "p_prev_as_of", "p_region", "p_region_description",
      "p_district", "p_district_description", "p_range_unit_id", "p_range_unit_name",
      "p_update_userid", "p_pasture_revision_count", "p_pasture_id", "p_pasture_name",
      "p_district_admin_zone", "p_revision_count", "p_error_message");

  private static final String BLOCK = buildBlock();

  private final JdbcTemplate jdbc;

  // The template every service injects; its JdbcTemplate shares the transaction-bound
  // connection, so the call joins the caller's transaction.
  public RangeUnitPackage(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc.getJdbcTemplate();
  }

  static String buildBlock() {
    StringBuilder sql = new StringBuilder("DECLARE\n");
    for (String p : MAINLINE_PARAMS) {
      sql.append("  v_").append(p).append(" VARCHAR2(")
          .append("p_error_message".equals(p) ? 32767 : 4000).append(");\n");
    }
    sql.append("  v_cursor the.fta_630_mn_rg_un_pst.ref_m_r_u_p_result;\n");
    sql.append("BEGIN\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  v_").append(p).append(" := ?;\n"));
    sql.append("  the.fta_630_mn_rg_un_pst.mainline(\n");
    for (String p : MAINLINE_PARAMS) {
      sql.append("    ").append(p).append(" => v_").append(p).append(",\n");
    }
    sql.append("    p_m_r_u_p_results => v_cursor);\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  ? := v_").append(p).append(";\n"));
    sql.append("END;");
    return sql.toString();
  }

  /**
   * Runs {@code mainline} with {@code inputs} (parameter name to value; missing or empty ones are
   * null) and returns every parameter's value afterwards, null as "".
   */
  public Map<String, String> call(Map<String, String> inputs) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(BLOCK),
        (CallableStatement cs) -> {
          int n = MAINLINE_PARAMS.size();
          for (int i = 0; i < n; i++) {
            String v = inputs.get(MAINLINE_PARAMS.get(i));
            cs.setString(i + 1, v == null || v.isEmpty() ? null : v);
          }
          for (int i = 0; i < n; i++) {
            cs.registerOutParameter(n + i + 1, Types.VARCHAR);
          }
          cs.execute();
          Map<String, String> out = new HashMap<>();
          for (int i = 0; i < n; i++) {
            String v = cs.getString(n + i + 1);
            out.put(MAINLINE_PARAMS.get(i), v == null ? "" : v);
          }
          return out;
        });
  }
}
