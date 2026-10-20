package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When a save writes the marking codes to HAULING_AUTHORITY: whenever they changed, at any
 * status the rules open them at. Only HI used to write them, so an HN mark's change was lost.
 */
@DisplayName("Unit Test | MarkUpdateService marking codes")
class MarkUpdateServiceMarkingTest {

  @Test
  void unchangedCodesAreNotRewritten() {
    assertThat(MarkUpdateService.markingChanged("HAM", "PAI", "HAM", "PAI")).isFalse();
  }

  @Test
  void aChangedRequirementOrInstrumentIsWritten() {
    assertThat(MarkUpdateService.markingChanged("HAM", "PAI", "NON", "PAI")).isTrue();
    assertThat(MarkUpdateService.markingChanged("HAM", "PAI", "HAM", "TAG")).isTrue();
    assertThat(MarkUpdateService.markingChanged(null, null, "HAM", "PAI")).isTrue();
  }
}
