package ca.bc.gov.nrs.fta.tenure.tab.details;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The FTA100 port's pure logic: messages, the protect/hide rules, the form validators. */
@DisplayName("Unit Test | Tenure details (FTA100)")
class TenureDetailsLogicTest {

  /** Lookups that all pass, with the override function answering {@code override}. */
  private static TenureDetailsValidator.Checks checks(boolean override) {
    return new TenureDetailsValidator.Checks() {
      public boolean mgmtUnitExists(String type, String id) {
        return true;
      }

      public boolean expiryMatchesTerm(String a, String y, String m, String e) {
        return true;
      }

      public boolean quotaTypeValid(String fileType, String quotaType) {
        return true;
      }

      public boolean timberMarkExists(String mark) {
        return true;
      }

      public boolean fileExists(String forestFileId) {
        return true;
      }

      public boolean sameLicensee(String forestFileId, String pulpwoodFile) {
        return true;
      }

      public boolean statusChangeNeedsOverride(String newStatus, String oldStatus) {
        return override;
      }
    };
  }

  /** An A01 forest licence, held (HI), as GET returns it. */
  private static Map<String, String> a01() {
    Map<String, String> v = new HashMap<>();
    TenureDetailsPackage.MAINLINE_PARAMS.forEach(p -> v.put(p, ""));
    v.put("p_forest_file_id", "A12345");
    v.put("p_file_type_code", "A01");
    v.put("p_file_status_st", "HI");
    v.put("p_prev_file_status_st", "HI");
    v.put("p_file_status_date", "2020-01-01");
    v.put("p_prev_file_status_date", "2020-01-01");
    v.put("p_admin_org_unit_no", "1");
    v.put("p_mgmt_unit_type", "U");
    v.put("p_mgmt_unit_id", "24");
    v.put("p_pfu_revision_count", "3");
    v.put("p_tt_revision_count", "1");
    v.put("p_award_date", "2010-04-01");
    v.put("p_prev_award_date", "2010-04-01");
    v.put("p_expiry_date", "2030-03-31");
    v.put("p_prev_expiry_date", "2030-03-31");
    v.put("p_tenure_term_years", "20");
    v.put("p_tenure_term_months", "0");
    v.put("p_prev_tenure_term_years", "20");
    v.put("p_prev_tenure_term_months", "0");
    v.put("p_show_tenure_term_ind", "Y");
    v.put("p_show_extension_ind", "Y");
    v.put("p_show_mark_ind", "Y");
    v.put("p_quota_type_code", "A");
    v.put("p_marking_method_cd", "S");
    v.put("p_markng_instrmnt_cd", "H");
    v.put("p_salvage_ind", "N");
    v.put("p_sfl_ind", "N");
    v.put("p_licence_replaceable_ind", "N");
    v.put("p_disable_save_ind", "N");
    v.put("p_waste_assess_reqd_ind", "U");
    return v;
  }

  @Test
  void parsesMessagesIntoErrorsAndWarnings() {
    TenureDetailsMessages.Parsed parsed = TenureDetailsMessages.parse(
        "sil.error.usr.required:Location;fta.status.change.invalid:HI,PP;"
            + "fta.web.error.user.custom.msg:~W,Tenure Term exceeds one year;"
            + "fta.web.error.user.custom.msg:BCTS Org must be entered if BCTS Fund is Y;");
    assertThat(parsed.errors()).containsExactly(
        "Location is mandatory.",
        "Status change from HI to PP is invalid.",
        "BCTS Org must be entered if BCTS Fund is Y");
    assertThat(parsed.warnings()).containsExactly("Tenure Term exceeds one year");
    assertThat(parsed.modified()).isFalse();
    assertThat(TenureDetailsMessages.parse(
            "fta.web.usr.database.record.modified:FTA_100_TENURE,SAVE,TENURE_TERM;").modified())
        .isTrue();
    assertThat(TenureDetailsMessages.parse("").failed()).isFalse();
  }

  @Test
  void mapsKeysAndParametersBothWays() {
    assertThat(TenureDetailsPackage.MAINLINE_PARAMS).hasSize(177).doesNotHaveDuplicates();
    for (String p : TenureDetailsPackage.MAINLINE_PARAMS) {
      assertThat(TenureDetailsService.param(TenureDetailsForm.key(p))).isEqualTo(p);
    }
    assertThat(TenureDetailsForm.key("p_p01_total_aac")).isEqualTo("p01TotalAac");
  }

  @Test
  void normalizesWhatGetReturned() {
    TenureDetailsForm form = new TenureDetailsForm(a01(), false);
    assertThat(form.get("p_waste_assess_reqd_ind")).isEqualTo("Y");
    Map<String, String> v = a01();
    v.put("p_tenure_term_years", "0");
    form = new TenureDetailsForm(v, false);
    assertThat(form.get("p_tenure_term_years")).isEmpty();
    assertThat(form.get("p_tenure_term_months")).isEmpty();
  }

