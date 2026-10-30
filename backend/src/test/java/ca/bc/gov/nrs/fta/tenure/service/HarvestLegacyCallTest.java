package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | HarvestLegacyCall")
class HarvestLegacyCallTest {

  @Test
  void customMessagesAreSentences() {
    assertThat(HarvestLegacyCall.readable(
        "fta.web.error.user.custom.msg:Suspension cannot begin before permit term begins "))
        .isEqualTo("Suspension cannot begin before permit term begins.");
  }

  @Test
  void endSuspensionsRunOnEntryKeepsOnlyTheMessage() {
    assertThat(HarvestLegacyCall.readable(
        "fta.web.error.user.custom.msg:Suspension term must fall within cutting permit term "
            + "fta.web.usr.database.unexpected:CUT_BLOCK_SUSPENSION,END_SUSPENSION,1,"
            + "User-Defined Exception;"))
        .isEqualTo("Suspension term must fall within cutting permit term.");
  }

  @Test
  void knownKeysReadAsLegacysText() {
    assertThat(HarvestLegacyCall.readable(
        "sil.error.usr.field.alreadyExists:Specified Timber Mark;"))
        .isEqualTo("Specified Timber Mark already exists.");
    assertThat(HarvestLegacyCall.readable(
        "fta.web.usr.database.record.orphanTimberMark:v_old_timber_mark;"))
        .startsWith("There are no other references");
    assertThat(HarvestLegacyCall.readable("fta.web.usr.database.record.modified:A,B,C;"))
        .contains("changed by someone else");
  }

  @Test
  void severalEntriesAreJoined() {
    assertThat(HarvestLegacyCall.readable(
        "fta.web.error.user.custom.msg:One;fta.web.error.user.custom.msg:Two;"))
        .isEqualTo("One. Two.");
  }

  @Test
  void theBlockBindsEveryValueAndAnyCursorLast() {
    assertThat(HarvestLegacyCall.block("pkg.mainline", null, 2))
        .isEqualTo("BEGIN the.pkg.mainline(?, ?); END;");
    assertThat(HarvestLegacyCall.block("pkg.mainline", "the.pkg.cur", 2))
        .isEqualTo("DECLARE v_c the.pkg.cur; BEGIN the.pkg.mainline(?, ?, v_c); END;");
  }
}
