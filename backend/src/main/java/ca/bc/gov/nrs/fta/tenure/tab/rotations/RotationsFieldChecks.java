package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The rotation tabs' field checks, ported from the legacy form validators with their message
 * texts ({@code sil.error.usr.*} in {@code ApplicationResources.properties}): pure functions,
 * so they are unit tested without a database.
 */
final class RotationsFieldChecks {

  /** {@code fta.web.usr.database.record.modified}, without its "press GO" advice. */
  static final String MODIFIED = "The record you are attempting to update has been modified by"
      + " another user. Reload the tab and try again.";

  /** {@code sil.web.error.usr.tenureTerm}. */
  static final String NOT_IN_TERM = "Calendar year is not within tenure term.";

  /** The days in a month of grazing, by which legacy turns head-days into AUMs. */
  private static final double DAYS_PER_AUM_MONTH = 30.436850;

  /** Legacy counts a sheep as a quarter of an animal unit. */
  private static final String SHEEP = "SH";

  private static final Pattern MONTH_DAY = Pattern.compile("\\d{2}-\\d{2}");


  private RotationsFieldChecks() {}

  static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  static String upper(String s) {
    String t = trim(s);
    return t == null ? null : t.toUpperCase(Locale.ROOT);
  }

  /**
   * Legacy's {@code RequiredFieldValidator} + {@code IntegerFieldValidator} +
   * {@code IntegerLimitsValidator} on one field. Adds the first failing message to
   * {@code errors} and returns null then, or when the field is blank.
   *
   * @param requiredLabel the label the required check names (legacy sometimes differs, e.g.
   *                      "No." vs "Livestock Count"); null when the field is optional
   */
  static Integer integer(
      String raw, String requiredLabel, String label, long min, long max, List<String> errors) {
    String v = trim(raw);
    if (v == null) {
      if (requiredLabel != null) {
        errors.add(requiredLabel + " is mandatory.");
      }
      return null;
    }
    long n;
    try {
      n = Long.parseLong(v);
    } catch (NumberFormatException e) {
      errors.add(label + " must be an integer.");
      return null;
    }
    if (n < min || n > max) {
      errors.add(label + " field must be between " + min + " and " + max + ".");
      return null;
    }
    return (int) n;
  }

  /**
   * A legacy MM-DD rotation day in {@code year} ({@code DateValidator} with format MM-DD
   * against "year-MM-DD"), or null when it is not a real day that year (02-29 in a year that
   * is not a leap year included).
   */
  static LocalDate monthDay(String mmdd, int year) {
    String v = trim(mmdd);
    if (v == null || !MONTH_DAY.matcher(v).matches()) {
      return null;
    }
    try {
      return LocalDate.of(
          year, Integer.parseInt(v.substring(0, 2)), Integer.parseInt(v.substring(3, 5)));
    } catch (DateTimeException e) {
      return null;
    }
  }

  /**
   * TTL AUMs when the user leaves them blank — {@code Fta611GrazeRotatnAction.setTTLAUM}:
   * head × days ÷ 30.43685, a quarter of that for sheep (SH), rounded. Days are end − start,
   * plus one when that is positive: legacy counts a same-day rotation as 0 days, kept as is.
   */
  static int aums(String livestockCode, int count, LocalDate begin, LocalDate end) {
    long days = ChronoUnit.DAYS.between(begin, end);
    if (days > 0) {
      days++;
    }
    double headMonths = (count * days) / DAYS_PER_AUM_MONTH;
    return (int) (SHEEP.equals(livestockCode)
        ? Math.round(headMonths * .25f)
        : Math.round(headMonths));
  }

