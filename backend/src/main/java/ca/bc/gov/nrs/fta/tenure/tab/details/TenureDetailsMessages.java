package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the message strings FTA_100_TENURE (and the PL/SQL it calls) returns into
 * legacy's text. The package appends {@code key;} or {@code key:arg,arg;} entries to
 * {@code p_error_message}; {@code fta.web.error.user.custom.msg:} carries its own text, which
 * is a warning when it starts {@code ~W,} (legacy's {@code parseToErrorsWarnings}). Warnings
 * do not stop a save; anything else does.
 */
final class TenureDetailsMessages {

  private TenureDetailsMessages() {}

  /** The package's messages split into errors (the save failed) and warnings. */
  record Parsed(List<String> errors, List<String> warnings) {
    boolean failed() {
      return !errors.isEmpty();
    }

    /** Whether the failure is a revision-count mismatch (another user saved first). */
    boolean modified() {
      return errors.stream().anyMatch(e -> e.startsWith(MODIFIED_PREFIX));
    }
  }

  private static final String CUSTOM = "fta.web.error.user.custom.msg";

  private static final String MODIFIED_PREFIX =
      "The record you are attempting to update has been modified by another user.";

  /** ApplicationResources.properties text for the keys these procedures return. */
  private static final Map<String, String> TEXT = Map.ofEntries(
      Map.entry("sil.error.usr.required", "{0} is mandatory."),
      Map.entry("sil.error.usr.isrequired", "{0} is required."),
      Map.entry("sil.error.usr.missing", "{0} is missing."),
      Map.entry("sil.error.usr.invalid.value", "Invalid {0}."),
      Map.entry("fta.web.error.user.status.pa",
          "Only Road Permits Special Use Permits and License to Cut(Waiting for spatial)"
              + "can be changed to PA status."),
      Map.entry("fta.status.change.invalid", "Status change from {0} to {1} is invalid."),
      Map.entry("fta.status.change.noauthority",
          "You do not have authority to change status from {0} to {1}."),
      Map.entry("fta.status.change.invalidwithrespect",
          "Status change is invalid with respect to related statuses for this tenure"),
      Map.entry("fta.status.change.invalidwithrespect.block",
          "Cannot change {0} status to {1} -- all blocks must be in {2} status"),
      Map.entry("fta.status.change.invalidwithrespect.mark",
          "Cannot change {0} status to {1} -- all marks must be in {2} status"),
      Map.entry("fta.status.change.blockstatus",
          "All block(s) must be in {0} status before you can change {1} status to {2}"),
      Map.entry("fta.status.change.adminareacashsale",
          "Insufficient Sale Info. Enter Admin Area Cash Sale Volume & Dollars"),
      Map.entry("fta.status.change.majortsl",
          "SB Category code and Planned Sale Date must be entered on Sale Info"),
      Map.entry("fta.status.change.aac", "Insufficient AAC information to proceed"),
      Map.entry("fta.type.change.invalid", "Type change from {0} to {1} is invalid."),
      Map.entry("fta.web.usr.database.record.modified",
          MODIFIED_PREFIX + " Reload the tenure and try again. System Information: Package {0},"
              + " Action {1}, Table {2}."),
      Map.entry("fta.web.usr.database.record.invalid",
          "No Records Found. System Information: Table {2} Package/Procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.toomany",
          "Multiple records were found when expecting only one. System Information"
              + " Package/Procedure {0}, {1}. Table {2}."),
      Map.entry("fta.web.usr.database.record.duplicate",
          "A record was found with an identical key value. System Information: table {2},"
              + " package/procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.retired", "The file has already been retired."),
      Map.entry("fta.web.usr.database.record.DistrictOverrideCommentRequired",
          "District override comment required."),
      Map.entry("fta.web.usr.database.unexpected",
          "An unexpected error has occurred in package/procedure {0}, {1} - {2}, {3} Please"
              + " contact System Support."),
      Map.entry("fta.web.usr.database.proc.unexpected",
          "An unexpected error has occurred in procedure {0},{1}. Please contact System"
              + " Support."),
      Map.entry("fta.web.usr.database.invalid.function",
          "An unknown database action was requested. System Information - {0} - {1} - {2}."));

  /** Keys that are informational rather than failures. */
  private static final List<String> WARNING_KEYS = List.of("fta.web.warning.FtaAuditLogged");

  static Parsed parse(String message) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (message == null || message.isBlank()) {
      return new Parsed(errors, warnings);
    }
    for (String part : message.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1);
      if (CUSTOM.equals(key)) {
        String text = args.trim();
        if (text.startsWith("~W,")) {
          warnings.add(text.substring(3).trim());
        } else {
          errors.add(text);
        }
      } else if (WARNING_KEYS.contains(key)) {
        // The district-override audit's "logged" note: nothing to tell the user.
        continue;
      } else if (args.startsWith("~W,")) {
        warnings.add(format(key, args.substring(3)));
      } else {
        errors.add(format(key, args));
      }
    }
    return new Parsed(errors, warnings);
  }

  private static String format(String key, String args) {
    String pattern = TEXT.get(key);
    if (pattern == null) {
      return args.isEmpty() ? key : key + ": " + args;
    }
    Object[] values = args.isEmpty() ? new Object[0] : args.split(",", -1);
    // MessageFormat treats single quotes as escapes; the texts have none, but be safe.
    return new MessageFormat(pattern.replace("'", "''")).format(values);
  }
}
