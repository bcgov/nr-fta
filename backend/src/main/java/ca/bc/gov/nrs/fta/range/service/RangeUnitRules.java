package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitSaveRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * FTA630's checks that need no database: {@code Fta630MnRgUnPstForm}'s validators and
 * {@code forceFormats}, {@code Fta630MnRgUnPstAction.handleSaveRange}'s As of default, the guards
 * of {@code FTA_630_MN_RG_UN_PST.REMOVE} / {@code REMOVE_ITEM}, and the text of the messages the
 * package returns (ApplicationResources.properties).
 */
final class RangeUnitRules {

  static final int NAME_MAX = 30;
  static final int ZONE_MAX = 4;
  static final int PASTURE_ID_MAX = 2;
  static final int PASTURE_NAME_MAX = 30;

  /** A new range unit's status: the screen preset P and disabled the list. */
  static final String NEW_STATUS = "P";
  /** The pasture the package creates with each unit, named after it, which cannot be deleted. */
  static final String FIRST_PASTURE = "00";

  static final String NAME_REQUIRED = "Range Unit Name is mandatory.";
  static final String ZONE_REQUIRED = "Zone is mandatory.";
  static final String ZONE_NOT_ALPHANUMERIC = "Zone must be alpha numeric [A-Z, 0-9]";
  static final String STATUS_REQUIRED = "Status is mandatory.";
  static final String DISTRICT_REQUIRED = "District is mandatory.";
  static final String DISTRICT_INVALID = "Invalid District.";
  static final String AS_OF_FORMAT = "As of must be in the format YYYY-MM-DD.";
  static final String BLANK_ON_NEW = "Range Unit will be generated. Please leave blank.";
  static final String REVISION_REQUIRED = "Revision count is required.";
  static final String DISTRICT_NOT_IN_REGION = "District does not fall within the region.";
  /** handleSaveRange's warning (fta.web.warning.statusDateChange). */
  static final String STATUS_DATE_CHANGE = "As of Date has been updated to Today's date.";
  /** The package's warning (fta.web.warning.CbStatusDateChanged). */
  static final String CB_STATUS_DATE_CHANGED = "As of Date has been updated to today's date.";

  static final String PASTURE_ID_REQUIRED = "Pasture ID is mandatory.";
  static final String PASTURE_ID_INTEGER = "Pasture ID must be an integer.";
  static final String PASTURE_NAME_REQUIRED = "Pasture Description is mandatory.";
  static final String PASTURE_REVISION_REQUIRED = "Pasture revision count is required.";
  static final String DUPLICATE_PASTURE =
      "Pasture ID or Name already exists for this Range Unit.";

  static final String UNIT_RISK_SITE =
      "Range Unit cannot be deleted as it is being referenced in a CIMS range risk site";
  static final String UNIT_INSPECTION =
      "Range Unit cannot be deleted. It is being referenced in a CIMS range inspection";
  static final String PASTURE_RISK_SITE =
      "Range Unit pasture cannot be deleted. It is being referenced in a CIMS range risk site";
  static final String PASTURE_INSPECTION =
      "Range Unit pasture cannot be deleted. It is being referenced in a CIMS range inspection";
  static final String DELETE_MEADOW =
      "This range/pasture are used by Meadow Rotations and cannot be deleted.";
  static final String DELETE_LIVESTOCK =
      "This range/pasture are used by Livestock Rotations and cannot be deleted.";
  static final String DELETE_FIRST_PASTURE =
      "Records with a Pasture Id of 00 may not be deleted.";

  /**
   * fta.web.usr.database.record.modified, with "press the GO button" put as this screen has it.
   */
  static final String MODIFIED = "The record you are attempting to update has been modified by"
      + " another user. Reload the range unit and try again.";

  private static final DateTimeFormatter ISO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  private static final String CUSTOM = "fta.web.error.user.custom.msg";
  private static final String MODIFIED_KEY = "fta.web.usr.database.record.modified";
  private static final String AUTH_KEY = "fta.web.error.districtNotInRegion";