  /** "row 2" or "rows 1, 3" — legacy's multi-row messages listed the rows. */
  static String rows(List<Integer> rows) {
    return (rows.size() == 1 ? "row " : "rows ")
        + rows.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  /**
   * The hay grid's own checks — {@code Fta612HaycutRotatForm}'s {@code RequiredMultiFieldValidator}s
   * (Range Unit, Meadow Name, Permit Block on every non-blank row) and the Auth Harvest
   * integer and range checks — plus the lengths of the legacy inputs, on the rows
   * {@code changed} names (an unchanged row is not saved, so data longer than today's inputs
   * does not block a save). Rows to delete and blank new rows are skipped. Rows are numbered
   * from 1 in the order given.
   */
  static List<String> hayRowErrors(List<RotationsDtos.HayRowRequest> rows, IntPredicate changed) {
    List<Integer> noRangeUnit = new ArrayList<>();
    List<Integer> noMeadow = new ArrayList<>();
    List<Integer> noBlock = new ArrayList<>();
    List<Integer> notInteger = new ArrayList<>();
    List<Integer> outOfRange = new ArrayList<>();
    List<Integer> blockTooLong = new ArrayList<>();
    List<Integer> rangeUnitTooLong = new ArrayList<>();
    List<Integer> meadowTooLong = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      RotationsDtos.HayRowRequest r = rows.get(i);
      int row = i + 1;
      if (r.delete() || hayRowBlank(r)) {
        continue;
      }
      boolean lengths = changed.test(i);
      String ru = trim(r.rangeUnitId());
      String meadow = trim(r.meadowName());
      String block = trim(r.permitBlockId());
      if (ru == null) {
        noRangeUnit.add(row);
      } else if (lengths && ru.length() > HAY_MAX_RANGE_UNIT) {
        rangeUnitTooLong.add(row);
      }
      if (meadow == null) {
        noMeadow.add(row);
      } else if (lengths && meadow.length() > HAY_MAX_MEADOW) {
        meadowTooLong.add(row);
      }
      if (block == null) {
        noBlock.add(row);
      } else if (lengths && block.length() > HAY_MAX_BLOCK) {
        blockTooLong.add(row);
      }
      String harvest = trim(r.authorizedHarvest());
      if (harvest != null) {
        try {
          int n = Integer.parseInt(harvest);
          if (n < 0 || n > HAY_MAX_HARVEST) {
            outOfRange.add(row);
          }
        } catch (NumberFormatException e) {
          notInteger.add(row);
        }
      }
    }
    List<String> e = new ArrayList<>();
    if (!noRangeUnit.isEmpty()) {
      e.add("Range Unit is required on " + rows(noRangeUnit) + ".");
    }
    if (!noMeadow.isEmpty()) {
      e.add("Meadow Name is required on " + rows(noMeadow) + ".");
    }
    if (!noBlock.isEmpty()) {
      e.add("Permit Block is required on " + rows(noBlock) + ".");
    }
    if (!notInteger.isEmpty()) {
      e.add("Auth Harvest must be an integer. Check " + rows(notInteger) + ".");
    }
    if (!outOfRange.isEmpty()) {
      e.add("Auth Harvest field must be in the range [0," + HAY_MAX_HARVEST + "]. Check "
          + rows(outOfRange) + ".");
    }
    if (!blockTooLong.isEmpty()) {
      e.add("Permit Block can be at most " + HAY_MAX_BLOCK + " characters. Check "
          + rows(blockTooLong) + ".");
    }
    if (!rangeUnitTooLong.isEmpty()) {
      e.add("Range Unit can be at most " + HAY_MAX_RANGE_UNIT + " characters. Check "
          + rows(rangeUnitTooLong) + ".");
    }
    if (!meadowTooLong.isEmpty()) {
      e.add("Meadow Name can be at most " + HAY_MAX_MEADOW + " characters. Check "
          + rows(meadowTooLong) + ".");
    }
    return e;
  }

  /** The legacy hay grid's input lengths and the Auth Harvest limit. */
  static final int HAY_MAX_BLOCK = 4;

  static final int HAY_MAX_RANGE_UNIT = 6;

  static final int HAY_MAX_MEADOW = 22;

  static final int HAY_MAX_HARVEST = 99999;

  /** {@code Fta612InsertBean.isRowBlank}: all four inputs empty. */
  static boolean hayRowBlank(RotationsDtos.HayRowRequest r) {
    return trim(r.permitBlockId()) == null
        && trim(r.rangeUnitId()) == null
        && trim(r.meadowName()) == null
        && trim(r.authorizedHarvest()) == null;
  }

  /** A row's Auth Harvest as a number for the total; legacy skips what does not parse. */
  static int harvestOrZero(String raw) {
    String v = trim(raw);
    if (v == null) {
      return 0;
    }
    try {
      return Integer.parseInt(v);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * {@code fta.web.error.usr.harvestTotal}, when the year's harvest plus non-use is not the
   * authorized tonnes; null when it is.
   */
  static String hayTotalError(long harvestPlusNonUse, long authorizedForageTonnes) {
    return harvestPlusNonUse == authorizedForageTonnes
        ? null
        : "The total harvest plus the non-use (" + harvestPlusNonUse + ") must equal the"
            + " Authorized Forage Tonnes (" + authorizedForageTonnes + "). Authorized Forage"
            + " Tonnes are updatable via the main tenure screen.";
  }
}
