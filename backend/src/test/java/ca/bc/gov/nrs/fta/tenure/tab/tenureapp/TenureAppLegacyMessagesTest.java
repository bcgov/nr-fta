package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FTA_950X_TEN_APP_ISSUE_PERMIT's message keys as legacy's text. */
@DisplayName("Unit Test | TenureAppLegacyMessages")
class TenureAppLegacyMessagesTest {

  @Test
  void statusChangeKeysReadAsLegacysText() {
    assertThat(TenureAppLegacyMessages.readable("fta.status.change.invalid:HN,HI;"))
        .isEqualTo("Status change from HN to HI is invalid.");
    assertThat(TenureAppLegacyMessages.readable(
        "fta.status.change.blockstatus:LC,file,HI;"))
        .isEqualTo("All block(s) must be in LC status before you can change file status to HI");
  }

  @Test
  void customAndUnknownKeys() {
    assertThat(TenureAppLegacyMessages.readable(
        "fta.web.error.user.custom.msg:Mark is not valid;")).isEqualTo("Mark is not valid");
    assertThat(TenureAppLegacyMessages.readable("some.key:x;")).isEqualTo("some.key:x");
    assertThat(TenureAppLegacyMessages.readable("")).isNull();
  }
}
