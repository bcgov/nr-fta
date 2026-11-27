package ca.bc.gov.nrs.fta.range.tenuretab;

import ca.bc.gov.nrs.fta.tenure.tab.details.TenureDetailsMessages;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns what FTA_100RANGE_TENURE (and the PL/SQL it calls) appends to {@code p_error_message}
 * into legacy's text, as {@code parseToErrorsWarnings} did: entries are {@code key;} or
 * {@code key:arg,arg;}; an entry whose arguments start {@code ~W,} (or a bare {@code ~W}) is a
 * warning, which does not stop a save; {@code fta.web.error.user.custom.msg} carries its own
 * text. Texts are ApplicationResources.properties'.
 */
public final class RangeTenureMessages {

  private RangeTenureMessages() {}

  /** The package's messages, split. */
  public record Parsed(List<String> errors, List<String> warnings) {
    public boolean failed() {
      return !errors.isEmpty();
    }

    /** Whether the failure is a revision-count mismatch (another user saved first). */
    public boolean modified() {
      return errors.stream().anyMatch(e -> e.startsWith(MODIFIED_PREFIX));
    }
  }

  private static final String CUSTOM = "fta.web.error.user.custom.msg";

  static final String MODIFIED_PREFIX =
      "The record you are attempting to update has been modified by another user.";

  private static final Map<String, String> TEXT = buildTexts();

  private static Map<String, String> buildTexts() {
    Map<String, String> t = new HashMap<>(TenureDetailsMessages.statusChangeTexts());
    t.put("sil.error.usr.invalid.value", "Invalid {0}.");
    t.put("fta.type.change.invalid", "Type change from {0} to {1} is invalid.");
    t.put("fta.web.error.user.status.pa",
        "Only Road Permits Special Use Permits and License to Cut(Waiting for spatial)"
            + "can be changed to PA status.");
    t.put("fta.web.warning.retire.allowed", "Tenure may now be retired.");
    t.put("fta.web.warning.FtaAuditLogged", "{0}.");
    t.put("fta.web.error.usr.duplicateNonOtherRangeCondition",
        "Range tenure already has a special condition of type {0}.");
    t.put("fta.web.usr.database.record.InvalidRangeSpecialCondition",
        "The selected special condition does not exist.");
    t.put("fta.web.usr.database.record.notenureapp",
        "A submission record for this tenure cannot be found. Please contact support.");
    t.put("fta.web.usr.database.record.retired", "The file has already been retired.");
    t.put("fta.web.xml.database.file_id_generate", "Error occurred generating file id.");
    t.put("fta.web.usr.database.record.modified",
        MODIFIED_PREFIX + " Reload the tenure and try again. System Information: Package {0},"
            + " Action {1}, Table {2}.");
    t.put("fta.web.usr.database.record.invalid",
        "No Records Found. System Information: Table {2} Package/Procedure {0}, {1}.");
    t.put("fta.web.usr.database.record.toomany",
        "Multiple records were found when expecting only one. System Information"
            + " Package/Procedure {0}, {1}. Table {2}.");
    t.put("fta.web.usr.database.record.duplicate",
        "A record was found with an identical key value. System Information: table {2},"
            + " package/procedure {0}, {1}.");
    t.put("fta.web.usr.database.record.DistrictOverrideCommentRequired",
        "District override comment required.");
    t.put("fta.web.usr.database.unexpected",
        "An unexpected error has occurred in package/procedure {0}, {1} - {2}, {3} Please"
            + " contact System Support.");
    t.put("fta.web.usr.database.proc.unexpected",
        "An unexpected error has occurred in procedure {0},{1}. Please contact System"
            + " Support.");
    t.put("fta.web.usr.database.invalid.function",
        "An unknown database action was requested. System Information - {0} - {1} - {2}.");
    return Map.copyOf(t);
  }

  public static Parsed parse(String message) {
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
      } else if ("~W".equals(args)) {
        warnings.add(format(key, ""));
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
    return new MessageFormat(pattern.replace("'", "''")).format(values);
  }
}
