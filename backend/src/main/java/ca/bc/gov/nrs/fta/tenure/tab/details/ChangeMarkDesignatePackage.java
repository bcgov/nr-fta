package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls the legacy {@code THE.FTA_101_CHANGE_MARK_DESIG.mainline} — the package behind FTA101
 * Change Mark Designate. Its GET reads a source file's {@code TIMBER_TENURE.MARK_DESIGNATE} (and
 * the target's revision count); its SAVE copies that designate onto the target when the two
 * files share region, file type and a licensee.
 *
 * <p>Every parameter is {@code IN OUT VARCHAR2}, bound by name. The package neither commits nor
 * rolls back, so the caller runs it in a transaction (legacy's BasicStore did the same).
 */
@Component
public class ChangeMarkDesignatePackage {

  /** {@code mainline}'s parameters, in the spec's order. */
  static final List<String> MAINLINE_PARAMS = List.of(
      "p_action", "p_forest_file_id", "p_pfu_revision_count", "p_file_type_cd",
      "p_file_type_desc", "p_admin_org", "p_licencee", "p_status", "p_status_date",
      "p_award_date", "p_expiry_date", "p_forest_file_id_src", "p_mark_designate",
      "p_update_userid", "p_revision_count", "p_error_message");

  private static final String BLOCK = buildBlock();

  private final JdbcTemplate jdbc;

  // Its JdbcTemplate shares the transaction-bound connection, so the call joins the caller's
  // transaction.
  public ChangeMarkDesignatePackage(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc.getJdbcTemplate();
  }

  private static String buildBlock() {
    StringBuilder sql = new StringBuilder("DECLARE\n");
    for (String p : MAINLINE_PARAMS) {
      sql.append("  v_").append(p).append(" VARCHAR2(")
          .append("p_error_message".equals(p) ? 32767 : 4000).append(");\n");
    }
    sql.append("BEGIN\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  v_").append(p).append(" := ?;\n"));
    sql.append("  the.fta_101_change_mark_desig.mainline(\n");
    for (int i = 0; i < MAINLINE_PARAMS.size(); i++) {
      String p = MAINLINE_PARAMS.get(i);
      sql.append("    ").append(p).append(" => v_").append(p)
          .append(i < MAINLINE_PARAMS.size() - 1 ? ",\n" : ");\n");
    }
    MAINLINE_PARAMS.forEach(p -> sql.append("  ? := v_").append(p).append(";\n"));
    sql.append("END;");
    return sql.toString();
  }

  /**
   * Runs {@code mainline} with {@code inputs} (missing ones are null) and returns every
   * parameter afterwards. Failures come back in {@code p_error_message}.
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
