package ca.bc.gov.nrs.fta.tenure.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA902's writes, by legacy's own PL/SQL, called rather than ported so {@code
 * Fta_Edit_Status_Change}, its transition matrix and the term rules stay exactly legacy's.
 *
 * <p>{@code FTA_902_CP_DETAIL.MAINLINE} is the package's only public entry for these actions, and
 * every action shares its one long parameter list, so a call passes every parameter by name and
 * gets them all back: {@code GET} supplies the {@code p_prev_*} values and revision counts that
 * {@code SAVE} then compares against.
 *
 * <p>Failure comes back as message keys in {@code p_error_message}, having possibly written
 * already: callers must throw inside their transaction when {@link
 * CuttingPermitMessages.Parsed#failed} says so.
 */
@Component
public class CuttingPermitDetailLegacy {

  /** MAINLINE's VARCHAR2 parameters, in declared order, around its CLOB and its cursor. */
  static final List<String> PARAMS = List.of(
      "p_action", "p_called_by", "p_hva_skey", "p_hva_id", "p_hdr_forest_file_id",
      "p_hdr_cutting_permit_id", "p_hdr_cut_block_id", "p_hdr_file_type_code",
      "p_hdr_file_type_code_desc", "p_hdr_admin_org", "p_hdr_licensee", "p_hdr_status",
      "p_hdr_status_as_of_date", "p_hdr_award_date", "p_hdr_expiry_date", "p_forest_district_no",
      "p_pfu_revision_count", "p_location", "p_object_area", "p_sched_a_area", "p_cp_id",
      "p_timber_mark", "p_bcts_fund_ind", "p_quota_type_code", "p_salvage_type_code",
      "p_prev_salvage_type_code", "p_deciduous_ind", "p_catastrophic_ind", "p_cruise_based_ind",
      "p_lands_region", "p_marking_method_cd", "p_markng_instrmnt_cd", "p_mark_status_st",
      "p_prev_mark_status_st", "p_mark_status_date", "p_prev_mark_status_date",
      "p_mark_issue_date", "p_prev_mark_issue_date", "p_mark_expiry_date",
      "p_prev_mark_expiry_date", "p_mark_extend_date", "p_prev_mark_extend_date",
      "p_mark_extend_rsn_cd", "p_mark_extend_count", "p_prev_mark_extend_count",
      "p_mark_tenure_term_years", "p_mark_tenure_term_months", "p_prev_mark_tenure_term_years",
      "p_prev_mark_tenure_term_months", "p_district_admn_zone", "p_higher_level_plan_reference",
      "p_mgmt_unit_id", "p_mgmt_unit_type_code", "p_hva_revision_count", "p_haa_revision_count",
      "p_hg_revision_count", "p_purpose_code", "p_amount_billed", "p_billing_date",
      "p_a11_revision_count", "p_minor_tsl_ind", "p_major_tsl_ind", "p_bcts_file_ind",
      "p_major_tenure_ind", "p_disable_save_ind", "p_disable_delete_ind",
      "p_district_override_reason", "p_fta_senior_admin_staff_ind",
      "p_fta_oil_gas_commission_ind", "p_disable_area_ind", "p_disable_retire_ind",
      // p_cp_legal_data (CLOB) is here.
      "p_map_url", "p_legend_url", "p_user_org_unit_no", "p_tenure_app_id", "p_userid",
      "p_trace_ind", "p_area_label", "p_fsj_ind", "p_cpp_postponement_id",
      "p_cpp_postponement_start_date", "p_cpp_postponement_expiry_date",
      "p_cpp_postponement_term_mnths", "p_cpp_postponement_term_yrs", "p_cpp_revision_count",
      "p_cpp_postponement_count", "p_cpp_rescinded", "p_cpp_original_mark_term_yr",
      "p_cpp_original_mark_term_mth", "p_ext_app_fee_waived", "p_ext_app_fee_paid", "p_frz_ind",
      "p_cb_susp_cnt", "p_error_message");


  private static final String MAINLINE_BLOCK = mainlineBlock();

  private final JdbcTemplate jdbc;

  public CuttingPermitDetailLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * Binds every parameter in a declared local, because MAINLINE's are all IN OUT, and reads each
   * back. The cursor (postponement history, opened on every action) is closed unread.
   */
  static String mainlineBlock() {
    StringBuilder sql = new StringBuilder("DECLARE ");
    StringJoiner args = new StringJoiner(", ");
    StringBuilder reads = new StringBuilder();
    for (int i = 0; i < PARAMS.size(); i++) {
      sql.append("v").append(i).append(" VARCHAR2(4000) := ?; ");
      if (PARAMS.get(i).equals("p_map_url")) {
        args.add("v_legal");
      }
      args.add("v" + i);
      reads.append("? := v").append(i).append("; ");
    }
    args.add("v_cur");
    return sql.append("v_legal CLOB; v_cur the.fta_902_cp_detail.ref_CPP; BEGIN ")
        .append("the.fta_902_cp_detail.mainline(").append(args).append("); ")
        .append("IF v_cur%ISOPEN THEN CLOSE v_cur; END IF; ")
        .append(reads).append("END;")
        .toString();
  }

  /**
   * Runs MAINLINE.
   *
   * @param in parameters by {@link #PARAMS} name; any left out are passed null
   * @return every parameter's value afterwards
   */
  public Map<String, String> mainline(Map<String, String> in) {
    int n = PARAMS.size();
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(MAINLINE_BLOCK),
        (CallableStatement cs) -> {
          for (int i = 0; i < n; i++) {
            cs.setString(i + 1, in.get(PARAMS.get(i)));
            cs.registerOutParameter(n + i + 1, Types.VARCHAR);
          }
          cs.execute();
          Map<String, String> out = new HashMap<>();
          for (int i = 0; i < n; i++) {
            out.put(PARAMS.get(i), cs.getString(n + i + 1));
          }
          return out;
        });
  }

  /**
   * {@code FTA_902_CP_CONDITIONS.MAINLINE}, for its SAVE and DELETE.
   *
   * @return the raw {@code p_error_message}
   */
  public String condition(
      String action, long hvaSkey, Long id, String themeCode, String description, String userId) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(
            "DECLARE v_action VARCHAR2(10) := ?; v_skey VARCHAR2(20) := ?; v_id VARCHAR2(20) := ?;"
                + " v_desc VARCHAR2(4000) := ?; v_code VARCHAR2(30) := ?; v_user VARCHAR2(100) := ?;"
                + " v_err VARCHAR2(4000); v_cur the.fta_902_cp_conditions.ref_CP_ISS; BEGIN"
                + " the.fta_902_cp_conditions.mainline("
                + "v_action, v_skey, v_id, v_desc, v_code, v_user, v_err, v_cur);"
                + " IF v_cur%ISOPEN THEN CLOSE v_cur; END IF; ? := v_err; END;"),
        (CallableStatement cs) -> {
          cs.setString(1, action);
          cs.setString(2, Long.toString(hvaSkey));
          cs.setString(3, id == null ? null : id.toString());
          cs.setString(4, description);
          cs.setString(5, themeCode);
          cs.setString(6, userId);
          cs.registerOutParameter(7, Types.VARCHAR);
          cs.execute();
          return cs.getString(7);
        });
  }
}
