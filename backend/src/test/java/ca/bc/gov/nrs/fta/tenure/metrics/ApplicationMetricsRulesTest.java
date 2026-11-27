package ca.bc.gov.nrs.fta.tenure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Column;
import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Criteria;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FTA008's validators, procedure inputs, sheet layout, file name and package messages. */
@DisplayName("Unit Test | FTA008 application metrics rules")
class ApplicationMetricsRulesTest {

  private static Criteria none() {
    return new Criteria(null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void nothingIsMandatory() {
    assertThat(ApplicationMetricsRules.validate(none())).isEmpty();
    assertThat(ApplicationMetricsRules.validate(new Criteria(" ", "", " ", "", "", "", "", "",
        "", "", ""))).isEmpty();
  }

  @Test
  void eachFromMustNotFollowItsTo() {
    Criteria c = new Criteria("1835", "2025-02-01", "2025-01-31", "2025-03-02", "2025-03-01",
        "2025-01-01", "2025-01-01", "2025-05-02", "2025-05-01", "2025-06-02", "2025-06-01");
    assertThat(ApplicationMetricsRules.validate(c)).containsExactly(
        "Submission Date From must be less than or equal to Submission Date To.",
        "Email Rec'd Date From must be less than or equal to Email Rec'd Date To.",
        "Decision Date From must be less than or equal to Decision Date To.",
        "Issuance Date From must be less than or equal to Issuance Date To.");
  }

  @Test
  void datesMustBeIsoAndRealInTheFormsOrderAndAreNotComparedWhenUnreadable() {
    Criteria c = new Criteria(null, "2025-02-30", "2025-13-01", null, "25-01-01", null, null,
        "2025-01-01') OR (1=1", null, null, "2025/06/01");
    assertThat(ApplicationMetricsRules.validate(c)).containsExactly(
        "Submission Date To must be a valid date in the format YYYY-MM-DD.",
        "Submission Date From must be a valid date in the format YYYY-MM-DD.",
        "Email Rec'd Date To must be a valid date in the format YYYY-MM-DD.",
        "Decision Date From must be a valid date in the format YYYY-MM-DD.",
        "Issuance Date To must be a valid date in the format YYYY-MM-DD.");
  }

  @Test
  void comparisonsComeBeforeFormatsAsTheFormAddedThem() {
    Criteria c = new Criteria(null, null, "bad", null, null, "2025-02-01", "2025-01-01", null,
        null, null, null);
    assertThat(ApplicationMetricsRules.validate(c)).containsExactly(
        "Acceptance Date From must be less than or equal to Acceptance Date To.",
        "Submission Date To must be a valid date in the format YYYY-MM-DD.");
  }

  @Test
  void districtMustBeAnOrgUnitNumberAsThePackageSplicesIt() {
    assertThat(ApplicationMetricsRules.validate(new Criteria("1835 OR 1=1", null, null, null,
        null, null, null, null, null, null, null)))
        .containsExactly(ApplicationMetricsRules.DISTRICT_INVALID);
    assertThat(ApplicationMetricsRules.validate(new Criteria("'1835'", null, null, null, null,
        null, null, null, null, null, null)))
        .containsExactly(ApplicationMetricsRules.DISTRICT_INVALID);
    assertThat(ApplicationMetricsRules.validate(new Criteria(" 1835 ", null, null, null, null,
        null, null, null, null, null, null))).isEmpty();
  }

  @Test
  void procedureArgumentsAreInMainlineOrderTrimmedWithBlanksAsNull() {
    Criteria c = new Criteria(" 1835 ", "2025-01-01", "2025-01-31", "2025-02-01", "2025-02-28",
        "2025-03-01", "2025-03-31", " ", "", "2025-06-01", null);
    assertThat(ApplicationMetricsRules.procedureArguments(c)).containsExactly(
        "1835", "2025-01-01", "2025-01-31", "2025-02-01", "2025-02-28", "2025-03-01",
        "2025-03-31", null, null, "2025-06-01", null);
    assertThat(ApplicationMetricsRules.procedureArguments(none())).hasSize(11).containsOnlyNulls();
  }

  @Test
  void metricsSheetHasLegacysNineteenHeadingsInOrder() {
    assertThat(ApplicationMetricsRules.METRICS_COLUMNS.stream().map(Column::heading))
        .containsExactly(
        "District", "Licensee", "Tenure ID", "Cutting Permit ID", "Tenure Type",
        "Application Purpose", "Submission Date", "Email Recd Date", "Email Acceptance Date",
        "FN Consult Start", "FN Consult End", "Decision Date", "Issuance Date", "On Hold",
        "Submission State", "Total Days Processing", "Total Days on Hold", "Net Num Days",
        "Wildfire CP");
    assertThat(ApplicationMetricsRules.METRICS_COLUMNS.get(8).name())
        .isEqualTo("EMAIL_ACCEPTANCE_DATE");
  }

  @Test
  void holdsSheetHasLegacysSevenHeadingsInOrder() {
    assertThat(ApplicationMetricsRules.HOLDS_COLUMNS.stream().map(Column::heading))
        .containsExactly(
        "Tenure ID", "Cutting Permit ID", "Hold Start Date", "Hold End Date", "Hold Reason",
        "Hold Notes", "# Days on Hold");
    assertThat(ApplicationMetricsRules.HOLDS_COLUMNS.get(6).name()).isEqualTo("NUM_DAYS_ON_HOLD");
  }

  @Test
  void filenameIsTheStampThenMetricsOutput() {
    assertThat(ApplicationMetricsRules.filename(LocalDateTime.of(2026, 3, 5, 9, 7, 3)))
        .isEqualTo("2026-03-05_090703_MetricsOutput.xlsx");
  }

  @Test
  void packageMessagesReadAsApplicationResourcesWordsThem() {
    assertThat(ApplicationMetricsRules.message(null)).isEmpty();
    assertThat(ApplicationMetricsRules.message(" ")).isEmpty();
    assertThat(ApplicationMetricsRules.message(
        "sil.web.usr.database.unexpected:FTA_008_METRICS_EXTRACT,GET_METRICS,-942,"
            + "ORA-00942: table or view does not exist, really;"))
        .isEqualTo("An unexpected error has occurred in package/procedure"
            + " FTA_008_METRICS_EXTRACT, GET_METRICS - -942, ORA-00942: table or view does not"
            + " exist, really.");
    assertThat(ApplicationMetricsRules.message(
        "sil.web.usr.database.invalid.function:FTA_008_METRICS_EXTRACT,MAINLINE,X;"))
        .isEqualTo("Unknown database action - FTA_008_METRICS_EXTRACT.");
    assertThat(ApplicationMetricsRules.message("something else;")).isEqualTo("something else");
  }

  @Test
  void criteriaListMatchesTheProcedureArity() {
    List<String> args = ApplicationMetricsRules.procedureArguments(none());
    assertThat(args).hasSize(ApplicationMetricsPackage.CRITERIA);
  }
}
