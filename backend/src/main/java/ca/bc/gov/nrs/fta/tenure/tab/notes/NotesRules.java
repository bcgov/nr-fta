package ca.bc.gov.nrs.fta.tenure.tab.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * The legacy FTA970 (Forest Notes) rules, computed once and shared by the GET (to enable the
 * Add button and say why not) and the POST (to enforce them).
 *
 * <p>Ported from {@code Fta970ForestNotesForm}'s "Save" validators:
 * <ul>
 *   <li>{@code noSaveWhenStatusPE} — "No updates can be performed on this file when the status
 *       is PE." (legacy checks the first two characters of the header's status);</li>
 *   <li>{@code noteRequired} (RequiredFieldValidator, "Note") and {@code note4000}
 *       (StringLengthValidator 4000, {@code fta.web.error.string.length}).</li>
 * </ul>
 * Legacy also disables Save for a non-HQ user on a private-mark file; every FTA_ADMIN gets
 * HQ's rules here, so that never applies.
 *
 * @param canAdd         whether a note may be added to the file
 * @param blockedReason  why not, when {@code canAdd} is false
 */
public record NotesRules(boolean canAdd, String blockedReason) {

  /** {@code Fta970ForestNotesForm.note4000}. */
  public static final int MAX_NOTE_LENGTH = 4000;

  /** {@code fta.web.error.user.noSaveWhenStatusPE}. */
  static final String STATUS_PE =
      "No updates can be performed on this file when the status is PE.";

  /** The rules for a file with the given status code ({@code PROV_FOREST_USE.FILE_STATUS_ST}). */
  public static NotesRules forStatus(String fileStatusCode) {
    if (fileStatusCode != null && fileStatusCode.trim().toUpperCase().startsWith("PE")) {
      return new NotesRules(false, STATUS_PE);
    }
    return new NotesRules(true, null);
  }

  /**
   * The note's validation errors (empty when valid), with legacy's message texts:
   * {@code sil.error.usr.required} ("{0} is mandatory.") and
   * {@code fta.web.error.string.length} ("{0} must not exceed {1} characters.").
   */
  public static List<String> validate(String note) {
    List<String> errors = new ArrayList<>();
    String text = note == null ? "" : note.trim();
    if (text.isEmpty()) {
      errors.add("Note is mandatory.");
    } else if (text.length() > MAX_NOTE_LENGTH) {
      errors.add("Note must not exceed " + MAX_NOTE_LENGTH + " characters.");
    }
    return errors;
  }
}
