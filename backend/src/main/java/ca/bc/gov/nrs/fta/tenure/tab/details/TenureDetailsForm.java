package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The FTA100 screen state for one tenure: the values {@code FTA_100_TENURE.GET} returned (keyed by
 * the package's parameter names, {@code ""} for null as legacy's beans held them), and the
 * protect/hide flags legacy's {@code Fta100TenureAction.setProtectedAndHideParams} and
 * {@code Fta100TenureForm} derive from them.
 *
 * <p>Legacy varies the flags by role and organization level. This app has no org levels and
 * only {@code FTA_ADMIN} writes tenures, so every flag here is the one an FTA Senior Admin at
 * Headquarters gets — {@code isSeniorAdmin} and {@code isHeadquarters}, not
 * {@code isSuperUser} (whose extra powers — changing Type, Admin Org and the SFL flag of an
 * existing file, bypassing the term limits — are not granted). The same flags decide what the
 * GET shows editable and what the PUT accepts, so the two cannot disagree.
 */
final class TenureDetailsForm {

  private final Map<String, String> v;
  private final boolean validLicenceToCutType;

  /**
   * @param getOutput             FTA_100_TENURE.GET's outputs
   * @param validLicenceToCutType {@code FTA_VALID_LICENCE_TO_CUT_TYPE} for the file type: whether
   *                              Purpose is {@code p_b05_purpose} rather than {@code p_purpose_code}
   */
  TenureDetailsForm(Map<String, String> getOutput, boolean validLicenceToCutType) {
    this.v = new HashMap<>(getOutput);
    this.validLicenceToCutType = validLicenceToCutType;
    normalize();
  }

  /** The values, as the screen holds them (and as SAVE is sent them). */
  Map<String, String> values() {
    return v;
  }

  String get(String param) {
    String s = v.get(param);
    return s == null ? "" : s;
  }

  boolean is(String param, String value) {
    return value.equals(get(param));
  }

  boolean yes(String param) {
    return is(param, "Y");
  }

  String type() {
    return get("p_file_type_code");
  }

  String status() {
    return get("p_file_status_st");
  }

  String prevStatus() {
    return get("p_prev_file_status_st");
  }

  private boolean typeIn(String... types) {
    return Set.of(types).contains(type());
  }

  private boolean typeStarts(String prefix) {
    return type().startsWith(prefix);
  }

  /** Fta100TenureAction.handleGet's adjustments to what GET returned. */
  private void normalize() {
    if (typeIn("B20", "B21", "B22", "B23") && !"PA".equals(status())) {
      v.put("p_show_frz_ind", "Y");
    }
    if ("A18".equals(type())) {
      v.put("p_show_area_and_total_aac_ind", "N");
      v.put("p_show_replaceable_fields_ind", "Y");
    }
    if (("PI".equals(status()) || "PE".equals(status())) && is("p_is_in_frz", "U")) {
      v.put("p_is_in_frz", "N");
    }
    if (is("p_waste_assess_reqd_ind", "U")) {
      v.put("p_waste_assess_reqd_ind", "Y");
    }
    if (is("p_tenure_term_months", "0") && is("p_tenure_term_years", "0")) {
      v.put("p_tenure_term_months", "");
      v.put("p_tenure_term_years", "");
    }
    if (orgMark()) {
      v.put("p_mark_issued_ind", "N");
    }
  }

  // ─── Legacy's display flags ────────────────────────────────────────────────

  /** protect_terms_section_ind: a Senior Admin may change the terms at any status but B10's. */
  boolean protectTermsSection() {
    return b10TermsLocked();
  }

  /** protectExpiryDateInd: as the terms. */
  boolean protectExpiryDate() {
    return b10TermsLocked();
  }

  private boolean b10TermsLocked() {
    return "B10".equals(type())
        && ("PE".equals(status()) || "PA".equals(status()) || prevStatus().isEmpty());
  }

  /** protectExtensionCountInd: open once the file has been held (H…), or at status A. */
  boolean protectExtensionCount() {
    return !(prevStatus().startsWith("H") || "A".equals(status()));
  }

  /** protectExtendedDateInd: the same rule. */
  boolean protectExtendedDate() {
    return protectExtensionCount();
  }

  /** protectReasonInd: locked while the file is pending (P…). */
  boolean protectReason() {
    return prevStatus().startsWith("P") || get("p_pfu_revision_count").isEmpty();
  }