  /** ApplicationResources.properties text for the keys FTA_630_MN_RG_UN_PST returns. */
  private static final Map<String, String> TEXT = Map.ofEntries(
      Map.entry(AUTH_KEY, DISTRICT_NOT_IN_REGION),
      Map.entry("fta.web.warning.CbStatusDateChanged", CB_STATUS_DATE_CHANGED),
      Map.entry("fta.web.usr.database.record.DuplicatePastureName", DUPLICATE_PASTURE),
      Map.entry("fta.web.usr.database.record.DeleteNotAllowed_Meadow", DELETE_MEADOW),
      Map.entry("fta.web.usr.database.record.DeleteNotAllowed_Livestock", DELETE_LIVESTOCK),
      Map.entry("fta.web.usr.database.record.DeleteNotAllowed", DELETE_FIRST_PASTURE),
      Map.entry("fta.web.usr.database.record.RangeUnitIdExists",
          "Range Unit Id already exists. Please contact system support."),
      Map.entry("sil.error.usr.invalid.value", "Invalid {0}."),
      Map.entry("fta.web.usr.database.record.invalid",
          "No Records Found. System Information: Table {2} Package/Procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.toomany",
          "Multiple records were found when expecting only one. System Information"
              + " Package/Procedure {0}, {1}. Table {2}."),
      Map.entry("fta.web.usr.database.record.duplicate",
          "A record was found with an identical key value. System Information: table {2},"
              + " package/procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.unexpected",
          "An unexpected error has occurred in package/procedure {0}, {1} - {2}, {3} Please"
              + " contact System Support."),
      Map.entry("fta.web.usr.database.invalid.function",
          "An unknown database action was requested. System Information - {0} - {1} - {2}."));

  /**
   * Keys longest first, so a message with no {@code ;} after it (REMOVE_ITEM's
   * DeleteNotAllowed_Livestock) is still told apart from what the package appends to it.
   */
  private static final List<String> KEYS = TEXT.keySet().stream()
      .sorted((a, b) -> b.length() - a.length())
      .toList();

  private RangeUnitRules() {}

  /** The package's messages, split as legacy's parseToErrorsWarnings did. */
  record Parsed(List<String> errors, List<String> warnings, boolean modified,
      boolean notAuthorized) {
    boolean failed() {
      return !errors.isEmpty();
    }

    /**
     * The delete guards among the errors. REMOVE_ITEM ends its livestock message with no
     * {@code ;} and may follow it with a spurious "modified", so when a guard stopped the delete
     * it is the whole story.
     */
    List<String> deleteBlocks() {
      return errors.stream().filter(DELETE_BLOCKS::contains).toList();
    }
  }

  // ── Save Range ─────────────────────────────────────────────────────

  /**
   * Save Range's validators, in the form's order: Range Unit Name, Zone (alphanumeric, then
   * required), Status, District, As of, and on an add the id left blank. Empty when the unit can
   * be saved. Expired codes need the code lists and are {@link #expiredCode}.
   *
   * @param adding an Add New (no revision count): Status is the preset P
   */
  static List<String> validateSave(RangeUnitSaveRequest r, boolean adding) {
    List<String> e = new ArrayList<>();
    if (r == null) {
      return List.of(NAME_REQUIRED, ZONE_REQUIRED, STATUS_REQUIRED, DISTRICT_REQUIRED);
    }
    if (blank(r.rangeUnitName())) {
      e.add(NAME_REQUIRED);
    } else if (r.rangeUnitName().trim().length() > NAME_MAX) {
      e.add(tooLong("Range Unit Name", NAME_MAX));
    }
    String zone = zone(r.districtAdminZone());
    if (zone != null && !zone.matches("[A-Z0-9]*")) {
      e.add(ZONE_NOT_ALPHANUMERIC);
    }
    if (zone == null) {
      e.add(ZONE_REQUIRED);
    } else if (zone.length() > ZONE_MAX) {
      e.add(tooLong("Zone", ZONE_MAX));
    }
    if (!adding && blank(r.status())) {
      e.add(STATUS_REQUIRED);
    }
    if (blank(r.district())) {
      e.add(DISTRICT_REQUIRED);
    } else if (!r.district().trim().matches("\\d{1,10}")) {
      e.add(DISTRICT_INVALID);
    }
    if (!blank(r.asOf()) && date(r.asOf()) == null) {
      e.add(AS_OF_FORMAT);
    }
    if (adding && !blank(r.rangeUnitId())) {
      e.add(BLANK_ON_NEW);
    }
    if (!adding && r.revisionCount() == null) {
      e.add(REVISION_REQUIRED);
    }
    return e;
  }

  /** forceFormats on Save Range: the zone trimmed and upper-cased; null when blank. */
  static String zone(String raw) {
    return blank(raw) ? null : raw.trim().toUpperCase(Locale.ROOT);
  }

  /**
   * CodeExpiryValidator on Status and District: a code that changed must be one of the current
   * codes. Null when it is fine.
   *
   * @param stored  the unit's code as read, or null on an add
   * @param current the current codes of the list
   */
  static String expiredCode(String label, String code, String stored,
      Collection<String> current) {
    if (blank(code) || Objects.equals(code.trim(), stored) || current.contains(code.trim())) {
      return null;
    }
    return label + " (" + code.trim() + ") is an expired code.";
  }

  /** The As of to save, and the warning legacy gave when it set it. */
  record AsOf(String value, String warning) {}

