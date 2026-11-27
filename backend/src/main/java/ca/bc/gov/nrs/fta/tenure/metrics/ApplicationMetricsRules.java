package ca.bc.gov.nrs.fta.tenure.metrics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * FTA008 Application Metrics Export's checks and layout that need no database:
 * {@code Fta008MetricsSrchForm}'s validators, the inputs {@code THE.FTA_008_METRICS_EXTRACT}
 * splices into its SQL, {@code Fta008MetricsSrchAction}'s two sheets and file name, and the text
 * of the messages the package returns (ApplicationResources.properties).
 */
final class ApplicationMetricsRules {

  /** The metrics sheet: one row per CP application (GET_METRICS). */
  static final String METRICS_SHEET = "Metrics";
  /** The holds sheet: one row per hold on those applications (GET_HOLDS). */
  static final String HOLDS_SHEET = "Holds";

  /** A sheet's column: the heading legacy wrote, and the cursor column under it. */
  record Column(String heading, String name) {}

  /** {@code handleSubmitReport}'s sheet 1 headings, over the cursor's columns, in order. */
  static final List<Column> METRICS_COLUMNS = List.of(
      new Column("District", "DISTRICT"),
      new Column("Licensee", "LICENSEE"),
      new Column("Tenure ID", "TENURE_ID"),
      new Column("Cutting Permit ID", "CUTTING_PERMIT_ID"),
      new Column("Tenure Type", "TENURE_TYPE"),
      new Column("Application Purpose", "APPLICATION_PURPOSE"),
      new Column("Submission Date", "SUBMISSION_DATE"),
      new Column("Email Recd Date", "EMAIL_RECD_DATE"),
      new Column("Email Acceptance Date", "EMAIL_ACCEPTANCE_DATE"),
      new Column("FN Consult Start", "FN_CONSULT_START"),
      new Column("FN Consult End", "FN_CONSULT_END"),
      new Column("Decision Date", "DECISION_DATE"),
      new Column("Issuance Date", "ISSUANCE_DATE"),
      new Column("On Hold", "ON_HOLD"),
      new Column("Submission State", "SUBMISSION_STATE"),
      new Column("Total Days Processing", "TOTAL_DAYS_PROCESSING"),
      new Column("Total Days on Hold", "TOTAL_DAYS_ON_HOLD"),
      new Column("Net Num Days", "NET_NUM_DAYS"),
      new Column("Wildfire CP", "WILDFIRE_CP"));

  /** Sheet 2's headings, over the same cursor type's hold columns. */
  static final List<Column> HOLDS_COLUMNS = List.of(
      new Column("Tenure ID", "TENURE_ID"),
      new Column("Cutting Permit ID", "CUTTING_PERMIT_ID"),
      new Column("Hold Start Date", "HOLD_START_DATE"),
      new Column("Hold End Date", "HOLD_END_DATE"),
      new Column("Hold Reason", "HOLD_REASON"),
      new Column("Hold Notes", "HOLD_NOTES"),
      new Column("# Days on Hold", "NUM_DAYS_ON_HOLD"));

  static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  static final String DISTRICT_INVALID = "District must be an org unit number.";

  private static final Pattern ORG_UNIT_NO = Pattern.compile("\\d{1,10}");

  private static final DateTimeFormatter ISO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  /** getDownloadFileName's stamp. */
  private static final DateTimeFormatter FILE_STAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

  private ApplicationMetricsRules() {}

  /**
   * The screen's criteria: District (an ORG_UNIT_NO, blank for every district) and five date
   * ranges ({@code YYYY-MM-DD}, each end optional).
   */
  record Criteria(
      String orgUnitNo,
      String submissionDateFrom,
      String submissionDateTo,
      String emailRecdDateFrom,
      String emailRecdDateTo,
      String acceptanceDateFrom,
      String acceptanceDateTo,
      String decisionDateFrom,
      String decisionDateTo,
      String issuanceDateFrom,
      String issuanceDateTo) {}

  /** One date range, with the labels legacy's validators named its ends by. */
  private record Range(String fromLabel, String from, String toLabel, String to) {}

  private static List<Range> ranges(Criteria c) {
    return List.of(
        new Range("Submission Date From", c.submissionDateFrom(),
            "Submission Date To", c.submissionDateTo()),
        new Range("Email Rec'd Date From", c.emailRecdDateFrom(),
            "Email Rec'd Date To", c.emailRecdDateTo()),
        new Range("Acceptance Date From", c.acceptanceDateFrom(),
            "Acceptance Date To", c.acceptanceDateTo()),
        new Range("Decision Date From", c.decisionDateFrom(),
            "Decision Date To", c.decisionDateTo()),
        new Range("Issuance Date From", c.issuanceDateFrom(),
            "Issuance Date To", c.issuanceDateTo()));
  }

