package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA980 gating and field checks: the page enables its buttons by these rules and
 * the endpoints enforce them, so a regression here lets a write through that legacy refused.
 */
@DisplayName("Unit Test | TlBlockRules and TlBlockFieldChecks")
class TlBlockRulesTest {

  @Nested
  @DisplayName("rules")
  class Rules {

    @Test
    void onlyTimberLicencesApply() {
      TlBlockRules r = TlBlockRules.of("A01", "HI");
      assertThat(r.timberLicence()).isFalse();
      assertThat(r.edit()).isFalse();
      assertThat(r.retire()).isFalse();
      assertThat(r.editReason()).contains("A06").contains("A01");
      assertThat(r.retireReason()).isEqualTo(r.editReason());
    }

    @Test
    void timberLicenceEditsAtAnyStatus() {
      assertThat(TlBlockRules.of("A06", "PE").edit()).isTrue();
      assertThat(TlBlockRules.of("A06", null).edit()).isTrue();
      assertThat(TlBlockRules.of("A06", "HI").editReason()).isNull();
    }

    @Test
    void retireOnlyOnceIssued() {
      assertThat(TlBlockRules.of("A06", "HI").retire()).isTrue();
      assertThat(TlBlockRules.of("A06", "HX").retire()).isTrue();
      TlBlockRules pending = TlBlockRules.of("A06", "PE");
      assertThat(pending.retire()).isFalse();
      assertThat(pending.retireReason()).contains("issued");
      assertThat(TlBlockRules.of("A06", null).retire()).isFalse();
    }
  }

  @Nested
  @DisplayName("field checks")
  class Fields {

    @Test
    void blockIdIsUpperCasedAndAlphaNumeric() {
      assertThat(TlBlockFieldChecks.normalizeBlockId("  a1 ")).isEqualTo("A1");
      assertThat(TlBlockFieldChecks.normalizeBlockId(" ")).isNull();
      assertThat(TlBlockFieldChecks.blockIdProblems(null)).containsExactly("Block is mandatory.");
      assertThat(TlBlockFieldChecks.blockIdProblems("A-1"))
          .containsExactly("Block must be alpha numeric [A-Z, 0-9]");
      assertThat(TlBlockFieldChecks.blockIdProblems("ABCDEFGHIJK")).hasSize(1);
      assertThat(TlBlockFieldChecks.blockIdProblems("ABCDEFGHIJ")).isEmpty();
    }

    @Test
    void grossIsRequiredPositiveOneDecimal() {
      assertThat(TlBlockFieldChecks.areaProblems(null, null)).containsExactly("Gross is mandatory.");
      assertThat(TlBlockFieldChecks.areaProblems(BigDecimal.ZERO, null)).hasSize(1);
      assertThat(TlBlockFieldChecks.areaProblems(new BigDecimal("10.25"), null))
          .containsExactly("Only one decimal-place is permitted for Gross (ha)");
      assertThat(TlBlockFieldChecks.areaProblems(new BigDecimal("10.50"), null)).isEmpty();
      assertThat(TlBlockFieldChecks.areaProblems(new BigDecimal("10000000"), null)).hasSize(1);
    }

    @Test
    void eliminatedIsOptionalAndNotAboveGross() {
      assertThat(TlBlockFieldChecks.areaProblems(BigDecimal.TEN, BigDecimal.ZERO)).isEmpty();
      assertThat(TlBlockFieldChecks.areaProblems(BigDecimal.TEN, BigDecimal.TEN)).isEmpty();
      assertThat(TlBlockFieldChecks.areaProblems(BigDecimal.TEN, new BigDecimal("10.1")))
          .containsExactly("Gross Value must be greater or equal to Eliminated.");
      assertThat(TlBlockFieldChecks.areaProblems(BigDecimal.TEN, new BigDecimal("-1")))
          .containsExactly("Eliminated must have a value between 0 and 9999999.9 inclusive.");
    }
  }
}