  /**
   * handleSaveRange: a status change with no As of date is as of today, with a warning. (The
   * package then also moves an unchanged As of date to today on a status change, with its own
   * warning.)
   *
   * @param prevStatus the status as read, or null on an add
   */
  static AsOf asOf(String status, String prevStatus, String asOf, LocalDate today) {
    String value = blank(asOf) ? null : asOf.trim();
    if (!Objects.equals(nullToEmpty(status), nullToEmpty(prevStatus)) && value == null) {
      return new AsOf(today.toString(), STATUS_DATE_CHANGE);
    }
    return new AsOf(value, null);
  }

  // ── Pastures ──────────────────────────────────────────────────────

  /**
   * The pasture Save's validators: Pasture ID required and an integer (at most two characters,
   * the input's maxlength) and Pasture Description required. Update locks the id, so only an add
   * checks it.
   */
  static List<String> validatePasture(String pastureId, String pastureName, boolean adding) {
    List<String> e = new ArrayList<>();
    if (adding) {
      if (blank(pastureId)) {
        e.add(PASTURE_ID_REQUIRED);
      } else if (pastureId.trim().length() > PASTURE_ID_MAX) {
        e.add(tooLong("Pasture ID", PASTURE_ID_MAX));
      } else if (!isInteger(pastureId.trim())) {
        e.add(PASTURE_ID_INTEGER);
      }
    }
    if (blank(pastureName)) {
      e.add(PASTURE_NAME_REQUIRED);
    } else if (pastureName.trim().length() > PASTURE_NAME_MAX) {
      e.add(tooLong("Pasture Description", PASTURE_NAME_MAX));
    }
    return e;
  }

  // ── Deletes ───────────────────────────────────────────────────────

  /**
   * REMOVE_ITEM's last guard: pasture '00', created with the unit and named after it, may not be
   * deleted. Null for any other pasture. (Its other guards — a CIMS range risk site or inspection,
   * a livestock rotation — and all of REMOVE's are the package's, which can read those tables.)
   */
  static String pastureDeleteBlock(String pastureId) {
    return FIRST_PASTURE.equals(pastureId) ? DELETE_FIRST_PASTURE : null;
  }

  /** The guard messages REMOVE and REMOVE_ITEM return; a delete they stop was not a conflict. */
  static final List<String> DELETE_BLOCKS = List.of(UNIT_RISK_SITE, UNIT_INSPECTION,
      PASTURE_RISK_SITE, PASTURE_INSPECTION, DELETE_MEADOW, DELETE_LIVESTOCK,
      DELETE_FIRST_PASTURE);

  // ── The package's messages ────────────────────────────────────────

  /**
   * Splits {@code p_error_message} into errors and warnings. The package appends {@code key;} or
   * {@code key:args;}; an entry whose arguments start {@code ~W} is a warning, and
   * {@code fta.web.error.user.custom.msg:} carries its own text.
   */
  static Parsed parse(String message) {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    boolean modified = false;
    boolean notAuthorized = false;
    if (message == null || message.isBlank()) {
      return new Parsed(errors, warnings, false, false);
    }
    for (String part : message.split(";")) {
      String rest = part.trim();
      while (!rest.isEmpty()) {
        int colon = rest.indexOf(':');
        String head = colon < 0 ? rest : rest.substring(0, colon);
        String known = KEYS.stream().filter(head::startsWith).findFirst().orElse(null);
        if (known != null && head.length() > known.length()) {
          // A key with no ';' after it, then the next message.
          add(known, "", errors, warnings);
          rest = rest.substring(known.length());
          continue;
        }
        String key = head;
        String args = colon < 0 ? "" : rest.substring(colon + 1);
        if (CUSTOM.equals(key)) {
          errors.add(args.trim());
        } else {
          modified |= MODIFIED_KEY.equals(key);
          notAuthorized |= AUTH_KEY.equals(key);
          add(key, args, errors, warnings);
        }
        rest = "";
      }
    }
    return new Parsed(errors, warnings, modified, notAuthorized);
  }

  private static void add(String key, String args, List<String> errors, List<String> warnings) {
    if (MODIFIED_KEY.equals(key)) {
      errors.add(MODIFIED);
    } else if (args.startsWith("~W")) {
      warnings.add(format(key, ""));
    } else {
      errors.add(format(key, args));
    }
  }

  private static String format(String key, String args) {
    String pattern = TEXT.get(key);
    if (pattern == null) {
      return args.isEmpty() ? key : key + ": " + args;
    }
    String[] values = args.isEmpty() ? new String[0] : args.split(",", -1);
    String text = pattern;
    for (int i = 0; i < 4; i++) {
      text = text.replace("{" + i + "}", i < values.length ? values[i] : "");
    }
    return text;
  }

  // ── Helpers ───────────────────────────────────────────────────────

  static String tooLong(String label, int max) {
    return label + " cannot be longer than " + max + " characters.";
  }

  static LocalDate date(String value) {
    try {
      return LocalDate.parse(value.trim(), ISO);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static boolean isInteger(String v) {
    try {
      Integer.parseInt(v);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s.trim();
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
