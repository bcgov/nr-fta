package ca.bc.gov.nrs.fta.range.tenuretab;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRules.Who;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Fta100RangeTenureForm's validators and field rules, and the action's adjustments. */
@DisplayName("Unit Test | RangeTenureRules (FTA100R)")
class RangeTenureRulesTest {

  private static final Who SENIOR = new Who(true, false);
  private static final Who ADMIN = new Who(true, true);
  private static final Who DISTRICT = new Who(false, false);

  private static final RangeTenureRules.Checks OK = new RangeTenureRules.Checks() {
    @Override
    public boolean mgmtUnitExists(String type, String id) {
      return true;
    }

    @Override
    public boolean expiryMatchesTerm(String award, String years, String months, String expiry) {
      int start = Integer.parseInt(award.substring(0, 4));
      int end = Integer.parseInt(expiry.substring(0, 4));
      return start + Integer.parseInt(years) - 1 == end;
    }
  };

  private static final Map<String, Set<String>> CODES = Map.of(
      "p_file_status_st", Set.of("A", "AR", "CA", "RRS", "PA", "EE", "D"),
      "p_file_type_code", Set.of("E01", "E02", "E03", "H01", "H02", "H03"),
      "p_forest_district_no", Set.of("1", "2"));

  private Map<String, String> get;
  private Map<String, String> form;

  /** An active E01 issued 2015 for 20 years, as the package's GET returns it. */
  @BeforeEach
  void setUp() {
    get = new HashMap<>();
    get.put("p_forest_file_id", "RAN001");
    get.put("p_file_type_code", "E01");
    get.put("p_prev_file_type_code", "E01");
    get.put("p_file_status_st", "A");
    get.put("p_prev_file_status_st", "A");
    get.put("p_file_status_date", "2020-01-01");
    get.put("p_prev_file_status_date", "2020-01-01");
    get.put("p_admin_org_unit_no", "1");
    get.put("p_forest_district_no", "1");
    get.put("p_mgmt_unit_type", "Q");
    get.put("p_mgmt_unit_id", "01");
    get.put("p_district_admin_zone", "Z1");
    get.put("p_issue_year", "2015");
    get.put("p_prev_issue_year", "2015");
    get.put("p_expiry_year", "2034");
    get.put("p_prev_expiry_year", "2034");
    get.put("p_tenure_term_years", "20");
    get.put("p_prev_tenure_term_years", "20");
    get.put("p_replacement_term_years", "");
    get.put("p_prev_replacement_term_years", "");
    get.put("p_show_tenure_term_ind", "Y");
    get.put("p_disable_replace_licence_ind", "N");
    get.put("p_disable_retire_ind", "Y");
    form = new HashMap<>(get);
  }

  private List<String> validate() {
    return RangeTenureRules.validate(form, get, CODES, OK, 2026);
  }

  @Test
  void anUnchangedFileIsValid() {
    assertThat(validate()).isEmpty();
  }

  @Nested
  @DisplayName("field rules")
  class Fields {
    @Test
    void districtOnlyForFtaAdmin() {
      assertThat(RangeTenureRules.editableFields(get, ADMIN)).contains("forestDistrictNo");
      assertThat(RangeTenureRules.editableFields(get, SENIOR)).doesNotContain("forestDistrictNo");
      assertThat(RangeTenureRules.editableFields(get, DISTRICT))
          .doesNotContain("forestDistrictNo");
    }

    @Test
    void typeForSeniorsAlwaysAndTheRangeTechOnlyOnPOrPa() {
      assertThat(RangeTenureRules.editableFields(get, SENIOR)).contains("fileTypeCode");
      assertThat(RangeTenureRules.editableFields(get, DISTRICT)).doesNotContain("fileTypeCode");
      get.put("p_prev_file_status_st", "PA");
      assertThat(RangeTenureRules.editableFields(get, DISTRICT)).contains("fileTypeCode");
    }

    @Test
    void statusIsProtectedOnPeAndEe() {
      get.put("p_file_status_st", "EE");
      assertThat(RangeTenureRules.editableFields(get, ADMIN)).doesNotContain("fileStatusSt");
    }

    @Test
    void historicalAndReplacementOnlyForE01AndH01() {
      assertThat(RangeTenureRules.editableFields(get, DISTRICT))
          .contains("originalIssueYear", "replacementTermYears");
      get.put("p_file_type_code", "E02");
      assertThat(RangeTenureRules.editableFields(get, DISTRICT))
          .doesNotContain("originalIssueYear", "replacementTermYears");
    }