  boolean protectMgmtUnit() {
    return typeIn("A28", "A04", "A44", "A29");
  }

  boolean protectTenureTerm() {
    return typeIn("A11", "B10");
  }

  boolean hideTenureTerm() {
    return typeIn("B05", "B06", "B10");
  }

  boolean protectStatus() {
    return "PE".equals(status()) || "EE".equals(status()) || "F05".equals(type());
  }

  boolean hideDistrict() {
    return typeStarts("A") || "P01".equals(type());
  }

  boolean hideDesignate() {
    return !(typeStarts("A") || "U2".equals(type()));
  }

  boolean hideSalvage() {
    return !typeIn("A01", "A18", "A41");
  }

  boolean protectPurpose() {
    return is("p_prev_b05_purpose", "PA");
  }

  boolean showPurposeSection() {
    return yes("p_show_purpose_ind");
  }

  boolean showPulpwoodAgreement() {
    return is("p_prev_b05_purpose", "PA") || "A18".equals(type());
  }

  boolean hideMark() {
    return typeIn("B04", "B05", "B06") && is("p_mark_issued_ind", "N");
  }

  /** isMNTypeDisabled: the map notation type opens only while the notation is PA or PI. */
  boolean mapNotationTypeOpen() {
    return "M01".equals(type()) && ("PA".equals(status()) || "PI".equals(status()));
  }

  /** "FOR", "SPEC" or "" (occupant) — which licence-to-cut list Purpose offers. */
  String purposeList() {
    if (typeIn("B04", "B07")) {
      return "FOR";
    }
    return typeStarts("S") ? "SPEC" : "";
  }

  /** Purpose is p_b05_purpose for a licence-to-cut type, p_purpose_code otherwise. */
  String purposeParam() {
    return validLicenceToCutType ? "p_b05_purpose" : "p_purpose_code";
  }

  String purpose() {
    return get(purposeParam());
  }

  boolean validLicenceToCutType() {
    return validLicenceToCutType;
  }

  /** Purpose with Annual Rent (the deposit section), when the mark section does not carry it. */
  boolean showDepositPurpose() {
    return yes("p_show_deposit_ind") && showPurposeSection() && !yes("p_show_b05_ind");
  }

  /** Purpose in the mark section (licences to cut). */
  boolean showMarkPurpose() {
    return yes("p_show_mark_ind") && yes("p_show_b05_ind");
  }

  boolean showDeposits() {
    return yes("p_show_deposit_ind") && is("p_show_b05_ind", "N");
  }

  /** A decked-timber (DT) licence has no mark of its own: legacy shows "ORG". */
  boolean orgMark() {
    String purpose = get("p_purpose_code");
    return "DT".equals(purpose)
        && (("B07".equals(type()) && !get("p_bcts_org_unit").isEmpty())
            || typeIn("B05", "B06"));
  }

  boolean showBctsArea() {
    return typeIn("B05", "B06") || yes("p_show_bcts_ind");
  }

  boolean showSpatialLater() {
    return typeIn("B05", "B06", "B07") && status().startsWith("P");
  }

  /** isPIandBlank: an existing file's payment method opens unless its id starts with D. */
  boolean paymentMethodOpen() {
    return !get("p_forest_file_id").startsWith("D");
  }

  boolean showMarkAdditional() {
    return "B01".equals(type()) && is("p_mark_issued_ind", "Y");
  }

  boolean oldRecreation() {
    return yes("p_recreation_ind") && get("p_forest_file_id").startsWith("900");
  }

  boolean fnAgreementMandatory() {
    return typeIn("A29", "A41", "A44");
  }

  boolean replaceable() {
    return yes("p_licence_replaceable_ind");
  }

  String licenseeLabel() {
    if (typeStarts("A") || yes("p_show_b05_ind")) {
      return "Licensee";
    }
    return typeIn("B08", "B09", "B14") ? "Markholder" : "Permittee";
  }