  /**
   * Export's validators, in the order the form added them: each range's From ≤ To
   * ({@code sil.error.usr.field.lessthanequal}), then each date's format
   * ({@code sil.error.usr.date.format.general}). None is mandatory; a range with an
   * unreadable end is not compared. The District check is not legacy's (it was a drop-down):
   * the package splices the value into its SQL, so only a number gets through.
   */
  static List<String> validate(Criteria c) {
    List<String> errors = new ArrayList<>();
    if (!blank(c.orgUnitNo()) && !ORG_UNIT_NO.matcher(c.orgUnitNo().trim()).matches()) {
      errors.add(DISTRICT_INVALID);
    }
    List<Range> ranges = ranges(c);
    for (Range r : ranges) {
      LocalDate from = date(r.from());
      LocalDate to = date(r.to());
      if (from != null && to != null && from.isAfter(to)) {
        errors.add("%s must be less than or equal to %s.".formatted(r.fromLabel(), r.toLabel()));
      }
    }
    // The form added Submission and Acceptance To before From, the others From before To.
    for (int i = 0; i < ranges.size(); i++) {
      Range r = ranges.get(i);
      boolean toFirst = i == 0 || i == 2;
      if (toFirst) {
        checkFormat(errors, r.toLabel(), r.to());
      }
      checkFormat(errors, r.fromLabel(), r.from());
      if (!toFirst) {
        checkFormat(errors, r.toLabel(), r.to());
      }
    }
    return errors;
  }

  private static void checkFormat(List<String> errors, String label, String value) {
    if (!blank(value) && date(value) == null) {
      errors.add("%s must be a valid date in the format YYYY-MM-DD.".formatted(label));
    }
  }

  /**
   * MAINLINE's criteria, in its order after {@code p_action} ({@code p_admin_org_unit_no}, then
   * the ten dates): trimmed, blanks as null. Call only once {@link #validate} passes.
   */
  static List<String> procedureArguments(Criteria c) {
    return Arrays.asList(
        trimmed(c.orgUnitNo()),
        trimmed(c.submissionDateFrom()),
        trimmed(c.submissionDateTo()),
        trimmed(c.emailRecdDateFrom()),
        trimmed(c.emailRecdDateTo()),
        trimmed(c.acceptanceDateFrom()),
        trimmed(c.acceptanceDateTo()),
        trimmed(c.decisionDateFrom()),
        trimmed(c.decisionDateTo()),
        trimmed(c.issuanceDateFrom()),
        trimmed(c.issuanceDateTo()));
  }

  /** getDownloadFileName: {@code yyyy-MM-dd_HHmmss_MetricsOutput.xlsx}. */
  static String filename(LocalDateTime now) {
    return now.format(FILE_STAMP) + "_MetricsOutput.xlsx";
  }

  /**
   * The package's {@code p_error_message} as text: each {@code key:args;} entry as
   * ApplicationResources.properties words it; blank when there is none.
   */
  static String message(String errorMessage) {
    if (blank(errorMessage)) {
      return "";
    }
    List<String> out = new ArrayList<>();
    for (String entry : errorMessage.split(";")) {
      if (entry.isBlank()) {
        continue;
      }
      int colon = entry.indexOf(':');
      String key = colon < 0 ? entry.trim() : entry.substring(0, colon).trim();
      String[] args = colon < 0 ? new String[0] : entry.substring(colon + 1).split(",", 4);
      out.add(switch (key) {
        case "sil.web.usr.database.unexpected" ->
            "An unexpected error has occurred in package/procedure %s, %s - %s, %s."
                .formatted(arg(args, 0), arg(args, 1), arg(args, 2), arg(args, 3));
        case "sil.web.usr.database.invalid.function" ->
            "Unknown database action - %s.".formatted(arg(args, 0));
        default -> entry.trim();
      });
    }
    return String.join(" ", out);
  }

  private static String arg(String[] args, int i) {
    return i < args.length ? args[i].trim() : "";
  }

  private static LocalDate date(String s) {
    if (blank(s)) {
      return null;
    }
    try {
      return LocalDate.parse(s.trim(), ISO);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String trimmed(String s) {
    return blank(s) ? null : s.trim();
  }
}
