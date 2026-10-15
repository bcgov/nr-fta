package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which saves make the saving user the certificate's signer — legacy FTA_510's: UPDATE_REC
 * (a data-only save, district or Headquarters) and SAVE's PI-to-HN issue, and nothing else.
 */
@DisplayName("Unit Test | MarkUpdateService signer")
class MarkUpdateServiceSignerTest {

  @Test
  void aDataOnlySaveSigns() {
    assertThat(MarkUpdateService.signsCertificate("HI", "HI", false)).isTrue();
    assertThat(MarkUpdateService.signsCertificate("PA", "PA", false)).isTrue();
  }

  @Test
  void issuingSigns() {
    assertThat(MarkUpdateService.signsCertificate("PI", "HN", false)).isTrue();
  }

  @Test
  void reinstatingACancelledMarkDoesNot() {
    assertThat(MarkUpdateService.signsCertificate("HX", "HI", false)).isFalse();
  }

  @Test
  void otherStatusChangesDoNot() {
    assertThat(MarkUpdateService.signsCertificate("HI", "HX", false)).isFalse();
    assertThat(MarkUpdateService.signsCertificate("PI", "DV", false)).isFalse();
    assertThat(MarkUpdateService.signsCertificate("HN", "HI", false)).isFalse();
  }

  @Test
  void anAmendmentStatusChangeDoesNot() {
    assertThat(MarkUpdateService.signsCertificate("HI", "HI", true)).isFalse();
  }
}
