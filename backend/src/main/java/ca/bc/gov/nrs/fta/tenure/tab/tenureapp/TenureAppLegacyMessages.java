package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.util.Map;

/**
 * Turns the {@code p_error_message} strings FTA_950X_TEN_APP_ISSUE_PERMIT returns —
 * {@code key:arg0,arg1,...;} pairs — into legacy's ApplicationResources text, with the
 * {@code {n}} placeholders filled in.
 */
final class TenureAppLegacyMessages {

  private static final String CUSTOM = "fta.web.error.user.custom.msg";

  /** ApplicationResources.properties text for the keys the package returns. */
  private static final Map<String, String> MESSAGES = Map.ofEntries(
      Map.entry("fta.web.usr.database.record.modified",
          "The record you are attempting to update has been modified by another user."
              + " Please refresh the screen. System Information: Package {0}, Action {1},"
              + " Table {2}."),
      Map.entry("fta.web.usr.database.record.invalid_sub_type",
          "Tenure Application Type has not been defined. System Information: Package {0},"
              + " Action {1}, Table {2}."),
      Map.entry("fta.web.usr.database.record.toomany",
          "Multiple records were found when expecting only one. System Information"
              + " Package/Procedure {0}, {1}. Table {2}."),
      Map.entry("fta.web.usr.database.record.duplicate",
          "A record was found with an identical key value. This indicates that the record you"
              + " are adding or updating already exists. System Information: table {2} ,"
              + " package/procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.invalid",
          "No Records Found. System Information: Table {2} Package/Procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.unexpected",
          "An unexpected error has occurred in package/procedure {0}, {1} - {2}, {3} Please"
              + " contact System Support."),
      Map.entry("fta.web.usr.database.invalid.function",
          "An unknown database action was requested. System Information - {0} - {1} - {2}."));

  private TenureAppLegacyMessages() {}

  /** The readable text of a legacy error string; null when it holds no message. */
  static String readable(String error) {
    if (error == null || error.isBlank()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (String part : error.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1);
      String text;
      if (CUSTOM.equals(key)) {
        text = args.replaceFirst("^~W,", "");
      } else if (MESSAGES.containsKey(key)) {
        text = fill(MESSAGES.get(key), args);
      } else {
        text = p;
      }
      if (!sb.isEmpty()) {
        sb.append(' ');
      }
      sb.append(text.trim());
    }
    return sb.isEmpty() ? null : sb.toString();
  }

  /** Fills {0}..{n}; the last placeholder takes the rest (SQLERRM may hold commas). */
  private static String fill(String template, String args) {
    int max = -1;
    for (int i = 0; i < 10; i++) {
      if (template.contains("{" + i + "}")) {
        max = i;
      }
    }
    String[] values = max < 0 ? new String[0] : args.split(",", max + 1);
    String out = template;
    for (int i = 0; i <= max; i++) {
      out = out.replace("{" + i + "}", i < values.length ? values[i].trim() : "");
    }
    return out;
  }
}
