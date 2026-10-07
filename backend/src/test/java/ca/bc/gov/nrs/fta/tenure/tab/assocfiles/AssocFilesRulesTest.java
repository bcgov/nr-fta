package ca.bc.gov.nrs.fta.tenure.tab.assocfiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Legacy FTA910's gate and form checks, with their message texts. */
@DisplayName("Unit Test | AssocFilesRules")
class AssocFilesRulesTest {

  private static final LocalDate AWARD = LocalDate.of(2020, 1, 1);
  private static final LocalDate EXPIRY = LocalDate.of(2030, 1, 1);

  @Test
  void blocksAddsWhileTheFileIsPe() {
    assertThat(AssocFilesRules.forStatus("PE").canAdd()).isFalse();
    assertThat(AssocFilesRules.forStatus("PE").addReason())
        .isEqualTo("No updates can be performed on this file when the status is PE.");
    assertThat(AssocFilesRules.forStatus("HI").canAdd()).isTrue();
    assertThat(AssocFilesRules.forStatus(null).canAdd()).isTrue();
  }

  @Test
  void requiresFileAndSource() {
    assertThat(AssocFilesRules.validate("A12345", null, null, null, null, AWARD, EXPIRY))
        .containsExactly("Associated File is mandatory.", "Source is mandatory.");
  }

  @Test
  void rejectsSelfAssociation() {
    assertThat(AssocFilesRules.validate("A12345", "A12345", "F", null, null, AWARD, EXPIRY))
        .containsExactly("File may not be associated with itself.");
  }

  @Test
  void allowsATypeOnlyWithTheFtasSource() {
    assertThat(AssocFilesRules.validate("A1", "X9", "R", "AAC", null, AWARD, EXPIRY))
        .containsExactly(
            "Source must be Forest Tenure System for an Association Type to be present.");
    assertThat(AssocFilesRules.validate("A1", "A2", "F", "AAC", null, AWARD, EXPIRY)).isEmpty();
  }

  @Test
  void allowsAnEndDateOnlyForAacInsideTheTenureTerm() {
    LocalDate inside = LocalDate.of(2025, 6, 1);
    assertThat(AssocFilesRules.validate("A1", "A2", "F", "OTH", inside, AWARD, EXPIRY))
        .containsExactly(
            "Association Type must be AAC for an Association End Date to be present.");
    assertThat(AssocFilesRules.validate("A1", "A2", "F", "AAC", inside, AWARD, EXPIRY)).isEmpty();
    assertThat(AssocFilesRules.validate(
            "A1", "A2", "F", "AAC", LocalDate.of(2019, 12, 31), AWARD, EXPIRY))
        .containsExactly(AssocFilesRules.END_DATE_RANGE);
    assertThat(AssocFilesRules.validate(
            "A1", "A2", "F", "AAC", LocalDate.of(2030, 1, 2), AWARD, EXPIRY))
        .containsExactly(AssocFilesRules.END_DATE_RANGE);
    // No term on the tenure: legacy's date compare has nothing to compare against.
    assertThat(AssocFilesRules.validate("A1", "A2", "F", "AAC", inside, null, null)).isEmpty();
  }
}
