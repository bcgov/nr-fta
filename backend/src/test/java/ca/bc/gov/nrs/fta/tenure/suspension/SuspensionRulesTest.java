package ca.bc.gov.nrs.fta.tenure.suspension;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | SuspensionRules")
class SuspensionRulesTest {

  private static final LocalDate START = LocalDate.of(2026, 1, 15);
  private static final LocalDate END = LocalDate.of(2026, 6, 15);

  @Test
  void permitBlocksFollowTheFta912List() {
    assertThat(SuspensionRules.permitBlockEligible("HB", false)).isTrue();
    assertThat(SuspensionRules.permitBlockEligible("PE", false)).isTrue();
    assertThat(SuspensionRules.permitBlockEligible("HB", true)).isFalse();
    assertThat(SuspensionRules.permitBlockEligible("HS", false)).isFalse();
    assertThat(SuspensionRules.permitBlockEligible("LC", false)).isFalse();
  }

  @Test
  void aBlockIsSuspendedOnceFromHarvestableStatuses() {
    assertThat(SuspensionRules.canSuspendBlock("HB", false)).isTrue();
    assertThat(SuspensionRules.canSuspendBlock("PP", false)).isTrue();
    assertThat(SuspensionRules.canSuspendBlock("PE", false)).isFalse();
    assertThat(SuspensionRules.canSuspendBlock("HB", true)).isFalse();
  }

  @Test
  void aSuspensionIsEditedOnlyWhileSuspended() {
    assertThat(SuspensionRules.canEditSuspension("HS", true)).isTrue();
    assertThat(SuspensionRules.canEditSuspension("HB", true)).isFalse();
    assertThat(SuspensionRules.canEditSuspension("HS", false)).isFalse();
  }

  @Test
  void everyFieldIsMandatory() {
    assertThat(SuspensionRules.fieldsProblem("P13", "123", START, END)).isNull();
    assertThat(SuspensionRules.fieldsProblem(" ", "123", START, END)).contains("Partition");
    assertThat(SuspensionRules.fieldsProblem("P13", null, START, END)).contains("Order");
    assertThat(SuspensionRules.fieldsProblem("P13", "123", null, END)).contains("Start");
    assertThat(SuspensionRules.fieldsProblem("P13", "123", START, null)).contains("End");
  }

  @Test
  void theOrderNumberFitsItsColumn() {
    assertThat(SuspensionRules.fieldsProblem("P13", "1234567890", START, END)).isNull();
    assertThat(SuspensionRules.fieldsProblem("P13", "12345678901", START, END)).contains("digits");
    assertThat(SuspensionRules.fieldsProblem("P13", "12A", START, END)).contains("number");
  }

  @Test
  void endingNeedsARealPostStatus() {
    assertThat(SuspensionRules.endProblem("HB", false, "5", "5")).isNull();
    assertThat(SuspensionRules.endProblem(null, false, "5", "5")).contains("mandatory");
    assertThat(SuspensionRules.endProblem("HS", false, "5", "5")).contains("must not");
  }

  @Test
  void endingAllBlocksCannotAlsoChangeTheOrderNumber() {
    assertThat(SuspensionRules.endProblem("HB", true, "6", "5")).contains("Save");
    assertThat(SuspensionRules.endProblem("HB", true, "5", "5")).isNull();
    assertThat(SuspensionRules.endProblem("HB", false, "6", "5")).isNull();
  }

  @Test
  void monthsRemainingRunToTheLaterTerm() {
    LocalDate expiry = LocalDate.of(2026, 7, 15);
    LocalDate extended = LocalDate.of(2027, 1, 15);
    assertThat(SuspensionRules.monthsRemaining(START, expiry, null)).isEqualTo(6);
    assertThat(SuspensionRules.monthsRemaining(START, expiry, extended)).isEqualTo(12);
    assertThat(SuspensionRules.monthsRemaining(null, expiry, null)).isNull();
    assertThat(SuspensionRules.monthsRemaining(START, null, null)).isNull();
  }
}
