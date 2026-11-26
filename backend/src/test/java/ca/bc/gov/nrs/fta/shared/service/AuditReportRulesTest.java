package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Criteria;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Report;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Scope;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | AuditReportRules (FTA675)")
class AuditReportRulesTest {

  private static Criteria c(String report, String region, String district, String from,
      String to, String... types) {
    return new Criteria(report, region, district, from, to, Arrays.asList(types));
  }

  @Test
  void aCompleteRequestPasses() {
    assertThat(AuditReportRules.validate(
        c("FTA403", "*", "*", "2026-08-25", "2026-11-25", "*"), true)).isEmpty();
    assertThat(AuditReportRules.validate(
        c("fta405", "1", "2", "", "", "A01", "", "B07"), true)).isEmpty();
  }

  @Test
  void everyErrorIsReportedInTheFormsOrder() {
    assertThat(AuditReportRules.validate(
        c("", "", " ", "2026-13-01", "nope", "A01", "a01"), true))
        .containsExactly(
            AuditReportRules.REGION_REQUIRED,
            AuditReportRules.DISTRICT_REQUIRED,
            AuditReportRules.FROM_INVALID,
            AuditReportRules.TO_INVALID,
            AuditReportRules.DUPLICATE_FILE_TYPES,
            AuditReportRules.REPORT_REQUIRED);
  }

  @Test
  void fromMustNotBeAfterTo() {
    assertThat(AuditReportRules.validate(
        c("FTA403", "*", "*", "2026-11-26", "2026-11-25"), true))
        .containsExactly(AuditReportRules.FROM_AFTER_TO);
    assertThat(AuditReportRules.validate(
        c("FTA403", "*", "*", "2026-11-25", "2026-11-25"), true)).isEmpty();
  }

  @Test
  void theDistrictMustBeInTheRegionOnlyWhenBothAreChosen() {
    assertThat(AuditReportRules.validate(c("FTA403", "1", "2", "", ""), false))
        .containsExactly(AuditReportRules.NOT_IN_REGION);
    assertThat(AuditReportRules.validate(c("FTA403", "*", "2", "", ""), false)).isEmpty();
    assertThat(AuditReportRules.validate(c("FTA403", "1", "*", "", ""), false)).isEmpty();
  }

  @Test
  void blankFileTypesAreSqueezedOut() {
    List<String> t = AuditReportRules.compactFileTypes(Arrays.asList("", "b07", null, "A01"));
    assertThat(t).containsExactly("B07", "A01", "", "", "");
    assertThat(AuditReportRules.allFileTypes(t)).isFalse();
    assertThat(AuditReportRules.allFileTypes(AuditReportRules.compactFileTypes(null))).isTrue();
    assertThat(AuditReportRules.allFileTypes(AuditReportRules.compactFileTypes(List.of("*"))))
        .isTrue();
  }

  @Test
  void scopesFollowTheProceduresCursors() {
    assertThat(Scope.of("*", "*")).isEqualTo(Scope.ALL);
    assertThat(Scope.of("1", "*")).isEqualTo(Scope.REGION);
    assertThat(Scope.of("*", "2")).isEqualTo(Scope.DISTRICT_ANY_REGION);
    assertThat(Scope.of("1", "2")).isEqualTo(Scope.DISTRICT);
  }

  @Test
  void dateRangesMatchEachProcedure() {
    assertThat(AuditReportRules.from("")).isEqualTo(LocalDateTime.of(1, 1, 1, 0, 0));
    assertThat(AuditReportRules.from("2026-08-25")).isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0));
    assertThat(AuditReportRules.to(Report.FTA403, null))
        .isEqualTo(LocalDateTime.of(9999, 12, 31, 0, 0));
    // FTAR403P takes the whole To day; FTAR405P stops at its midnight.
    assertThat(AuditReportRules.to(Report.FTA403, "2026-11-25"))
        .isEqualTo(LocalDateTime.of(2026, 11, 25, 23, 59, 59));
    assertThat(AuditReportRules.to(Report.FTA405, "2026-11-25"))
        .isEqualTo(LocalDateTime.of(2026, 11, 25, 0, 0));
  }

  @Test
  void fta405sHeaderMessage() {
    assertThat(AuditReportRules.outMessage(Scope.ALL, null, null))
        .isEqualTo("All Regions and Districts");
    assertThat(AuditReportRules.outMessage(Scope.REGION, "Cariboo Region", null))
        .isEqualTo("Cariboo Region Only ");
    assertThat(AuditReportRules.outMessage(Scope.DISTRICT, "R", "Cariboo-Chilcotin"))
        .isEqualTo("Cariboo-Chilcotin Only ");
  }

  @Test
  void onlyFta405AddsRegionOfficesForOneDistrictInAnyRegion() {
    assertThat(AuditReportService.orgFilter(Report.FTA405, Scope.DISTRICT_ANY_REGION))
        .contains("rollup_dist_no = :districtNo").contains("'R'");
    assertThat(AuditReportService.orgFilter(Report.FTA403, Scope.DISTRICT_ANY_REGION))
        .contains("rollup_dist_no = :districtNo").doesNotContain("'R'");
    assertThat(AuditReportService.orgFilter(Report.FTA403, Scope.DISTRICT))
        .isEqualTo("fal.org_unit_no = :districtNo");
  }
}