  /**
   * Whether a Headquarters Senior Admin sees this field open — the screen's protect and hide
   * rules for an existing file. {@code proposed} is the form as being edited, for the few
   * fields that open on another field's value (FUP First Nations fields, Pulpwood File).
   */
  List<String> editableFields(Map<String, String> proposed) {
    Set<String> f = new LinkedHashSet<>();
    if (yes("p_private_mark_ind")) {
      // GET: "Only Zone and quota type may be updated -- all other changes will be ignored".
      f.add("p_district_admin_zone");
      f.add("p_quota_type_code");
      return new ArrayList<>(f);
    }
    // Header
    if (!protectStatus()) {
      f.add("p_file_status_st");
    }
    if (!"F05".equals(type())) {
      f.add("p_file_status_date");
    }
    if (!hideDistrict()) {
      f.add("p_forest_district_no");
    }
    if (yes("p_major_tenure_ind")) {
      f.add("p_fn_agreement");
    }
    if (!protectMgmtUnit()) {
      f.add("p_mgmt_unit_type");
      f.add("p_mgmt_unit_id");
    }
    if ("M01".equals(type())) {
      if (mapNotationTypeOpen()) {
        f.add("p_map_notn_type_cd");
      }
      f.add("p_district_admin_zone");
    } else {
      if (!hideSalvage()) {
        f.add("p_salvage_ind");
      }
      if (yes("p_show_zone_ind")) {
        f.add("p_district_admin_zone");
      }
    }
    if ("B10".equals(type())) {
      f.add("p_fup_type_code");
      String fup = proposed == null ? get("p_fup_type_code")
          : proposed.getOrDefault("p_fup_type_code", get("p_fup_type_code"));
      if ("FN".equals(fup)) {
        f.add("p_fup_fn_usage_code");
        f.add("p_fup_cedar_species_ind");
        f.add("p_fup_treaty_purpose_ind");
      }
    }
    // Term and extension
    if (yes("p_show_tenure_term_ind")) {
      if (!protectTermsSection()) {
        f.add("p_award_date");
        if (!hideTenureTerm() && !protectTenureTerm()) {
          f.add("p_tenure_term_years");
          f.add("p_tenure_term_months");
        }
      }
      if (!protectTermsSection() || !protectExpiryDate()) {
        f.add("p_expiry_date");
      }
      if (yes("p_show_replaceable_fields_ind")) {
        if (is("p_sfl_ind", "N")) {
          f.add("p_licence_replaceable_ind");
        }
        f.add("p_maximum_harvest_volume");
        f.add("p_maximum_revenue_volume");
      }
      if (yes("p_show_extension_ind")) {
        if (!protectExtensionCount()) {
          f.add("p_extension_count");
        }
        if (!"A11".equals(type())) {
          if (!protectExtendedDate()) {
            f.add("p_extended_date");
          }
          if (replaceable()) {
            f.add("p_replaced_date");
          }
        }
        if (!replaceable() && !protectReason()) {
          f.add("p_extension_reason");
        }
        if (!hideDesignate()) {
          f.add("p_fn_held_level_code");
        }
      }
    }
    // Volumes, location, purpose, deposits, areas
    if (yes("p_show_pulpwood_ind")) {
      f.add("p_p01_total_aac");
      f.add("p_coniferous_aac");
      f.add("p_deciduous_aac");
    }
    if (yes("p_show_location_and_area_ind")) {
      f.add("p_permit_block_location");
      if (!yes("p_disable_area_ind")) {
        f.add("p_permit_block_area");
      }
    }
    if ((showDepositPurpose() || showMarkPurpose()) && !protectPurpose()) {
      f.add(purposeParam());
    }
    if (showDepositPurpose() && !get("p_annual_rent_fee_label").isEmpty()) {
      f.add("p_annual_rent_fee");
    }
    if (showDeposits()) {
      f.add("p_security_deposit_code");
      f.add("p_security_deposit_amount");
      f.add("p_other_deposit_code");
      f.add("p_other_deposit_amount");
    }
    if (yes("p_show_a06_a30_areas_ind") && !"A06".equals(type())) {
      f.add("p_init_licence_area");
      f.add("p_obligation_area");
      f.add("p_eliminated_area");
      f.add("p_other_area");
    }
    if (showBctsArea()) {
      if (yes("p_show_bcts_ind")) {
        f.add("p_bcts_fund_ind");
        f.add("p_bcts_org_unit");
        if (yes("p_show_payment_ind") && paymentMethodOpen()) {
          f.add("p_payment_method_cd");
        }
      }
      if (showSpatialLater() && !"PI".equals(status())) {
        f.add("p_sub_spatial_later_ind");
      }
    }
    // Mark
    if (yes("p_show_mark_ind")) {
      if (yes("p_show_waste_assess_ind")) {
        f.add("p_waste_assess_reqd_ind");
      }
      if (yes("p_show_frz_ind")) {
        f.add("p_is_in_frz");
      }
      f.add("p_fn_held_level_code");
      if (!"B02".equals(type())) {
        f.add("p_quota_type_code");
      }
      f.add("p_catastrophic_ind");
      f.add("p_deciduous_ind");
      f.add("p_cruise_based_ind");
      if (yes("p_compli_instru_enable_ind")) {
        f.add("p_marking_method_cd");
        f.add("p_markng_instrmnt_cd");
      }
      f.add("p_lands_region");
    }
    if (yes("p_show_occupancy_ind")) {
      f.add("p_other_marks_used1");
      f.add("p_other_marks_used2");
      f.add("p_other_marks_used3");
      f.add("p_other_marks_used4");
    }
    // Pulpwood agreement: shown for a PA licence; choosing PA opens it too (legacy's
    // pulpwoodRequiredWithPurposePA turns the section on).
    String proposedPurpose = proposed == null ? purpose()
        : proposed.getOrDefault(purposeParam(), purpose());
    if (showPulpwoodAgreement() || "PA".equals(proposedPurpose)) {
      f.add("p_pulpwood_file");
    }
    if (oldRecreation()) {
      f.add("p_rec_project_name");
    }
    return new ArrayList<>(f);
  }

