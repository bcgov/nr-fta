package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RentsFeesRules (FTA670)")
class RentsFeesRulesTest {

  @Test
  void aStoredSpaceIsTheBlankOption() {
    assertThat(RentsFeesRules.normalize(" ")).isEmpty();
    assertThat(RentsFeesRules.normalize(null)).isEmpty();
    assertThat(RentsFeesRules.normalize("R")).isEqualTo("R");
  }

  @Test
  void onlyAChangedOptIsSaved() {
    assertThat(RentsFeesRules.changed("R", "R")).isFalse();
    assertThat(RentsFeesRules.changed(" ", "")).isFalse();
    assertThat(RentsFeesRules.changed(null, "")).isFalse();
    assertThat(RentsFeesRules.changed("", "R")).isTrue();
    assertThat(RentsFeesRules.changed("R", "D")).isTrue();
    assertThat(RentsFeesRules.changed("R", "")).isTrue();
  }

  @Test
  void theOptOffersBlankRAndDOnly() {
    assertThat(RentsFeesRules.invalidIndicator(1, "")).isNull();
    assertThat(RentsFeesRules.invalidIndicator(1, null)).isNull();
    assertThat(RentsFeesRules.invalidIndicator(1, "R")).isNull();
    assertThat(RentsFeesRules.invalidIndicator(1, "D")).isNull();
    assertThat(RentsFeesRules.invalidIndicator(3, "X")).isEqualTo("Row 3: Invalid Opt");
  }

  @Test
  void rollupIsNeverSentForADistrictScopedAdministrator() {
    assertThat(RentsFeesRules.rollup(true, true)).isEqualTo("Y");
    assertThat(RentsFeesRules.rollup(true, false)).isEqualTo("N");
    assertThat(RentsFeesRules.rollup(true, null)).isEqualTo("N");
    assertThat(RentsFeesRules.rollup(false, true)).isEqualTo("N");
  }

  @Test
  void theBillingPeriodStartsAtTheEarliestAnniversaryWhenThatIsEarlier() {
    assertThat(RentsFeesRules.billFrom("2026-12-01", List.of("2026-12-15", "2026-11-20")))
        .isEqualTo("2026-11-20");
    assertThat(RentsFeesRules.billFrom("2026-12-01", List.of("2026-12-15"))).isEqualTo("2026-12-01");
    assertThat(RentsFeesRules.billFrom("2026-12-01", List.of())).isEqualTo("2026-12-01");
  }

  @Test
  void getsAuthorityRefusalReadsAsLegacy() {
    String msg = "fta.web.error.authority_exception_view:FTA_670_RentFee,GET,;";
    assertThat(RentsFeesRules.getMessages(msg)).containsExactly(
        "The records you are attempting to view belong to a District or Region outside your"
            + " Jurisdiction.");
    assertThat(RentsFeesRules.isAuthorityError(msg)).isTrue();
    assertThat(RentsFeesRules.getMessages("")).isEmpty();
    assertThat(RentsFeesRules.getMessages(null)).isEmpty();
  }

  @Test
  void getsUnexpectedErrorKeepsSqlerrmCommas() {
    assertThat(RentsFeesRules.getMessages(
        "fta.web.usr.database.unexpected:FTA_670_RentFee,GET,-942,ORA-00942: a, b;"))
        .containsExactly("An unexpected error has occurred in package/procedure FTA_670_RentFee,"
            + " GET - -942, ORA-00942: a, b Please contact System Support.");
  }

  @Test
  void saveMessagesCarryTheRowNumberAsLegacysMultiRowTexts() {
    assertThat(RentsFeesRules.saveMessages(
        "fta.web.usr.database.record.modified:FTA_670_RentFee,CHANGE,BILLABLE_TENURE;", 4))
        .containsExactly("Row 4 has been previously modified. Re-select data.");
    assertThat(RentsFeesRules.saveMessages(
        "fta.web.usr.database.record.modified:FTA_670_RentFee,REMOVE,BILLABLE_TENURE;", 2))
        .containsExactly("Row 2 has been previously modified. Re-select data.");
    assertThat(RentsFeesRules.saveMessages(
        "fta.web.usr.database.unexpected:FTA_670_RentFee,CHANGE,-1,ORA-00001: x;", 1))
        .containsExactly("Row 1: An unexpected error has occurred in package/procedure"
            + " FTA_670_RentFee - CHANGE, -1 - ORA-00001: x Please contact System Support.");
    assertThat(RentsFeesRules.saveMessages(
        "fta.web.usr.database.record.duplicate:FTA_670_RentFee,SAVE,BILLABLE_TENURE,-1;", 5))
        .containsExactly("Row 5: A duplicate key has been detected on table BILLABLE_TENURE in"
            + " package/procedure FTA_670_RentFee - SAVE.");
    assertThat(RentsFeesRules.saveMessages("", 1)).isEmpty();
  }

  @Test
  void theLockFailureIsRecognised() {
    assertThat(RentsFeesRules.isModified(RentsFeesRules.modified(1))).isTrue();
    assertThat(RentsFeesRules.isModified("Row 1: Invalid Opt")).isFalse();
  }
}
