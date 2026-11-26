package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA980 gating: the page enables Retire and Un-Retire by these rules and the
 * endpoints enforce them, so a regression here lets a write through that legacy refused.
 */
@DisplayName("Unit Test | TlBlockRules")
class TlBlockRulesTest {

  @Test
  void onlyTimberLicencesApply() {
    TlBlockRules r = TlBlockRules.of("A01", "HI");
    assertThat(r.timberLicence()).isFalse();
    assertThat(r.retire()).isFalse();
    assertThat(r.retireReason()).contains("A06").contains("A01");
  }

  @Test
  void retireOnlyOnceIssued() {
    assertThat(TlBlockRules.of("A06", "HI").retire()).isTrue();
    assertThat(TlBlockRules.of("A06", "HX").retire()).isTrue();
    assertThat(TlBlockRules.of("A06", "HI").retireReason()).isNull();
    TlBlockRules pending = TlBlockRules.of("A06", "PE");
    assertThat(pending.retire()).isFalse();
    assertThat(pending.retireReason()).contains("issued");
    assertThat(TlBlockRules.of("A06", null).retire()).isFalse();
  }
}