  /** Why the tenure cannot be saved at all, or null when it can. */
  String notEditableReason(List<String> getWarnings) {
    if (type().isEmpty()) {
      return "The tenure has no file type; legacy FTA100 does not allow it to be saved.";
    }
    if (!yes("p_disable_save_ind")) {
      return null;
    }
    // GET explains itself for road marks, BCTS-owned files and spatial-less files.
    if (!getWarnings.isEmpty()) {
      return getWarnings.get(0);
    }
    if (type().startsWith("F") && !"F05".equals(type())) {
      return "File type " + type() + " cannot be changed on this screen.";
    }
    if ("F05".equals(type())) {
      return "An F05 file can be changed only while it is PI or HI.";
    }
    return "Legacy FTA100 does not allow this tenure to be saved.";
  }

  /** The sections and labels the screen shows (see {@link TenureDetailsLayout}). */
  TenureDetailsLayout layout() {
    boolean showMark = yes("p_show_mark_ind");
    String markDisplay = orgMark() ? "ORG" : hideMark() ? "" : get("p_timber_mark");
    return new TenureDetailsLayout(
        licenseeLabel(),
        yes("p_recreation_ind"),
        oldRecreation(),
        yes("p_private_mark_ind"),
        !hideDistrict(),
        yes("p_major_tenure_ind"),
        "M01".equals(type()),
        "M01".equals(type()) || yes("p_show_zone_ind"),
        !"M01".equals(type()) && !hideSalvage(),
        "A01".equals(type()),
        "B10".equals(type()),
        yes("p_show_tenure_term_ind"),
        !hideTenureTerm(),
        yes("p_show_tenure_term_ind") && yes("p_show_extension_ind"),
        yes("p_show_tenure_term_ind") && yes("p_show_replaceable_fields_ind"),
        !hideDesignate(),
        replaceable() ? "Rep. Count" : "Ext. Count",
        yes("p_show_est_total_area_ind"),
        yes("p_show_pulpwood_ind"),
        yes("p_show_location_and_area_ind"),
        showDepositPurpose(),
        showMarkPurpose(),
        key(purposeParam()),
        get("p_annual_rent_fee_label"),
        showDeposits(),
        yes("p_show_a06_a30_areas_ind"),
        "A06".equals(type()),
        yes("p_show_minor_ind"),
        showBctsArea(),
        showBctsArea() && yes("p_show_bcts_ind"),
        showBctsArea() && yes("p_show_bcts_ind") && yes("p_show_payment_ind"),
        showBctsArea() && showSpatialLater(),
        yes("p_show_area_and_total_aac_ind"),
        showMark,
        markDisplay,
        showMark && yes("p_show_waste_assess_ind"),
        showMark && yes("p_show_frz_ind"),
        showMark && showMarkAdditional(),
        showMark && !"B02".equals(type()),
        "A11".equals(type()) ? "Area Based" : "Cruise Based",
        yes("p_show_occupancy_ind"),
        showPulpwoodAgreement());
  }

  /** {@code p_file_status_st} → {@code fileStatusSt}: the API's name for a parameter. */
  static String key(String param) {
    String name = param.startsWith("p_") ? param.substring(2) : param;
    StringBuilder sb = new StringBuilder();
    boolean upper = false;
    for (char c : name.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        sb.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return sb.toString();
  }
}