  @Test
  void opensTheHeldForestLicenceFieldsLegacyOpensForASeniorAdmin() {
    TenureDetailsForm form = new TenureDetailsForm(a01(), false);
    List<String> fields = form.editableFields(null);
    assertThat(fields).contains(
        "p_file_status_st", "p_file_status_date", "p_mgmt_unit_type", "p_salvage_ind",
        "p_award_date", "p_tenure_term_years", "p_expiry_date", "p_extension_count",
        "p_extended_date", "p_extension_reason", "p_fn_held_level_code", "p_quota_type_code",
        "p_lands_region");
    // A… files: no district; Admin Org, Type and SFL are not a Senior Admin's to change.
    assertThat(fields).doesNotContain(
        "p_forest_district_no", "p_admin_org_unit_no", "p_file_type_code", "p_sfl_ind",
        "p_marking_method_cd", "p_replaced_date");
    assertThat(form.notEditableReason(List.of())).isNull();
    assertThat(form.layout().licenseeLabel()).isEqualTo("Licensee");
    assertThat(form.layout().showDistrict()).isFalse();
  }

  @Test
  void locksWhatLegacyLocks() {
    Map<String, String> v = a01();
    v.put("p_file_type_code", "A04");
    v.put("p_file_status_st", "PE");
    v.put("p_prev_file_status_st", "PE");
    TenureDetailsForm form = new TenureDetailsForm(v, false);
    List<String> fields = form.editableFields(null);
    // PE: status protected; pending: no extension count, extended date or reason; A04: no MU.
    assertThat(fields).doesNotContain("p_file_status_st", "p_extension_count",
        "p_extended_date", "p_extension_reason", "p_mgmt_unit_type", "p_salvage_ind");
    assertThat(fields).contains("p_award_date", "p_expiry_date");

    v = a01();
    v.put("p_private_mark_ind", "Y");
    assertThat(new TenureDetailsForm(v, false).editableFields(null))
        .containsExactly("p_district_admin_zone", "p_quota_type_code");

    v = a01();
    v.put("p_disable_save_ind", "Y");
    assertThat(new TenureDetailsForm(v, false).notEditableReason(List.of("Road mark")))
        .isEqualTo("Road mark");
  }

  @Test
  void opensFupFirstNationsFieldsOnlyForAnFnPermit() {
    Map<String, String> v = a01();
    v.put("p_file_type_code", "B10");
    v.put("p_file_status_st", "HI");
    TenureDetailsForm form = new TenureDetailsForm(v, false);
    assertThat(form.editableFields(null)).contains("p_fup_type_code")
        .doesNotContain("p_fup_fn_usage_code");
    assertThat(form.editableFields(Map.of("p_fup_type_code", "FN")))
        .contains("p_fup_fn_usage_code", "p_fup_cedar_species_ind");
  }

  @Test
  void acceptsAnUnchangedForm() {
    TenureDetailsForm form = new TenureDetailsForm(a01(), false);
    assertThat(TenureDetailsValidator.validate(form, new HashMap<>(form.values()), checks(false)))
        .isEmpty();
  }

  @Test
  void runsTheFormValidators() {
    TenureDetailsForm form = new TenureDetailsForm(a01(), false);
    Map<String, String> after = new HashMap<>(form.values());
    after.put("p_tenure_term_years", "25");
    after.put("p_tenure_term_months", "13");
    after.put("p_extended_date", "2029-01-01");
    after.put("p_extension_reason", "");
    after.put("p_salvage_ind", "");
    after.put("p_other_marks_used1", "ABC");
    after.put("p_other_marks_used2", "abc");
    assertThat(TenureDetailsValidator.validate(form, after, checks(false))).contains(
        "Term (mo) must be between 0 and 12.",
        "Current Expiry Date must be after Initial Expiry Date.",
        "Salvage is mandatory.",
        "Other Marks Used by this licence cannot contain duplicate entries.");

    after = new HashMap<>(form.values());
    after.put("p_tenure_term_years", "25");
    assertThat(TenureDetailsValidator.validate(form, after, checks(false)))
        .contains("For A01, the maximum Tenure term is 20 years.");
  }

  @Test
  void asksForAnOverrideReasonWhereLegacyDoes() {
    TenureDetailsForm form = new TenureDetailsForm(a01(), false);
    Map<String, String> after = new HashMap<>(form.values());
    after.put("p_award_date", "2010-05-01");
    after.put("p_expiry_date", "2030-04-30");
    // Held file, Effective Date changed.
    assertThat(TenureDetailsValidator.overrideNeeded(form, after, checks(false))).isTrue();
    assertThat(TenureDetailsValidator.validate(form, after, checks(false)))
        .contains("District Override Reason is mandatory.");
    after.put("p_district_override_reason", "Data correction");
    assertThat(TenureDetailsValidator.validate(form, after, checks(false)))
        .doesNotContain("District Override Reason is mandatory.");

    after = new HashMap<>(form.values());
    after.put("p_file_status_st", "HX");
    assertThat(TenureDetailsValidator.overrideNeeded(form, after, checks(true))).isTrue();
    assertThat(TenureDetailsValidator.overrideNeeded(form, after, checks(false))).isFalse();
  }
}
