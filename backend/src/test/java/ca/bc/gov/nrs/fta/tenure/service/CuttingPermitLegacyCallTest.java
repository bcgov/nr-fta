package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pins the call shape and message parsing a wrong character of which only Oracle would reject. */
@DisplayName("Unit Test | FTA902 legacy call")
class CuttingPermitLegacyCallTest {

  @Test
  void bindsEveryParameterInAndOut() {
    String block = CuttingPermitDetailLegacy.mainlineBlock();
    int size = CuttingPermitDetailLegacy.PARAMS.size();
    assertThat(size).isEqualTo(94);
    assertThat(block.chars().filter(c -> c == '?').count()).isEqualTo(2L * size);
  }

  @Test
  void passesTheClobAndCursorWhereMainlineDeclaresThem() {
    String block = CuttingPermitDetailLegacy.mainlineBlock();
    int mapUrl = CuttingPermitDetailLegacy.PARAMS.indexOf("p_map_url");
    assertThat(block).contains("v" + (mapUrl - 1) + ", v_legal, v" + mapUrl);
    assertThat(block).contains("v" + (CuttingPermitDetailLegacy.PARAMS.size() - 1) + ", v_cur)");
  }

  @Test
  void anEmptyMessageIsSuccess() {
    assertThat(CuttingPermitMessages.parse(null).failed()).isFalse();
    assertThat(CuttingPermitMessages.parse("  ").failed()).isFalse();
  }

  @Test
  void aWarningDoesNotFail() {
    CuttingPermitMessages.Parsed p =
        CuttingPermitMessages.parse("fta.web.error.user.custom.msg:~W,Check the expiry;");
    assertThat(p.failed()).isFalse();
    assertThat(p.warnings()).containsExactly("Check the expiry");
  }

  @Test
  void keysBecomeTheirTextWithArguments() {
    CuttingPermitMessages.Parsed p = CuttingPermitMessages.parse(
        "sil.error.usr.required:Location;fta.status.change.invalid:HI,PP;");
    assertThat(p.errors())
        .containsExactly("Location is mandatory.", "Status change from HI to PP is invalid.");
  }

  @Test
  void aModifiedRecordIsFlagged() {
    CuttingPermitMessages.Parsed p = CuttingPermitMessages.parse(
        "fta.web.usr.database.record.modified:Fta_902_Cp_Detail,save,HARVESTING_AUTHORITY;");
    assertThat(p.modified()).isTrue();
    assertThat(p.errors()).hasSize(1);
  }

  @Test
  void aPlainConditionMessageSurvives() {
    assertThat(CuttingPermitMessages.parse("Permit Issuance Condition is required").errors())
        .containsExactly("Permit Issuance Condition is required");
  }
}