    @Test
    void retireOnlyWhenThePackageSaysN() {
      assertThat(RangeTenureRules.retirable(get)).isFalse();
      get.put("p_disable_retire_ind", "N");
      assertThat(RangeTenureRules.retirable(get)).isTrue();
    }

    @Test
    void cancelReplacementNeedsAReplacementTermOnAnE01OrH01() {
      assertThat(RangeTenureRules.canCancelReplacement(get)).isFalse();
      get.put("p_replacement_term_years", "20");
      assertThat(RangeTenureRules.canCancelReplacement(get)).isTrue();
      get.put("p_file_type_code", "E02");
      assertThat(RangeTenureRules.canCancelReplacement(get)).isFalse();
    }

    @Test
    void theTypeListIsTheFamily() {
      assertThat(RangeTenureRules.sameFamily("E01", "E03")).isTrue();
      assertThat(RangeTenureRules.sameFamily("E01", "H01")).isFalse();
    }
  }

  @Nested
  @DisplayName("adjustments")
  class Adjust {
    @Test
    void aStatusChangeWithTheDateUntouchedIsAsOfToday() {
      form.put("p_file_status_st", "CA");
      RangeTenureRules.adjust(form, get, LocalDate.of(2026, 11, 26));
      assertThat(form.get("p_file_status_date")).isEqualTo("2026-11-26");
    }

    @Test
    void aStatusChangeWithANewDateKeepsIt() {
      form.put("p_file_status_st", "CA");
      form.put("p_file_status_date", "2026-01-15");
      RangeTenureRules.adjust(form, get, LocalDate.of(2026, 11, 26));
      assertThat(form.get("p_file_status_date")).isEqualTo("2026-01-15");
    }

    @Test
    void zoneAndManagementUnitAreUpperCased() {
      form.put("p_district_admin_zone", "z9");
      form.put("p_mgmt_unit_id", "ab");
      RangeTenureRules.adjust(form, get, LocalDate.of(2026, 11, 26));
      assertThat(form.get("p_district_admin_zone")).isEqualTo("Z9");
      assertThat(form.get("p_mgmt_unit_id")).isEqualTo("AB");
    }
  }

  @Nested
  @DisplayName("validators")
  class Validators {
    @Test
    void theTypeStaysInItsFamily() {
      form.put("p_file_type_code", "H01");
      assertThat(validate()).contains("Type change from E01 to H01 is invalid.");
      form.put("p_file_type_code", "E02");
      assertThat(validate()).doesNotContain("Type change from E01 to E02 is invalid.");
    }

    @Test
    void zoneIsRequiredUnlessEe() {
      form.put("p_district_admin_zone", "");
      assertThat(validate()).contains("Zone is mandatory.");
      form.put("p_file_status_st", "EE");
      assertThat(validate()).doesNotContain("Zone is mandatory.");
    }

    @Test
    void aChangedStatusMustBeCurrent() {
      form.put("p_file_status_st", "XX");
      assertThat(validate()).contains("Status (XX) is an expired code.");
    }

    @Test
    void issuePlusTermMustEqualExpiry() {
      form.put("p_expiry_year", "2036");
      assertThat(validate()).contains("If both tenure term and Initial Expiry date have been"
          + " entered then Effective date plus tenure term must equal Initial Expiry date.");
    }

    @Test
    void termOrExpiryIsRequiredOffPa() {
      form.put("p_tenure_term_years", "");
      form.put("p_expiry_year", "");
      assertThat(validate()).contains(
          "You must enter values for either the Tenure Term (years) or the Expiry Date field.");
    }

    @Test
    void effectiveYearIsRequiredOffPa() {
      form.put("p_issue_year", "");
      assertThat(validate()).contains("Effective Date is mandatory.");
    }

    @Test
    void noTermChecksOnAPaFile() {
      get.put("p_file_status_st", "PA");
      get.put("p_prev_file_status_st", "PA");
      get.put("p_show_tenure_term_ind", "N");
      form = new HashMap<>(get);
      form.put("p_issue_year", "");
      form.put("p_tenure_term_years", "");
      form.put("p_expiry_year", "");
      assertThat(validate()).isEmpty();
    }

