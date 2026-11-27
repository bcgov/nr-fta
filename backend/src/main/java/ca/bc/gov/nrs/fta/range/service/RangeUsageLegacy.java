package ca.bc.gov.nrs.fta.range.service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA613R's Save and Copy, by legacy's own package {@code THE.FTA_613R_RANGE_USAGE}, called
 * rather than ported so its term check and its insert-or-update per year stay exactly legacy's.
 * Only {@code MAINLINE} is public on it.
 *
 * <ul>
 *   <li>{@code SAVE} — refuses a year outside the licence term (start date to the current, else
 *       initial, expiry), else updates the year's RANGE_PROVISION row under the revision read, or
 *       inserts it.
 *   <li>{@code COPY_UPDATE} — writes the values to every remaining year of the term after the
 *       source year, or to the years From to To, which must lie within the term.
 * </ul>
 *
 * <p>The package reports a refused year as a {@code ~W} custom message and writes nothing; legacy
 * then showed it beside "Save successful". Here every message the package returns fails the
 * request, so nothing is claimed that was not written.
 */
@Component
public class RangeUsageLegacy {

  /** {@code FTA_613R_RANGE_USAGE.MAINLINE}'s parameters, bar the result set, each a v_* local. */
  private static final List<String> PARAMS = List.of(
      "action", "forest_file_id", "pfu_revision_count", "file_type_cd", "file_type_desc",
      "admin_org", "licencee", "status", "status_date", "start_date", "expiry_date",
      "user_org_unit_no", "save_ok", "update_userid", "error_message", "calendar_year",
      "authorized_use", "non_use_agreement_no", "non_use_nonbillable", "non_use_billable",
      "range_non_use_reason_code", "temp_increase", "range_increase_reason_code",
      "total_annual_use", "revision_count", "all_remaining_years", "copy_from_year",
      "copy_to_year");

  /** The values {@link #call} binds, in order, before the error message it reads back. */
  private static final List<String> INPUTS = List.of(
      "action", "forest_file_id", "start_date", "expiry_date", "update_userid", "calendar_year",
      "authorized_use", "non_use_agreement_no", "non_use_nonbillable", "non_use_billable",
      "range_non_use_reason_code", "temp_increase", "range_increase_reason_code",
      "total_annual_use", "revision_count", "all_remaining_years", "copy_from_year",
      "copy_to_year");

  static final String BLOCK = "DECLARE "
      + PARAMS.stream().map(p -> "v_" + p + " VARCHAR2(4000);").collect(Collectors.joining(" "))
      + " v_results the.fta_613r_range_usage.ref_range_usage_results; BEGIN "
      + INPUTS.stream().map(p -> "v_" + p + " := ?;").collect(Collectors.joining(" "))
      + " the.fta_613r_range_usage.mainline("
      + PARAMS.stream().map(p -> "p_" + p + " => v_" + p).collect(Collectors.joining(", "))
      + ", p_range_usage_results => v_results); ? := v_error_message; END;";

  private static final String CUSTOM = "fta.web.error.user.custom.msg";
  private static final String MODIFIED = "fta.web.usr.database.record.modified";
  private static final String WARNING_PREFIX = "~W,";
  static final String MSG_MODIFIED =
      "The range usage was changed by someone else. Reload it and try again.";

  /** What the package reported; empty errors mean it wrote the change. */
  public record Outcome(List<String> errors, boolean modified) {}

  /** The values one Save or Copy sends; amounts as entered, blank as null. */
  public record Values(
      String forestFileId,
      String startDate,
      String expiryDate,
      String userId,
      int calendarYear,
      String authorizedUse,
      String nonUseAgreementNo,
      String nonUseNonbillable,
      String nonUseBillable,
      String rangeNonUseReasonCode,
      String tempIncrease,
      String rangeIncreaseReasonCode,
      int totalAnnualUse) {}

  private final JdbcTemplate jdbc;

  public RangeUsageLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /** {@code SAVE}, under the revision read (0 for a new year, as legacy's New set it). */
  public Outcome save(Values v, Integer revisionCount) {
    return parse(call(binds("SAVE", v,
        revisionCount == null ? "0" : revisionCount.toString(), null, null, null)));
  }

  /** {@code COPY_UPDATE}: every remaining year after the source year, or From to To. */
  public Outcome copy(Values v, boolean allRemainingYears, Integer fromYear, Integer toYear) {
    return parse(call(binds("COPY_UPDATE", v, null, allRemainingYears ? "Y" : "N",
        fromYear == null ? null : fromYear.toString(),
        toYear == null ? null : toYear.toString())));
  }

  static List<String> binds(String action, Values v, String revisionCount, String allRemaining,
      String fromYear, String toYear) {
    List<String> b = new ArrayList<>();
    b.add(action);
    b.add(v.forestFileId());
    b.add(v.startDate());
    b.add(v.expiryDate());
    b.add(v.userId());
    b.add(Integer.toString(v.calendarYear()));
    b.add(v.authorizedUse());
    b.add(v.nonUseAgreementNo());
    b.add(v.nonUseNonbillable());
    b.add(v.nonUseBillable());
    b.add(v.rangeNonUseReasonCode());
    b.add(v.tempIncrease());
    b.add(v.rangeIncreaseReasonCode());
    b.add(Integer.toString(v.totalAnnualUse()));
    b.add(revisionCount);
    b.add(allRemaining);
    b.add(fromYear);
    b.add(toYear);
    return b;
  }

  private String call(List<String> binds) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(BLOCK),
        (CallableStatement cs) -> {
          int i = 1;
          for (String bind : binds) {
            cs.setString(i++, bind);
          }
          cs.registerOutParameter(i, Types.VARCHAR);
          cs.execute();
          return cs.getString(i);
        });
  }

  /**
   * Legacy error strings are {@code key;} or {@code key:args;} pairs. A custom message (with or
   * without legacy's {@code ~W,} warning prefix) is shown as it is; a modified record gets a
   * reload message; anything else is shown raw.
   */
  static Outcome parse(String raw) {
    List<String> errors = new ArrayList<>();
    boolean modified = false;
    if (raw == null) {
      return new Outcome(errors, false);
    }
    for (String part : raw.split(";")) {
      String p = part.trim();
      if (p.isEmpty()) {
        continue;
      }
      int colon = p.indexOf(':');
      String key = colon < 0 ? p : p.substring(0, colon);
      String args = colon < 0 ? "" : p.substring(colon + 1).trim();
      if (CUSTOM.equals(key)) {
        String text = args.startsWith(WARNING_PREFIX)
            ? args.substring(WARNING_PREFIX.length()) : args;
        errors.add(sentence(text));
      } else if (MODIFIED.equals(key)) {
        errors.add(MSG_MODIFIED);
        modified = true;
      } else {
        errors.add(p);
      }
    }
    return new Outcome(errors.stream().distinct().toList(), modified);
  }

  private static String sentence(String s) {
    String t = s.trim();
    return t.endsWith(".") ? t : t + ".";
  }
}
