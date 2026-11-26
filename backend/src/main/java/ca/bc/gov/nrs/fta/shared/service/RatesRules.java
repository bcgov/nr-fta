package ca.bc.gov.nrs.fta.shared.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * FTA699's rules that need no database, from {@code THE.FTA_699_RATEFEE} and
 * {@code Fta699RatefeeForm}.
 *
 * <p>The screen shows 12 rates and fees; {@code RANGE_BILL_RATE} stores each of them on two or
 * three rows (one per range file type), 24 rows per calendar year. {@link #ROWS} is the package's
 * list of those rows, in the order its {@code save} writes them.
 */
final class RatesRules {

  /** One of the 12 values the screen shows, with the label legacy gave it. */
  record RateType(String code, String label) {}

  /** One stored row: its key, and which of the 12 values it holds. */
  record Row(String fileType, String rateType, String revenueClass, String value) {}

  /** A stored row as read back, for {@link #fold}. */
  record StoredRow(
      String fileType, String rateType, String revenueClass, BigDecimal rate,
      String updateTimestamp, String updateUserid) {}

  /** What {@code FTA_699_RATEFEE.get} makes of a year's rows. */
  record Fold(
      boolean corrupt, Map<String, BigDecimal> values, String updateTimestamp,
      String updateUserid) {}

  /** The 12 values, in the order fta699Ratefee.jsp lays them out (two columns of six). */
  static final List<RateType> RATE_TYPES = List.of(
      new RateType("GGR", "Grazing Ground Rent Rate"),
      new RateType("GUF", "Grazing Usage Fee Rate"),
      new RateType("GNF", "Grazing Non-Usage Fee Rate - Billable"),
      new RateType("HGR", "Haycutting Ground Rent Rate"),
      new RateType("HUF", "Haycutting Harvest Usage Fee Rate"),
      new RateType("HNF", "Haycutting Harvest Non-Usage Fee Rate - Billable"),
      new RateType("GLF", "Grazing Licence"),
      new RateType("GPF", "Grazing Permit"),
      new RateType("NGI", "Non Replaceable Grazing Permit"),
      new RateType("HLF", "Haycutting Licence"),
      new RateType("HPF", "Haycutting Permit"),
      new RateType("NHI", "Non Replaceable Haycutting Permit"));

  /** The 24 rows {@code save} inserts or updates, in its order (PKS lines 408-548). */
  static final List<Row> ROWS = List.of(
      new Row("E01", "GGR", "RGL", "GGR"),
      new Row("E02", "GGR", "RGP", "GGR"),
      new Row("E03", "GGR", "RNG", "GGR"),
      new Row("E01", "GUF", "RLU", "GUF"),
      new Row("E02", "GUF", "RPU", "GUF"),
      new Row("E03", "GUF", "RNU", "GUF"),
      new Row("E01", "GNF", "RGN", "GNF"),
      new Row("E02", "GNF", "RGN", "GNF"),
      new Row("E03", "GNF", "RGN", "GNF"),
      new Row("H01", "HGR", "RHL", "HGR"),
      new Row("H02", "HGR", "RHP", "HGR"),
      new Row("H03", "HGR", "RNH", "HGR"),
      new Row("H01", "HUF", "RHU", "HUF"),
      new Row("H02", "HUF", "RHF", "HUF"),
      new Row("H03", "HUF", "RNF", "HUF"),
      new Row("H01", "HNF", "RHN", "HNF"),
      new Row("H02", "HNF", "RHN", "HNF"),
      new Row("H03", "HNF", "RHN", "HNF"),
      new Row("E01", "GLF", "RLF", "GLF"),
      new Row("E02", "GPF", "RLF", "GPF"),
      new Row("H01", "HLF", "RLF", "HLF"),
      new Row("H02", "HPF", "RLF", "HPF"),
      new Row("E03", "NGI", "RLF", "NGI"),
      new Row("H03", "NHI", "RLF", "NHI"));

  /**
   * Before 2014 the six licence and permit fees were stored under other rate types and revenue
   * class RTI; {@code get} reads them for those years only.
   */
  private static final List<Row> ROWS_BEFORE_2014 = List.of(
      new Row("E01", "GLI", "RTI", "GLF"),
      new Row("E02", "GPI", "RTI", "GPF"),
      new Row("H01", "HLI", "RTI", "HLF"),
      new Row("H02", "HPI", "RTI", "HPF"),
      new Row("E03", "NGI", "RTI", "NGI"),
      new Row("H03", "NHI", "RTI", "NHI"));

  static final int ROWS_PER_YEAR = 24;

  static final BigDecimal MAX = new BigDecimal("999.99");

  static final String YEAR_REQUIRED = "Calendar Year is mandatory.";
  static final String CORRUPT =
      "Corrupt data has been found in Range Billing Rates table. Please contact system support.";
  static final String MODIFIED =
      "The record you are attempting to update has been modified by another user. "
          + "Please refresh the screen and try again.";

  private static final Pattern NUMBER = Pattern.compile("\\+?(\\d+\\.?\\d*|\\.\\d+)");

  private RatesRules() {}

  static String yearNotListed(int year) {
    return "Calendar Year (" + year + ") is not in the list of billing years.";
  }

  /**
   * {@code Fta699RatefeeForm}'s chain for one value — required, not negative, numeric, between
   * 0.00 and 999.99, at most two decimals — first failure only, keyed by rate type code. Empty
   * when every value passes.
   */
  static Map<String, String> validate(Map<String, String> raw) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (RateType t : RATE_TYPES) {
      String message = check(t.label(), raw == null ? null : raw.get(t.code()));
      if (message != null) {
        errors.put(t.code(), message);
      }
    }
    return errors;
  }

  private static String check(String label, String raw) {
    String v = raw == null ? "" : raw.trim();
    if (v.isEmpty()) {
      return label + " is mandatory.";
    }
    if (v.contains("-")) {
      return "Negative values are not allowed for " + label + ".";
    }
    if (!NUMBER.matcher(v).matches()) {
      return label + " must be numeric.";
    }
    BigDecimal d = new BigDecimal(v);
    if (d.compareTo(MAX) > 0) {
      return label + " field must be between 0.00 and 999.99.";
    }
    int dot = v.indexOf('.');
    if (dot >= 0 && v.length() - dot - 1 > 2) {
      return label + " cannot contain more than 2 places of decimal.";
    }
    return null;
  }

  /** A value that passed {@link #validate}, as stored (NUMBER(5,2)). */
  static BigDecimal parse(String raw) {
    return new BigDecimal(raw.trim()).setScale(2, RoundingMode.UNNECESSARY);
  }

  /**
   * {@code FTA_699_RATEFEE.get}: maps a year's latest rows onto the 12 values. A row that is not
   * one of the year's 24, or a count other than 0 or 24, is corrupt: no values, and Save stays
   * off. No rows at all gives 0.00 for every value (a year not yet entered).
   *
   * @param rows the rows sharing the year's latest update_timestamp, oldest first
   */
  static Fold fold(int year, List<StoredRow> rows) {
    List<Row> licence = year < 2014 ? ROWS_BEFORE_2014 : ROWS.subList(18, 24);
    List<Row> valid = new ArrayList<>(ROWS.subList(0, 18));
    valid.addAll(licence);

    Map<String, BigDecimal> values = new LinkedHashMap<>();
    String ts = null;
    String user = null;
    int count = 0;
    for (StoredRow r : rows) {
      String value = valid.stream()
          .filter(v -> v.fileType().equals(r.fileType())
              && v.rateType().equals(r.rateType())
              && v.revenueClass().equals(r.revenueClass()))
          .map(Row::value)
          .findFirst()
          .orElse(null);
      if (value == null) {
        return new Fold(true, Map.of(), null, null);
      }
      values.put(value, r.rate() == null ? null : r.rate().setScale(2, RoundingMode.HALF_UP));
      count++;
      ts = r.updateTimestamp();
      user = r.updateUserid();
    }
    if (count == 0) {
      Map<String, BigDecimal> zeros = new LinkedHashMap<>();
      RATE_TYPES.forEach(t -> zeros.put(t.code(), new BigDecimal("0.00")));
      return new Fold(false, zeros, null, null);
    }
    if (count != ROWS_PER_YEAR) {
      return new Fold(true, Map.of(), null, null);
    }
    return new Fold(false, values, ts, user);
  }
}
