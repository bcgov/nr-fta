package ca.bc.gov.nrs.fta.billing.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * FTA670 Annual Rents and Fees Preparation's rules outside the package
 * ({@code Fta670RentfeeAction} / {@code Fta670RentfeeForm} / {@code fta670Rentfee.jsp}), and the
 * text of what {@code THE.FTA_670_RENTFEE} appends to {@code p_error_message}
 * (ApplicationResources.properties).
 */
public final class RentsFeesRules {

  private RentsFeesRules() {}

  static final String ORG_UNIT_REQUIRED = "Org Unit is required.";
  static final String ROWS_REQUIRED = "There are no rows to prepare.";
  /** fta.web.warning.prepare.success */
  public static final String PREPARE_SUCCESS = "Prepare successful.";
  /** fta.web.warning.save.noChanges */
  public static final String NO_CHANGES = "No changes made.";

  /** The Opt dropdown's values: blank, R and D. D deletes the row; the others update it. */
  static final Set<String> INDICATORS = Set.of("", "R", "D");

  private static final String MODIFIED_KEY = "fta.web.usr.database.record.modified";
  private static final String AUTHORITY_KEY = "fta.web.error.authority_exception_view";

  /**
   * The indicator as the dropdown shows it: the package returns BILLABLE_TENURE's NOT NULL
   * column, where "none" is stored as a space (CHANGE writes {@code NVL(new, ' ')}).
   */
  static String normalize(String indicator) {
    return indicator == null ? "" : indicator.trim();
  }

  /**
   * Whether Prepare saves the row: legacy called SAVE only where the chosen Opt differed from
   * the one GET returned. A stored space and a blank choice are the same "none".
   */
  static boolean changed(String before, String after) {
    return !normalize(before).equals(normalize(after));
  }

  /** "Row n: Invalid Opt" (sil.multiRow.error.usr.invalid.value) for a value not offered. */
  static String invalidIndicator(int row, String indicator) {
    return INDICATORS.contains(normalize(indicator)) ? null : "Row %d: Invalid Opt".formatted(row);
  }

  /**
   * Rollup as GET receives it. Legacy hid the checkbox from district users, so theirs was
   * always N; here that is a district-scoped administrator.
   */
  static String rollup(boolean unscoped, Boolean requested) {
    return unscoped && Boolean.TRUE.equals(requested) ? "Y" : "N";
  }

  /**
   * The billing period's start as legacy's handleGet showed it: the package's first of next
   * month, or the earliest anniversary date listed when that is earlier (both YYYY-MM-DD,
   * compared as text as legacy did).
   */
  static String billFrom(String packageFrom, Collection<String> anniversaryDates) {
    String min = packageFrom;
    for (String d : anniversaryDates) {
      if (d != null && (min == null || d.compareTo(min) < 0)) {
        min = d;
      }
    }
    return min;
  }

  /** GET's messages, as text. Empty when it succeeded. */
  static List<String> getMessages(String errorMessage) {
    List<String> out = new ArrayList<>();
    for (Entry e : entries(errorMessage)) {
      out.add(switch (e.key) {
        case AUTHORITY_KEY ->
            "The records you are attempting to view belong to a District or Region outside your"
                + " Jurisdiction.";
        case "fta.web.usr.database.record.invalid" ->
            "No Records Found. System Information: Table %s Package/Procedure %s, %s."
                .formatted(e.arg(2), e.arg(0), e.arg(1));
        case "fta.web.usr.database.unexpected" ->
            ("An unexpected error has occurred in package/procedure %s, %s - %s, %s Please contact"
                + " System Support.").formatted(e.arg(0), e.arg(1), e.arg(2), e.rest(3));
        default -> e.raw;
      });
    }
    return out;
  }

  /** Whether GET refused the org unit as outside the user's jurisdiction. */
  static boolean isAuthorityError(String errorMessage) {
    return entries(errorMessage).stream().anyMatch(e -> AUTHORITY_KEY.equals(e.key));
  }

  /** "Row n has been previously modified. Re-select data." (fta.multiRow...record.modified) */
  static String modified(int row) {
    return "Row %d has been previously modified. Re-select data.".formatted(row);
  }

  /**
   * SAVE's messages for one row, as legacy's enhanceErrorMessage turned them into its
   * {@code fta.multiRow.*} texts: the row number in front, the package and action as one
   * "PACKAGE - ACTION". Empty when the row saved.
   */
  static List<String> saveMessages(String errorMessage, int row) {
    List<String> out = new ArrayList<>();
    for (Entry e : entries(errorMessage)) {
      String where = e.arg(0) + " - " + e.arg(1);
      out.add(switch (e.key) {
        case MODIFIED_KEY -> modified(row);
        case "fta.web.usr.database.record.toomany" ->
            ("Row %d: Multiple records were found in table %s when expecting an exact fetch by"
                + " package/procedure %s.").formatted(row, e.arg(2), where);
        case "fta.web.usr.database.record.duplicate" ->
            "Row %d: A duplicate key has been detected on table %s in package/procedure %s."
                .formatted(row, e.arg(2), where);
        case "fta.web.usr.database.record.invalid" ->
            "Row %d: No Records Found on table %s in package/procedure %s."
                .formatted(row, e.arg(2), where);
        case "fta.web.usr.database.unexpected" ->
            ("Row %d: An unexpected error has occurred in package/procedure %s, %s - %s Please"
                + " contact System Support.").formatted(row, where, e.arg(2), e.rest(3));
        default -> "Row %d: %s".formatted(row, e.raw);
      });
    }
    return out;
  }

  /** Whether any of SAVE's messages is the optimistic-lock failure. */
  static boolean isModified(String message) {
    return message != null && message.contains("has been previously modified");
  }

  /** {@code key:arg,arg;} entries; an argument list may itself hold commas (SQLERRM). */
  private record Entry(String key, String[] args, String raw) {
    String arg(int i) {
      return i < args.length ? args[i] : "";
    }

    String rest(int from) {
      return from < args.length
          ? String.join(",", java.util.Arrays.copyOfRange(args, from, args.length)) : "";
    }
  }

  private static List<Entry> entries(String message) {
    List<Entry> out = new ArrayList<>();
    if (message == null || message.isBlank()) {
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
      out.add(new Entry(key, args.isEmpty() ? new String[0] : args.split(",", -1), p));
    }
    return out;
  }
}
