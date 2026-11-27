package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeClientsRules (FTA920R)")
class RangeClientsRulesTest {

  @Test
  void getNeedsTheClientNumberAndLocation() {
    assertThat(RangeClientsRules.getErrors("00012345", "00")).isEmpty();
    assertThat(RangeClientsRules.getErrors(" ", null)).containsExactly(
        "Client Number is mandatory.", "Client Location Code is mandatory.");
  }

  @Test
  void saveNeedsTheLicenseeStartDate() {
    assertThat(RangeClientsRules.saveErrors("00012345", "00", "", null))
        .containsExactly("Licensee Start Date is mandatory.");
  }

  @Test
  void saveChecksTheDatesFormat() {
    assertThat(RangeClientsRules.saveErrors("00012345", "00", "2026-02-30", "26/01/01"))
        .containsExactly(
            "Licensee Start Date must be a valid date in the format YYYY-MM-DD.",
            "Previous Licensee End Date must be a valid date in the format YYYY-MM-DD.");
  }

  @Test
  void previousEndDateIsOptionalAndNotComparedWithTheStart() {
    assertThat(RangeClientsRules.saveErrors("00012345", "00", "2026-01-01", null)).isEmpty();
    // Legacy's start/end comparison was commented out.
    assertThat(RangeClientsRules.saveErrors("00012345", "00", "2026-01-01", "2026-06-01"))
        .isEmpty();
  }

  @Test
  void saveAlsoNeedsTheClientAfterTheDates() {
    assertThat(RangeClientsRules.saveErrors(null, null, null, null)).containsExactly(
        "Licensee Start Date is mandatory.", "Client Number is mandatory.",
        "Client Location Code is mandatory.");
  }

  @Test
  void datesGoToThePackageAsYyyyMmDd() {
    assertThat(RangeClientsRules.packageDate(" 2026-03-04 ")).isEqualTo("2026-03-04");
    assertThat(RangeClientsRules.packageDate("")).isNull();
    assertThat(RangeClientsRules.packageDate(null)).isNull();
  }

  @Test
  void anApplicationInTheInboxMakesTheFileViewOnly() {
    assertThat(RangeClientsRules.canEdit(true, false)).isTrue();
    assertThat(RangeClientsRules.canEdit(true, true)).isFalse();
    assertThat(RangeClientsRules.canEdit(false, false)).isFalse();
  }

  @Test
  void theInboxWarningIsForUsersWhoCouldOtherwiseSave() {
    assertThat(RangeClientsRules.warnings(true, true))
        .containsExactly("You may not update while application is in the Inbox.");
    assertThat(RangeClientsRules.warnings(false, true)).isEmpty();
    assertThat(RangeClientsRules.warnings(true, false)).isEmpty();
  }

  @Test
  void readsTheDuplicateLicenseeMessageAsLegacyShowedIt() {
    assertThat(RangeClientsRules.errors("fta.web.error.user.custom.msg:Client already exists as"
        + " the main or secondary licensee;"))
        .containsExactly("Client already exists as the main or secondary licensee.");
  }

  @Test
  void readsTheExpiredLocationKeyThatHasNoSemicolon() {
    assertThat(RangeClientsRules.errors(
        "fta.web.error.user.custom.msg:Client already exists as the main or secondary"
            + " licensee;fta.web.usr.database.record.expiredClientLocation"))
        .containsExactly("Client already exists as the main or secondary licensee.",
            "That client location is expired. Please check your client and location.");
  }

  @Test
  void readsTheSystemMessagesWithTheirArguments() {
    assertThat(RangeClientsRules.errors("fta.web.usr.database.record.invalid:Fta920R_Asso_Clnts,"
        + "GET_RELATED_CLIENT,V_RELATED_CLIENT_PUBLIC;"))
        .containsExactly("No Records Found. System Information: Table V_RELATED_CLIENT_PUBLIC"
            + " Package/Procedure Fta920R_Asso_Clnts, GET_RELATED_CLIENT.");
    assertThat(RangeClientsRules.errors("some.unknown.key:a,b;")).containsExactly(
        "some.unknown.key: a,b");
  }

  @Test
  void noMessageIsNoError() {
    assertThat(RangeClientsRules.errors(null)).isEmpty();
    assertThat(RangeClientsRules.errors("  ")).isEmpty();
    assertThat(RangeClientsRules.errors("fta.web.error.user.custom.msg:~W,note;")).isEmpty();
  }
}
