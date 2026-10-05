package ca.bc.gov.nrs.fta.tenure.tab.notes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins legacy FTA970's Save rules (Fta970ForestNotesForm). */
class NotesRulesTest {

  @Test
  void pendingFileCannotTakeNotes() {
    NotesRules rules = NotesRules.forStatus("PE");
    assertThat(rules.canAdd()).isFalse();
    assertThat(rules.blockedReason())
        .isEqualTo("No updates can be performed on this file when the status is PE.");
  }

  @Test
  void otherStatusesCanTakeNotes() {
    assertThat(NotesRules.forStatus("HI").canAdd()).isTrue();
    assertThat(NotesRules.forStatus(null).canAdd()).isTrue();
    assertThat(NotesRules.forStatus("HI").blockedReason()).isNull();
  }

  @Test
  void noteIsMandatory() {
    assertThat(NotesRules.validate(null)).containsExactly("Note is mandatory.");
    assertThat(NotesRules.validate("   ")).containsExactly("Note is mandatory.");
  }

  @Test
  void noteAtMost4000Characters() {
    assertThat(NotesRules.validate("x".repeat(4000))).isEmpty();
    assertThat(NotesRules.validate(" " + "x".repeat(4000) + " ")).isEmpty();
    assertThat(NotesRules.validate("x".repeat(4001)))
        .containsExactly("Note must not exceed 4000 characters.");
  }
}
