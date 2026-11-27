package ca.bc.gov.nrs.fta.range.tenuretab;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_100RANGE_TENURE.mainline} — the package behind FTA100R, the range
 * Tenure tab — rather than re-writing it. Its actions, as {@code Fta100RangeTenureAction} posted
 * them:
 *
 * <ul>
 *   <li>{@code GET} — the file, its prev_* copies, revision counts and the disable indicators
 *       (SET_AUTHORITY_IND, SET_RETIRED_IND);
 *   <li>{@code SAVE} — an existing file (the E/H type lock, the PA rules,
 *       {@code FTA_EDIT_STATUS_CHANGE}, the CA/RRS/AR handling, the district override and its
 *       {@code FTA_DISTRICT_OVERRIDE.AUDIT_FF}), or, with no PROV_FOREST_USE revision count, a new
 *       one ({@code ADD_NEW_REC}: generates the file id, PROV_FOREST_USE and RANGE_TENURE);
 *   <li>{@code RETIRE}, {@code CANCEL_REPLACEMENT};
 *   <li>{@code SAVE_COND}, {@code DELETE_COND} — the special conditions.
 * </ul>
 *
 * <p>Every parameter but the last is {@code IN OUT VARCHAR2}, bound by name. The last,
 * {@code p_spec_condition_results}, is a ref cursor of the package's own type; it is declared
 * locally and closed unread (the special conditions are read by the range tenure detail).
 *
 * <p>The package neither commits nor rolls back: a SAVE that reports an error has still made the
 * writes before it, so callers run in a transaction and roll back on an error (legacy's action
 * did {@code store.rollback()}).
 */
@Component
public class RangeTenurePackage {

  /** mainline's VARCHAR2 parameters, in the spec's order (FTA_100RANGE_TENURE.pks, 42-103). */
  static final List<String> MAINLINE_PARAMS = List.of(
      "p_action", "p_forest_file_id", "p_file_type_code", "p_admin_org_unit_no",
      "p_forest_district_no", "p_licensee", "p_client_number", "p_client_locn_code",
      "p_sec_licensee_ind", "p_notes_label", "p_file_status_st", "p_prev_file_status_st",
      "p_file_status_date", "p_prev_file_status_date", "p_mgmt_unit_type", "p_mgmt_unit_id",
      "p_mgmt_unit_name", "p_district_admin_zone", "p_pfu_revision_count", "p_tt_revision_count",
      "p_rt_revision_count", "p_rsc_revision_count", "p_issue_year", "p_prev_issue_year",
      "p_expiry_year", "p_prev_expiry_year", "p_original_issue_year",
      "p_prev_original_issue_year", "p_prev_file_type_code", "p_tenure_term_years",
      "p_prev_tenure_term_years", "p_replacement_term_years", "p_prev_replacement_term_years",
      "p_replacement_count", "p_prev_replacement_count", "p_replacement_label",
      "p_show_tenure_term_ind", "p_range_ind", "p_disable_save_ind",
      "p_disable_replace_licence_ind", "p_district_override_reason",
      "p_district_admin_authority_ind", "p_user_org_unit_code", "p_user_org_unit_no",
      "p_disable_retire_ind", "p_tenure_app_id", "p_licence_replaceable_ind", "p_file_name",
      "p_rtspc_skey", "p_range_special_condition_code", "p_condition_title",
      "p_condition_description", "p_userid", "p_trace_ind", "p_error_message");

  static final String BLOCK = buildBlock();

  private final JdbcTemplate jdbc;

  // Its JdbcTemplate shares the transaction-bound connection, so a call joins the caller's
  // transaction.
  public RangeTenurePackage(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc.getJdbcTemplate();
  }

  private static String buildBlock() {
    StringBuilder sql = new StringBuilder("DECLARE\n");
    for (String p : MAINLINE_PARAMS) {
      sql.append("  v_").append(p).append(" VARCHAR2(")
          .append("p_error_message".equals(p) ? 32767 : 4000).append(");\n");
    }
    sql.append("  v_results the.fta_100range_tenure.ref_spec_condition_results;\n");
    sql.append("BEGIN\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  v_").append(p).append(" := ?;\n"));
    sql.append("  the.fta_100range_tenure.mainline(\n");
    for (String p : MAINLINE_PARAMS) {
      sql.append("    ").append(p).append(" => v_").append(p).append(",\n");
    }
    sql.append("    p_spec_condition_results => v_results);\n");
    sql.append("  IF v_results%ISOPEN THEN CLOSE v_results; END IF;\n");
    MAINLINE_PARAMS.forEach(p -> sql.append("  ? := v_").append(p).append(";\n"));
    sql.append("END;");
    return sql.toString();
  }

  /**
   * Runs mainline with {@code inputs} (parameter to value; missing ones are null) and returns
   * every parameter's value afterwards ("" for null). Failures are not thrown: the package
   * reports them in {@code p_error_message}.
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