    @Test
    void expiryYearMayNotPrecedeEffectiveYear() {
      form.put("p_expiry_year", "2010");
      form.put("p_tenure_term_years", "");
      assertThat(validate()).contains("Expiry Year must be greater or equal to Effective Year.");
    }

    @Test
    void mgmtUnitMustExist() {
      List<String> e = RangeTenureRules.validate(form, get, CODES,
          new RangeTenureRules.Checks() {
            @Override
            public boolean mgmtUnitExists(String type, String id) {
              return false;
            }

            @Override
            public boolean expiryMatchesTerm(String a, String y, String m, String x) {
              return true;
            }
          }, 2026);
      assertThat(e).contains("Invalid Management Unit type/id combination.");
    }
  }

  @Nested
  @DisplayName("term limits by type and start year")
  class TermLimits {
    @ParameterizedTest(name = "{0} from {1} for {2} years: {3}")
    @CsvSource({
      "E01, 2015, 20, ''",
      "E01, 2015, 10, 'For E01, Tenure term must be at least 15 and no more than 25 years.'",
      "H01, 2014, 26, 'For H01, Tenure term must be at least 15 and no more than 25 years.'",
      "E01, 2010, 11, 'For E01, Tenure term must be at least 1 and no more than 10 years.'",
      "E01, 2010, 10, ''",
      "E02, 2015, 10, ''",
      "E02, 2015, 11, 'For E02, Tenure term must be at least 1 and no more than 10 years.'",
      "H02, 2012, 6, 'For H02, Tenure term must be at least 1 and no more than 5 years.'",
      "H03, 2015, 2, 'For H03, the Tenure term must be 1 year(s).'",
      "H03, 2015, 1, ''",
      "E03, 2015, 6, 'For E03, Tenure term must be for at least one month and no more than 5"
          + " years.'",
    })
    void byTypeAndStartYear(String type, int start, int term, String expected) {
      get.put("p_file_type_code", type);
      get.put("p_prev_file_type_code", type);
      form = new HashMap<>(get);
      form.put("p_issue_year", String.valueOf(start));
      form.put("p_tenure_term_years", String.valueOf(term));
      form.put("p_expiry_year", String.valueOf(start + term - 1));
      assertThat(RangeTenureRules.tenureTermLimits(form, get).orElse("")).isEqualTo(expected);
    }

    @Test
    void anOldLicenceBeingReplacedSkipsTheTenYearLimit() {
      form.put("p_issue_year", "2010");
      form.put("p_tenure_term_years", "12");
      form.put("p_expiry_year", "2021");
      form.put("p_replacement_term_years", "20");
      assertThat(RangeTenureRules.tenureTermLimits(form, get)).isEmpty();
    }

    @Test
    void onlyWhenTheTermOrExpiryChanged() {
      get.put("p_tenure_term_years", "5");
      get.put("p_prev_tenure_term_years", "5");
      get.put("p_expiry_year", "2019");
      get.put("p_prev_expiry_year", "2019");
      form = new HashMap<>(get);
      assertThat(RangeTenureRules.tenureTermLimits(form, get)).isEmpty();
    }

    @Test
    void notWhenEitherStatusIsPa() {
      form.put("p_file_status_st", "PA");
      form.put("p_tenure_term_years", "3");
      form.put("p_expiry_year", "2017");
      assertThat(RangeTenureRules.tenureTermLimits(form, get)).isEmpty();
    }
  }

  @Nested
  @DisplayName("replacement")
  class Replacement {
    @Test
    void replacementTermIsFifteenToTwentyFive() {
      form.put("p_replacement_term_years", "10");
      assertThat(validate())
          .contains("For E01, Replacement term must be at least 15 and no more than 25 years.");
      form.put("p_replacement_term_years", "20");
      assertThat(validate()).isEmpty();
    }

    @Test
    void aNewReplacementOnAnExpiredTenureNeedsAnOverrideReason() {
      form.put("p_issue_year", "2000");
      form.put("p_tenure_term_years", "10");
      form.put("p_expiry_year", "2009");
      get.putAll(Map.of("p_issue_year", "2000", "p_tenure_term_years", "10",
          "p_expiry_year", "2009", "p_prev_expiry_year", "2009",
          "p_prev_tenure_term_years", "10"));
      form.put("p_replacement_term_years", "20");
      assertThat(RangeTenureRules.overrideRequired(form, get, 2026)).isTrue();
      assertThat(validate()).contains("District Override Reason is mandatory.");
      form.put("p_district_override_reason", "Late paperwork");
      assertThat(validate()).doesNotContain("District Override Reason is mandatory.");
    }

