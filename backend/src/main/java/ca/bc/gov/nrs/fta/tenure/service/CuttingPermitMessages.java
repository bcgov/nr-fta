package ca.bc.gov.nrs.fta.tenure.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the {@code p_error_message} of {@code FTA_902_CP_DETAIL} into text. It is a string of
 * {@code key;} or {@code key:args;} entries; a {@code ~W,} prefix or a warning key marks an entry
 * that does not stop the save (legacy's {@code parseToErrorsWarnings}). The text is
 * ApplicationResources.properties', for the keys the package and the procedures it calls send.
 */
final class CuttingPermitMessages {

  /** The parsed message; {@code modified} when the row changed since it was read. */
  record Parsed(List<String> errors, List<String> warnings, boolean modified) {
    boolean failed() {
      return !errors.isEmpty();
    }
  }

  private static final String CUSTOM = "fta.web.error.user.custom.msg";
  private static final String MODIFIED = "fta.web.usr.database.record.modified";

  private static final Map<String, String> TEXT = Map.ofEntries(
      Map.entry("fta.status.change.aac", "Insufficient AAC information to proceed."),
      Map.entry("fta.status.change.adminareacashsale",
          "Insufficient Sale Info. Enter Admin Area Cash Sale Volume & Dollars."),
      Map.entry("fta.status.change.blockstatus",
          "All block(s) must be in {0} status before you can change {1} status to {2}."),
      Map.entry("fta.status.change.invalid", "Status change from {0} to {1} is invalid."),
      Map.entry("fta.status.change.invalidwithrespect",
          "Status change is invalid with respect to related statuses for this tenure."),
      Map.entry("fta.status.change.invalidwithrespect.block",
          "Cannot change {0} status to {1} -- all blocks must be in {2} status."),
      Map.entry("fta.status.change.invalidwithrespect.mark",
          "Cannot change {0} status to {1} -- all marks must be in {2} status."),
      Map.entry("fta.status.change.majortsl",
          "SB Category code and Planned Sale Date must be entered on Sale Info."),
      Map.entry("fta.status.change.noauthority",
          "You do not have authority to change status from {0} to {1}."),
      Map.entry("fta.web.error.invalid.fileType",
          "The File Type ({0}) associated with the queried File is invalid for this screen."),
      Map.entry("fta.web.error.invalidMarkTermLimits",
          "Tenure term cannot be more than 4 years for {0}."),
      Map.entry("fta.web.error.usr.fta902.cppExpiryBeforeStart",
          "The Postponement Expiry date must be greater than the Postponement Start Date."),
      Map.entry("fta.web.error.usr.fta902.invalidPPTermLimits",
          "Postponement term cannot be more than 2 years."),
      Map.entry("fta.web.usr.database.fsj.cphvaid.exists",
          "The Harvesting Authority Id {0} already exists as a Cutting Permit for this file."),
      Map.entry("fta.web.usr.database.record.ExternalRef",
          "The permit is referenced by external systems and cannot be deleted."),
      Map.entry("fta.web.usr.database.record.duplicate",
          "A record with an identical key already exists. Reload the page and try again."),
      Map.entry("fta.web.usr.database.record.invalid", "No records found."),
      Map.entry(MODIFIED,
          "The permit was changed by someone else. Reload the page and try again."),
      Map.entry("fta.web.usr.database.record.toomany",
          "Multiple records were found when expecting only one."),
      Map.entry("fta.web.usr.database.unexpected",
          "An unexpected error has occurred in package/procedure {0}, {1}. Please contact System"
              + " Support."),
      Map.entry("sil.error.usr.invalid.value", "Invalid {0}."),
      Map.entry("sil.error.usr.isrequired", "{0} is required."),
      Map.entry("sil.error.usr.missing", "{0} is missing."),
      Map.entry("sil.error.usr.required", "{0} is mandatory."));

  private CuttingPermitMessages() {}

  static Parsed parse(String raw) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    boolean modified = false;
    if (raw == null || raw.isBlank()) {
      return new Parsed(errors, warnings, false);
    }
    for (String part : raw.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1).trim();
      modified |= MODIFIED.equals(key);
      if (CUSTOM.equals(key)) {
        if (args.startsWith("~W,")) {
          warnings.add(args.substring(3).trim());
        } else {
          errors.add(args);
        }
      } else if (key.startsWith("fta.web.warning.")) {
        warnings.add(TEXT.getOrDefault(key, key));
      } else {
        errors.add(format(TEXT.getOrDefault(key, p), args));
      }
    }
    return new Parsed(errors, warnings, modified);
  }

  private static String format(String template, String args) {
    String text = template;
    String[] values = args.isEmpty() ? new String[0] : args.split(",");
    for (int i = 0; i < values.length; i++) {
      text = text.replace("{" + i + "}", values[i].trim());
    }
    return text;
  }
}
