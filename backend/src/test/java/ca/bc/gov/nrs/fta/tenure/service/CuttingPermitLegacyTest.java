package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Legacy error strings come back as message keys; the user sees legacy's text for them. */
@DisplayName("Unit Test | CuttingPermitLegacy")
class CuttingPermitLegacyTest {

  @Test
  void translatesKnownKeys() {
    assertThat(CuttingPermitLegacy.readable("fta.cp.letterIO.invalid;"))
        .isEqualTo("CP ID cannot contain I or O.");
    assertThat(CuttingPermitLegacy.readable("fta.cp.exists;fta.cp.one.letter.only;"))
        .isEqualTo("CP exists already. CP ID can only be 1 letter.");
  }

  @Test
  void keepsCustomMessageTextAndDropsTheWarningPrefix() {
    assertThat(CuttingPermitLegacy.readable(
            "fta.web.xml.database.timber_mark_generation;"
                + "fta.web.error.user.custom.msg:ORA-01403- no data found;"))
        .isEqualTo("Error occurred generating timber mark. ORA-01403- no data found");
    assertThat(CuttingPermitLegacy.readable("fta.web.error.user.custom.msg:~W,Check this;"))
        .isEqualTo("Check this");
  }

  @Test
  void passesUnknownKeysThrough() {
    assertThat(CuttingPermitLegacy.readable("fta.some.new.key;")).isEqualTo("fta.some.new.key");
  }
}
