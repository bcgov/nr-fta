package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins legacy FTA950's Spatial Submissions columns, Issue Permit gate and package messages. */
class TenureAppRulesTest {

  @Test
  void issuePermitOnlyForApprovedOrIssued() {
    assertThat(TenureAppRules.canIssue("APP")).isTrue();
    assertThat(TenureAppRules.canIssue("ISS")).isTrue();
    assertThat(TenureAppRules.issueBlockedReason("APP")).isNull();
    assertThat(TenureAppRules.canIssue("INB")).isFalse();
    assertThat(TenureAppRules.canIssue("REJ")).isFalse();
    assertThat(TenureAppRules.canIssue(null)).isFalse();
    assertThat(TenureAppRules.issueBlockedReason("INB"))
        .isEqualTo("A permit can be issued only for an application that is approved or issued.");
  }

  @Test
  void issueNeedsADocument() {
    assertThat(TenureAppRules.validateIssue(null)).containsExactly("Permit document is mandatory.");
    assertThat(TenureAppRules.validateIssue("  ")).containsExactly("Permit document is mandatory.");
    assertThat(TenureAppRules.validateIssue("https://dm/files/1")).isEmpty();
    assertThat(TenureAppRules.validateIssue("x".repeat(2001)))
        .containsExactly("Permit document link must not exceed 2000 characters.");
  }

  @Test
  void timberLicenceColumns() {
    TenureAppColumns c = TenureAppColumns.of("A01", false, false, false, false, false);
    assertThat(c.status()).isTrue();
    assertThat(c.applicationType()).isTrue();
    assertThat(c.featureType()).isFalse();
    assertThat(c.chart()).isFalse();
    assertThat(c.cuttingPermit()).isTrue();
    assertThat(c.timberMark()).isTrue();
    assertThat(c.location()).isTrue();
    assertThat(c.pointOfCommencement()).isTrue();
    assertThat(c.area()).isTrue();
    assertThat(c.length()).isFalse();
    assertThat(c.cpLinksToPermit()).isTrue();
  }

  @Test
  void minorTslHasNoCpAndSomeTimberTypesNoTypeOrLink() {
    TenureAppColumns c = TenureAppColumns.of("A20", true, false, false, false, false);
    assertThat(c.cuttingPermit()).isFalse();
    assertThat(c.applicationType()).isFalse();
    assertThat(c.cpLinksToPermit()).isFalse();
  }

  @Test
  void chartFileShowsChartAndFeatureNotStatus() {
    TenureAppColumns c = TenureAppColumns.of("C01", false, false, false, false, false);
    assertThat(c.chart()).isTrue();
    assertThat(c.featureType()).isTrue();
    assertThat(c.status()).isFalse();
    assertThat(c.timberMark()).isFalse();
    assertThat(c.cpLinksToPermit()).isFalse();
  }

  @Test
  void rangeFileHasNoPofCOrArea() {
    TenureAppColumns c = TenureAppColumns.of("E01", false, false, false, true, false);
    assertThat(c.pointOfCommencement()).isFalse();
    assertThat(c.area()).isFalse();
  }

  @Test
  void f05ShowsLengthNotPofC() {
    TenureAppColumns c = TenureAppColumns.of("F05", false, false, false, false, false);
    assertThat(c.length()).isTrue();
    assertThat(c.pointOfCommencement()).isFalse();
    assertThat(c.area()).isTrue();
  }

  @Test
  void specialUseRoadWithRpHidesArea() {
    assertThat(TenureAppColumns.of("S01", false, false, false, false, true).area()).isFalse();
    assertThat(TenureAppColumns.of("S01", false, false, false, false, false).area()).isTrue();
    // The RP rule is S01/S02's only.
    assertThat(TenureAppColumns.of("B20", false, false, false, false, true).area()).isTrue();
  }

  @Test
  void singleMarkAndPermitTypes() {
    TenureAppColumns c = TenureAppColumns.of("B20", false, true, true, false, false);
    assertThat(c.timberMark()).isTrue();
    assertThat(c.location()).isTrue();
    assertThat(c.cuttingPermit()).isFalse();
  }

  @Test
  void ftcWarning() {
    String note = "NOTE: Information may not be displayed correctly for FTC submissions";
    assertThat(TenureAppColumns.warning("F05", false)).isEqualTo(note);
    assertThat(TenureAppColumns.warning("A28", false)).isEqualTo(note);
    assertThat(TenureAppColumns.warning("R01", true)).isEqualTo(note);
    assertThat(TenureAppColumns.warning("A01", false)).isNull();
  }

  @Test
  void applicationsBranchByFileType() {
    String c01 = TenureAppService.applicationsSql("C01");
    assertThat(c01).contains("feature_type_code = 'CH'");
    assertThat(TenureAppService.applicationsSql("A01")).contains("UNION").contains("'TU'");
    assertThat(TenureAppService.applicationsSql("F05")).contains("GROUP BY ta.tenure_app_id");
    assertThat(TenureAppService.applicationsSql("S01")).contains("ta.cutting_permit_id");
    assertThat(TenureAppService.applicationsSql("B20")).contains("'RPP'");
  }

  @Test
  void packageMessagesAreReadable() {
    assertThat(TenureAppLegacyMessages.readable(null)).isNull();
    assertThat(TenureAppLegacyMessages.readable("")).isNull();
    assertThat(TenureAppLegacyMessages.readable(
        "fta.web.error.user.custom.msg:The mark has no expiry date.;"))
        .isEqualTo("The mark has no expiry date.");
    assertThat(TenureAppLegacyMessages.readable(
        "fta.web.usr.database.record.invalid:FTA_950X_TEN_APP_ISSUE_PERMIT,ISSUE_PERMIT,"
            + "TENURE_APPLICATION;"))
        .isEqualTo("No Records Found. System Information: Table TENURE_APPLICATION"
            + " Package/Procedure FTA_950X_TEN_APP_ISSUE_PERMIT, ISSUE_PERMIT.");
    assertThat(TenureAppLegacyMessages.readable(
        "fta.web.usr.database.unexpected:FTA_950X_TEN_APP_ISSUE_PERMIT,ISSUE_PERMIT,-1,"
            + "ORA-00001: unique constraint (A,B) violated;"))
        .isEqualTo("An unexpected error has occurred in package/procedure"
            + " FTA_950X_TEN_APP_ISSUE_PERMIT, ISSUE_PERMIT - -1, ORA-00001: unique constraint"
            + " (A,B) violated Please contact System Support.");
  }
}
