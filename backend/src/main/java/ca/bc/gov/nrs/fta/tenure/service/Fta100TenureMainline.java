package ca.bc.gov.nrs.fta.tenure.service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A call to legacy {@code THE.FTA_100_TENURE.mainline} for one action, as an anonymous PL/SQL
 * block. The package runs with its owner's rights (no {@code AUTHID} clause), so the
 * application needs only {@code EXECUTE} on it, not write grants on the tables it touches
 * (such as {@code MRK_DSG_NUM_CTL}, which the application account may only read).
 *
 * <p>Every {@code mainline} parameter is a {@code VARCHAR2}, almost all {@code IN OUT}, so each
 * gets a variable; the ones not named stay null. The block binds the named inputs in order,
 * then two outputs: {@code output}'s value and {@code p_error_message}.
 */
final class Fta100TenureMainline {

  private Fta100TenureMainline() {}

  /** {@code mainline}'s parameters, in the spec's order (FTA_100_TENURE.PKS). */
  static final List<String> PARAMS = List.of(
      "p_action", "p_called_by", "p_forest_file_id", "p_file_type_code", "p_admin_org_unit_no",
      "p_forest_district_no", "p_licensee", "p_client_number", "p_client_locn_code",
      "p_sec_licensee_ind", "p_notes_label", "p_file_status_st", "p_prev_file_status_st",
      "p_file_status_date", "p_prev_file_status_date", "p_mgmt_unit_type", "p_mgmt_unit_id",
      "p_mgmt_unit_name", "p_district_admin_zone", "p_pfu_revision_count", "p_tt_revision_count",
      "p_ltc_revision_count", "p_sup_revision_count", "p_pw_revision_count",
      "p_tla_revision_count", "p_td_revision_count", "p_lcf_revision_count",
      "p_hs_revision_count", "p_award_date", "p_prev_award_date", "p_expiry_date",
      "p_prev_expiry_date", "p_prev_file_type_code", "p_tenure_term_years",
      "p_tenure_term_months", "p_prev_tenure_term_years", "p_prev_tenure_term_months",
      "p_extended_date", "p_prev_extended_date", "p_extension_count", "p_prev_extension_count",
      "p_extension_reason", "p_prev_extension_reason", "p_mark_designate",
      "p_permit_block_location", "p_permit_block_area", "p_a04_a28_licence_area",
      "p_sched_a_aac", "p_sched_b_aac", "p_licence_area", "p_total_aac", "p_cp_id",
      "p_timber_mark", "p_mark_issued_ind", "p_mark_expiry_date", "p_prev_mark_expiry_date",
      "p_bcts_fund_ind", "p_b05_purpose", "p_prev_b05_purpose", "p_quota_type_code",
      "p_deciduous_ind", "p_catastrophic_ind", "p_cruise_based_ind", "p_cascade_split_code",
      "p_lands_region", "p_marking_method_cd", "p_markng_instrmnt_cd", "p_hva_revision_count",
      "p_haa_revision_count", "p_hva_skey", "p_mark_award_date", "p_prev_mark_award_date",
      "p_mark_extended_date", "p_mark_extension_count", "p_prev_mark_extension_count",
      "p_mark_extension_reason", "p_security_deposit_amount", "p_security_deposit_code",
      "p_other_deposit_amount", "p_other_deposit_code", "p_occupation_authority_files",
      "p_other_marks_used1", "p_other_marks_used2", "p_other_marks_used3", "p_other_marks_used4",
      "p_purpose_code", "p_annual_rent_fee_label", "p_annual_rent_fee", "p_pulpwood_file",
      "p_prev_pulpwood_file", "p_coniferous_aac", "p_deciduous_aac", "p_p01_total_aac",
      "p_init_licence_area", "p_obligation_area", "p_eliminated_area", "p_other_area",
      "p_est_total_area_vol", "p_valid_mark_ind", "p_tsl_licence_ha", "p_tsl_active_ha",
      "p_bcts_org_unit", "p_rec_site_area", "p_rec_site_length", "p_rec_project_name",
      "p_old_rec_project", "p_utm_zone", "p_utm_easting", "p_utm_northing", "p_rec_project_type",
      "p_rec_proj_update_date", "p_rec_proj_revision_count", "p_recreation_ind",
      "p_show_zone_ind", "p_show_occupant_ind", "p_show_tenure_term_ind", "p_show_extension_ind",
      "p_show_est_total_area_ind", "p_show_pulpwood_ind", "p_show_deposit_ind",
      "p_show_purpose_ind", "p_show_location_and_area_ind", "p_show_area_and_total_aac_ind",
      "p_show_area_and_sched_aac_ind", "p_show_a06_a30_areas_ind", "p_show_b05_ind",
      "p_show_occupancy_ind", "p_show_bcts_ind", "p_show_mark_ind", "p_show_minor_ind",
      "p_private_mark_ind", "p_minor_tsl_ind", "p_major_tsl_ind", "p_bcts_file_ind",
      "p_single_tsl_ind", "p_major_tenure_ind", "p_payment_method_cd", "p_prev_pay_method_cd",
      "p_sub_spatial_later_ind", "p_show_payment_ind", "p_salvage_ind", "p_disable_save_ind",
      "p_disable_delete_ind", "p_disable_replace_tenure_ind", "p_district_override_reason",
      "p_district_admin_authority_ind", "p_user_org_unit_code", "p_user_org_unit_no",
      "p_disable_area_ind", "p_disable_retire_ind", "p_disable_assign_mark_ind",
      "p_compli_instru_enable_ind", "p_tenure_app_id", "p_fup_type_code", "p_fup_fn_usage_code",
      "p_fup_treaty_purpose_ind", "p_fup_cedar_species_ind", "p_replaced_date",
      "p_prev_replaced_date", "p_maximum_harvest_volume", "p_maximum_revenue_volume",
      "p_revenue_share_volume", "p_show_replaceable_fields_ind", "p_licence_replaceable_ind",
      "p_sfl_ind", "p_harvest_unit_of_measure_code", "p_map_notn_type_cd",
      "p_waste_assess_reqd_ind", "p_show_waste_assess_ind", "p_show_frz_ind", "p_is_in_frz",
      "p_fn_agreement", "p_fn_held_level_code", "p_is_super_user", "p_userid", "p_trace_ind",
      "p_error_message");

  /**
   * The block for {@code action}: binds each of {@code inputs} (in order), then returns
   * {@code output} and {@code p_error_message}.
   */
  static String block(String action, List<String> inputs, String output) {
    StringBuilder sql = new StringBuilder("DECLARE\n");
    PARAMS.forEach(p -> sql.append("  v_").append(p).append(" VARCHAR2(4000);\n"));
    sql.append("BEGIN\n  v_p_action := '").append(action).append("';\n");
    inputs.forEach(p -> sql.append("  v_").append(p).append(" := ?;\n"));
    sql.append("  the.fta_100_tenure.mainline(\n")
        .append(PARAMS.stream().map(p -> "    " + p + " => v_" + p)
            .collect(Collectors.joining(",\n")))
        .append(");\n")
        .append("  ? := v_").append(output).append(";\n")
        .append("  ? := v_p_error_message;\n")
        .append("END;");
    return sql.toString();
  }

  /**
   * The package's message without its key prefixes ("key:text;"): the custom messages carry
   * their text after {@code fta.web.error.user.custom.msg:}.
   */
  static String readable(String error) {
    return error.replace("fta.web.error.user.custom.msg:", "").replaceAll(";\\s*$", "").trim();
  }
}
