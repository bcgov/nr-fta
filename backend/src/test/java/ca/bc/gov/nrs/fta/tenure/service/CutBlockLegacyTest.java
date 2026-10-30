package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | CutBlockLegacy error strings")
class CutBlockLegacyTest {

  @Test
  void nothingReportedIsSuccess() {
    CutBlockLegacy.Outcome outcome = CutBlockLegacy.parse(null);
    assertThat(outcome.errors()).isEmpty();
    assertThat(outcome.warnings()).isEmpty();
    assertThat(outcome.modified()).isFalse();
  }

  @Test
  void aWarningIsNotAnError() {
    CutBlockLegacy.Outcome outcome = CutBlockLegacy.parse(
        "fta.web.error.user.custom.msg:~W,As Of date has been updated to todays date;");
    assertThat(outcome.errors()).isEmpty();
    assertThat(outcome.warnings()).containsExactly("As Of date has been updated to todays date.");
  }

  @Test
  void aCustomMessageIsAnError() {
    CutBlockLegacy.Outcome outcome = CutBlockLegacy.parse(
        "fta.web.error.user.custom.msg:Mark Status must be HI for block status to be HB;");
    assertThat(outcome.errors())
        .containsExactly("Mark Status must be HI for block status to be HB.");
  }

  @Test
  void statusChangeKeysTakeTheirArguments() {
    assertThat(CutBlockLegacy.parse("fta.status.change.noauthority:HB,PP;").errors())
        .containsExactly("You do not have authority to change status from HB to PP.");
  }

  @Test
  void aModifiedRecordIsFlagged() {
    assertThat(CutBlockLegacy.parse(
            "fta.web.usr.database.record.modified:FTA_231_CUTBLK_RELABEL,SAVE,Cut_Block;")
        .modified()).isTrue();
    assertThat(CutBlockLegacy.parse(
            "fta.web.error.user.custom.msg:Record CUT_BLOCK has been modified by another user."
                + " Please exit screen;")
        .modified()).isTrue();
  }

  @Test
  void errorsAndWarningsAreSeparated() {
    CutBlockLegacy.Outcome outcome = CutBlockLegacy.parse(
        "fta.web.error.user.custom.msg:~W,Review Sale Info;fta.status.change.invalid:HB,PP;");
    assertThat(outcome.errors()).hasSize(1);
    assertThat(outcome.warnings()).hasSize(1);
  }
}
