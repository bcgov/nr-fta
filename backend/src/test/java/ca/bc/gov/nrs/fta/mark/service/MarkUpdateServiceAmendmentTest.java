package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cancelling an issued mark (HI to HX) approves its pending (PI) amendment to HN rather than
 * dropping it, as legacy's cancel did — unless the save sets the amendment's status itself.
 */
@DisplayName("Unit Test | MarkUpdateService amendment on cancel")
class MarkUpdateServiceAmendmentTest {

  @Test
  void cancellingApprovesAPendingAmendment() {
    assertThat(MarkUpdateService.autoApprovesAmendment("HI", "HX", "PI", false)).isTrue();
  }

  @Test
  void anExplicitAmendmentStatusWins() {
    assertThat(MarkUpdateService.autoApprovesAmendment("HI", "HX", "PI", true)).isFalse();
  }

  @Test
  void onlyAPendingAmendmentIsApproved() {
    assertThat(MarkUpdateService.autoApprovesAmendment("HI", "HX", "HN", false)).isFalse();
    assertThat(MarkUpdateService.autoApprovesAmendment("HI", "HX", null, false)).isFalse();
  }

  @Test
  void onlyCancellingAnIssuedMarkApproves() {
    assertThat(MarkUpdateService.autoApprovesAmendment("HI", "HI", "PI", false)).isFalse();
    assertThat(MarkUpdateService.autoApprovesAmendment("HX", "HX", "PI", false)).isFalse();
  }
}
