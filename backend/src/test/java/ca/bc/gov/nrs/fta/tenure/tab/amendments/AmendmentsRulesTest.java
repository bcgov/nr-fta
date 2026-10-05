package ca.bc.gov.nrs.fta.tenure.tab.amendments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pins FTA905's file-type gate (FTA_905_CP_AMEND / FTA_905_BLK_AMEND MAINLINE). */
class AmendmentsRulesTest {

  @ParameterizedTest
  @ValueSource(strings = {"B40", "B02", "B01", "S01", "S02", "C01"})
  void listedFileTypesAreInvalid(String type) {
    AmendmentsRules rules = AmendmentsRules.of(type, false, false);
    assertThat(rules.available()).isFalse();
    assertThat(rules.unavailableReason()).isEqualTo(
        "The File Type (" + type + ") associated with the queried File is invalid for this"
            + " screen.");
  }

  @Test
  void rangeAndRecreationTypesAreInvalid() {
    assertThat(AmendmentsRules.of("E01", true, false).available()).isFalse();
    assertThat(AmendmentsRules.of("R01", false, true).available()).isFalse();
  }

  @Test
  void timberTenuresAreValid() {
    AmendmentsRules rules = AmendmentsRules.of("A01", false, false);
    assertThat(rules.available()).isTrue();
    assertThat(rules.unavailableReason()).isNull();
  }
}
