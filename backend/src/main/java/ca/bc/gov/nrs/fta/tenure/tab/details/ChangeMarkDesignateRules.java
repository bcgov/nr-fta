package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * FTA101 Change Mark Designate's rules outside its package: when FTA100 offers the button, what
 * the Source File field sends, and how {@code FTA_101_CHANGE_MARK_DESIG}'s messages read.
 *
 * <p>The package itself decides the rest: GET answers "Invalid file ID" for a source (or target)
 * without a {@code TIMBER_TENURE} row; SAVE copies the source's designate only when the two
 * files share region and file type and one of the source's licensees (client type A) is a
 * client of the target, and does nothing when the source is the target itself.
 */
final class ChangeMarkDesignateRules {

  private ChangeMarkDesignateRules() {}

  /** The file types FTA100 shows Change Mark Designate for (ptweb25538). */
  static final Set<String> FILE_TYPES =
      Set.of("A01", "A02", "A03", "A18", "A25", "A26", "A41", "U2");

  /**
   * FTA100's Change Mark Designate: shown for its file types and enabled when Save is
   * ({@code p_disable_save_ind} not Y). Legacy also required a Senior Admin (or a BCTS org
   * user), which the endpoint's role rule covers.
   */
  static boolean changesMarkDesignate(String fileType, boolean saveDisabled) {
    return fileType != null && FILE_TYPES.contains(fileType) && !saveDisabled;
  }

  /**
   * The Source File as the package reads it: trimmed and upper-cased; blank means the file
   * itself, as GET makes it ({@code p_forest_file_id_src := p_forest_file_id}).
   */
  static String sourceFileId(String raw, String forestFileId) {
    String source = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    return source.isEmpty() ? forestFileId : source;
  }

  /**
   * SAVE's stale-revision key — {@code results.} rather than the {@code fta.} the other
   * packages use, so it has no text of its own.
   */
  static final String MODIFIED_KEY = "results.web.usr.database.record.modified";

  static final String MODIFIED_TEXT = "This tenure was changed by someone else since you opened"
      + " it. Reload it and try again.";

  /** The outcome of a package call: its error texts, and whether the lock failed. */
  record Outcome(List<String> errors, boolean modified) {
    boolean failed() {
      return modified || !errors.isEmpty();
    }
  }

  /** Reads {@code p_error_message}: the stale-revision key, or legacy's texts for the rest. */
  static Outcome outcome(String message) {
    String m = message == null ? "" : message;
    boolean modified = m.contains(MODIFIED_KEY);
    String rest = m.replace(MODIFIED_KEY + ";", "").replace(MODIFIED_KEY, "");
    TenureDetailsMessages.Parsed parsed = TenureDetailsMessages.parse(rest);
    return new Outcome(parsed.errors(), modified || parsed.modified());
  }
}
