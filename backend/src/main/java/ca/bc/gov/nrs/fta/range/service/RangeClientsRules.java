package ca.bc.gov.nrs.fta.range.service;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy FTA920R (Range Associated Clients) rules that live outside its package: the form's
 * field checks ({@code Fta920RAssoClientsForm}'s validators for Get and Save), the view-only
 * test, and how {@code FTA_920R_ASSO_CLIENTS_DETAILS}'s messages read.
 *
 * <p>The package decides the rest. Its GET of a related client fails with "Client already exists
 * as the main or secondary licensee" and with the expired-location message; its SAVE ends the
 * open previous licensees, makes the current A and B licensees C and P, and inserts the new
 * client as A with any joint-venture partners as B.
 */
public final class RangeClientsRules {

  private RangeClientsRules() {}

  /** Labels the form's validators named the fields by. */
  static final String CLIENT_NUMBER = "Client Number";

  static final String CLIENT_LOCATION = "Client Location Code";
  static final String START_DATE = "Licensee Start Date";
  static final String PREVIOUS_END_DATE = "Previous Licensee End Date";

  /** {@code sil.error.usr.required}. */
  static String mandatory(String label) {
    return label + " is mandatory.";
  }

  /** {@code sil.error.usr.date.format.general} with the form's YYYY-MM-DD. */
  static String badDate(String label) {
    return label + " must be a valid date in the format YYYY-MM-DD.";
  }

  /** {@code fta.web.error.usr.notAuthorizedToAttachClient} for the FILE level (attachAuthority). */
  public static final String NOT_AUTHORIZED = "You are not authorized to attach clients at the"
      + " FILE level.";

  /** The package's {@code ~W} warning when an application is in the Inbox. */
  public static final String APPLICATION_PENDING =
      "You may not update while application is in the Inbox.";

  /** {@code fta.web.warning.update.current.tenure.client.success}. */
  public static final String SAVED =
      "Update successful. The Range Tenure Current Client has been updated.";

  private static final DateTimeFormatter ISO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  /** Get's checks: the client number and location are required. */
  static List<String> getErrors(String clientNumber, String clientLocnCode) {
    List<String> errors = new ArrayList<>();
    if (isBlank(clientNumber)) {
      errors.add(mandatory(CLIENT_NUMBER));
    }
    if (isBlank(clientLocnCode)) {
      errors.add(mandatory(CLIENT_LOCATION));
    }
    return errors;
  }

  /**
   * Save's checks, in the form's order: Licensee Start Date required and a date, Previous
   * Licensee End Date a date when entered, then the client. Legacy did not compare the two
   * dates (that validator is commented out).
   */
  static List<String> saveErrors(String clientNumber, String clientLocnCode, String startDate,
      String previousEndDate) {
    List<String> errors = new ArrayList<>();
    if (isBlank(startDate)) {
      errors.add(mandatory(START_DATE));
    } else if (date(startDate) == null) {
      errors.add(badDate(START_DATE));
    }
    if (!isBlank(previousEndDate) && date(previousEndDate) == null) {
      errors.add(badDate(PREVIOUS_END_DATE));
    }
    errors.addAll(getErrors(clientNumber, clientLocnCode));
    return errors;
  }

  /** A YYYY-MM-DD date, or null when blank or not a date. */
  static LocalDate date(String text) {
    if (isBlank(text)) {
      return null;
    }
    try {
      return LocalDate.parse(text.trim(), ISO);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** The date as the package reads it (YYYY-MM-DD), or null when blank. */
  static String packageDate(String text) {
    LocalDate d = date(text);
    return d == null ? null : d.format(ISO);
  }

  /**
   * Whether the user may save: mainline GET's {@code p_save_ok}. The file authority and org
   * match are {@link RangeFileAccess}; an application in the Inbox makes the file view only.
   */
  static boolean canEdit(boolean mayEditFile, boolean applicationPending) {
    return mayEditFile && !applicationPending;
  }

  /**
   * The warning GET shows: legacy checked the Inbox only for a user whose org matched the file
   * ({@code ELSIF} after the org test), so others see no warning.
   */
  static List<String> warnings(boolean mayEditFile, boolean applicationPending) {
    return mayEditFile && applicationPending ? List.of(APPLICATION_PENDING) : List.of();
  }

  private static final String CUSTOM = "fta.web.error.user.custom.msg";

  /** ApplicationResources.properties text for the keys the package returns. */
  private static final Map<String, String> TEXT = Map.ofEntries(
      Map.entry(CUSTOM, "{0}."),
      Map.entry("fta.web.usr.database.record.expiredClientLocation",
          "That client location is expired. Please check your client and location."),
      Map.entry("fta.web.usr.database.record.invalid",
          "No Records Found. System Information: Table {2} Package/Procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.toomany",
          "Multiple records were found when expecting only one. System Information"
              + " Package/Procedure {0}, {1}. Table {2}."),
      Map.entry("fta.web.usr.database.record.duplicate",
          "A record was found with an identical key value. This indicates that the record you"
              + " are adding or updating already exists. System Information: table {2} ,"
              + " package/procedure {0}, {1}."),
      Map.entry("fta.web.usr.database.record.modified",
          "The record you are attempting to update has been modified by another user."
              + " System Information: Package {0}, Action {1}, Table {2}."),
      Map.entry("fta.web.usr.database.unexpected",
          "An unexpected error has occurred in package/procedure {0}, {1} - {2}, {3} Please"
              + " contact System Support."),
      Map.entry("fta.web.usr.database.invalid.function",
          "An unknown database action was requested. System Information - {0} - {1} - {2}."));

  /**
   * The package's {@code p_error_message} as legacy's texts. Entries are {@code key;} or
   * {@code key:args;} (the expired-location key comes without its semicolon); a custom message
   * starting {@code ~W,} is a warning, which this package's related-client calls never send.
   */
  static List<String> errors(String message) {
    List<String> out = new ArrayList<>();
    if (isBlank(message)) {
      return out;
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
        if (!args.trim().startsWith("~W,")) {
          out.add(format(key, new Object[] {args.trim()}));
        }
        continue;
      }
      String pattern = TEXT.get(key);
      if (pattern == null) {
        out.add(args.isEmpty() ? key : key + ": " + args);
      } else {
        out.add(format(key, args.isEmpty() ? new Object[0] : args.split(",", -1)));
      }
    }
    return out;
  }

  private static String format(String key, Object[] values) {
    // MessageFormat treats single quotes as escapes; the texts have none, but be safe.
    return new MessageFormat(TEXT.get(key).replace("'", "''")).format(values);
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