    @Test
    void noOverrideWhenTheReplacementWasAlreadyThere() {
      get.put("p_prev_replacement_term_years", "20");
      form.put("p_expiry_year", "2009");
      form.put("p_replacement_term_years", "20");
      assertThat(RangeTenureRules.overrideRequired(form, get, 2026)).isFalse();
    }

    @Test
    void theEntryPeriodWarningAsLegacyComputedIt() {
      Map<String, String> saved = new HashMap<>(form);
      saved.put("p_replacement_term_years", "20");
      saved.put("p_expiry_year", "2027");
      // Between Feb 1 of the year before expiry and Jan 31 of the expiry year: no warning.
      assertThat(RangeTenureRules.replacementEntryWarning(saved, get, LocalDate.of(2026, 6, 1)))
          .isEmpty();
      assertThat(RangeTenureRules.replacementEntryWarning(saved, get, LocalDate.of(2027, 1, 31)))
          .isEmpty();
      assertThat(RangeTenureRules.replacementEntryWarning(saved, get, LocalDate.of(2027, 2, 1)))
          .contains(RangeTenureRules.REPLACEMENT_ENTRY_PERIOD);
      assertThat(RangeTenureRules.replacementEntryWarning(saved, get, LocalDate.of(2026, 1, 31)))
          .contains(RangeTenureRules.REPLACEMENT_ENTRY_PERIOD);
    }

    @Test
    void noEntryPeriodWarningWhenTheReplacementIsUnchanged() {
      get.put("p_prev_replacement_term_years", "20");
      Map<String, String> saved = new HashMap<>(form);
      saved.put("p_replacement_term_years", "20");
      assertThat(RangeTenureRules.replacementEntryWarning(saved, get, LocalDate.of(2040, 1, 1)))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("special conditions")
  class Conditions {
    private final Set<String> current = Set.of("GRZ", "OTH");

    @Test
    void codeTitleAndDescriptionAreRequired() {
      assertThat(RangeTenureRules.validateCondition("", "", "", null, current)).containsExactly(
          "Condition Code is mandatory.",
          "Condition Title is mandatory if Condition Type is Other.",
          "Condition Description is mandatory if Condition Type is Other.");
    }

    @Test
    void descriptionIsAtMost500() {
      assertThat(RangeTenureRules.validateCondition("OTH", "T", "x".repeat(501), null, current))
          .containsExactly("Condition Description cannot exceed a maximum of 500 characters.");
      assertThat(RangeTenureRules.validateCondition("OTH", "T", "x".repeat(500), null, current))
          .isEmpty();
    }

    @Test
    void aNewCodeMustBeCurrent() {
      assertThat(RangeTenureRules.validateCondition("OLD", "T", "D", null, current))
          .containsExactly("Special Condition (OLD) is an expired code.");
      assertThat(RangeTenureRules.validateCondition("OLD", "T", "D", "OLD", current)).isEmpty();
    }
  }

  @Nested
  @DisplayName("messages")
  class Messages {
    @Test
    void warningsDoNotFailASave() {
      RangeTenureMessages.Parsed p = RangeTenureMessages.parse(
          "fta.web.warning.retire.allowed:~W;fta.web.warning.FtaAuditLogged:~W,Tenure term change"
              + " audited;");
      assertThat(p.failed()).isFalse();
      assertThat(p.warnings())
          .containsExactly("Tenure may now be retired.", "Tenure term change audited.");
    }

    @Test
    void packageErrorsAreLegacysText() {
      RangeTenureMessages.Parsed p = RangeTenureMessages.parse(
          "fta.status.change.invalid:PA,A;fta.web.error.usr.duplicateNonOtherRangeCondition:GRZ;");
      assertThat(p.errors()).containsExactly("Status change from PA to A is invalid.",
          "Range tenure already has a special condition of type GRZ.");
    }

    @Test
    void aRevisionMismatchIsModified() {
      assertThat(RangeTenureMessages.parse(
          "fta.web.usr.database.record.modified:FTA_100RANGE_TENURE,SAVE,RANGE_TENURE;")
          .modified()).isTrue();
    }
  }
}
