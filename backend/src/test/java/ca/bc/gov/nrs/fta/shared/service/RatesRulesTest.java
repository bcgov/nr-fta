package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.shared.service.RatesRules.Fold;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Row;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.StoredRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RatesRules (FTA699)")
class RatesRulesTest {

  static Map<String, String> allValid() {
    Map<String, String> m = new HashMap<>();
    RatesRules.RATE_TYPES.forEach(t -> m.put(t.code(), "1.25"));
    return m;
  }

  private static final String TS = "2025-01-02 03:04:05";

  static List<StoredRow> stored(List<Row> rows) {
    return rows.stream()
        .map(r -> new StoredRow(r.fileType(), r.rateType(), r.revenueClass(),
            new BigDecimal("2.5"), TS, "IDIR\\JSMITH"))
        .toList();
  }

  @Test
  void twelveLabelledValuesInTheScreensOrder() {
    assertThat(RatesRules.RATE_TYPES).extracting(RatesRules.RateType::code)
        .containsExactly("GGR", "GUF", "GNF", "HGR", "HUF", "HNF",
            "GLF", "GPF", "NGI", "HLF", "HPF", "NHI");
  }

  @Test
  void theTwentyFourRowsAreThePackagesTuples() {
    assertThat(RatesRules.ROWS).hasSize(24);
    assertThat(new HashSet<>(RatesRules.ROWS)).hasSize(24);
    assertThat(RatesRules.ROWS).startsWith(
        new Row("E01", "GGR", "RGL", "GGR"),
        new Row("E02", "GGR", "RGP", "GGR"),
        new Row("E03", "GGR", "RNG", "GGR"));
    assertThat(RatesRules.ROWS).contains(
        new Row("H03", "HUF", "RNF", "HUF"),
        new Row("E02", "GNF", "RGN", "GNF"),
        new Row("H02", "HNF", "RHN", "HNF"));
    assertThat(RatesRules.ROWS).endsWith(
        new Row("E01", "GLF", "RLF", "GLF"),
        new Row("E02", "GPF", "RLF", "GPF"),
        new Row("H01", "HLF", "RLF", "HLF"),
        new Row("H02", "HPF", "RLF", "HPF"),
        new Row("E03", "NGI", "RLF", "NGI"),
        new Row("H03", "NHI", "RLF", "NHI"));
    // Each value fans out to all of its file types: 3 rows for the six rates, 1 for each fee.
    Map<String, Long> perValue = RatesRules.ROWS.stream()
        .collect(Collectors.groupingBy(Row::value, Collectors.counting()));
    assertThat(perValue).containsEntry("GGR", 3L).containsEntry("HNF", 3L)
        .containsEntry("GLF", 1L).containsEntry("NHI", 1L).hasSize(12);
  }

  @Test
  void validValuesPass() {
    assertThat(RatesRules.validate(allValid())).isEmpty();
    Map<String, String> m = allValid();
    m.put("GGR", "0");
    m.put("GUF", "999.99");
    m.put("GNF", ".5");
    m.put("HGR", "12.");
    assertThat(RatesRules.validate(m)).isEmpty();
  }

  @Test
  void blankIsMandatoryNotZero() {
    Map<String, String> m = allValid();
    m.put("GGR", " ");
    m.remove("NHI");
    assertThat(RatesRules.validate(m)).containsOnly(
        Map.entry("GGR", "Grazing Ground Rent Rate is mandatory."),
        Map.entry("NHI", "Non Replaceable Haycutting Permit is mandatory."));
  }

  @Test
  void negativeNonNumericRangeAndDecimals() {
    Map<String, String> m = allValid();
    m.put("GGR", "-1");
    m.put("GUF", "abc");
    m.put("GNF", "1000");
    m.put("HGR", "1.234");
    m.put("HUF", "1e2");
    assertThat(RatesRules.validate(m)).containsExactly(
        Map.entry("GGR", "Negative values are not allowed for Grazing Ground Rent Rate."),
        Map.entry("GUF", "Grazing Usage Fee Rate must be numeric."),
        Map.entry("GNF",
            "Grazing Non-Usage Fee Rate - Billable field must be between 0.00 and 999.99."),
        Map.entry("HGR",
            "Haycutting Ground Rent Rate cannot contain more than 2 places of decimal."),
        Map.entry("HUF", "Haycutting Harvest Usage Fee Rate must be numeric."));
  }

  @Test
  void nullMapReportsEveryValue() {
    assertThat(RatesRules.validate(null)).hasSize(12);
  }

  @Test
  void parseKeepsTwoDecimals() {
    assertThat(RatesRules.parse(" 3.5 ")).isEqualByComparingTo("3.50");
    assertThat(RatesRules.parse("3.5").scale()).isEqualTo(2);
  }

  @Test
  void anEmptyYearShowsZerosAndNoTimestamp() {
    Fold f = RatesRules.fold(2027, List.of());
    assertThat(f.corrupt()).isFalse();
    assertThat(f.updateTimestamp()).isNull();
    assertThat(f.values()).hasSize(12).allSatisfy((k, v) -> assertThat(v).isZero());
  }

  @Test
  void aCompleteYearFoldsToTwelveValuesWithItsTimestamp() {
    Fold f = RatesRules.fold(2025, stored(RatesRules.ROWS));
    assertThat(f.corrupt()).isFalse();
    assertThat(f.updateTimestamp()).isEqualTo(TS);
    assertThat(f.updateUserid()).isEqualTo("IDIR\\JSMITH");
    assertThat(f.values()).hasSize(12);
    assertThat(f.values().get("GGR")).isEqualByComparingTo("2.50");
  }

  @Test
  void anIncompleteYearIsCorrupt() {
    Fold f = RatesRules.fold(2025, stored(RatesRules.ROWS.subList(0, 23)));
    assertThat(f.corrupt()).isTrue();
    assertThat(f.values()).isEmpty();
    assertThat(f.updateTimestamp()).isNull();
  }

  @Test
  void anUnknownRowIsCorrupt() {
    List<Row> rows = new ArrayList<>(RatesRules.ROWS.subList(0, 23));
    rows.add(new Row("H03", "NHI", "XXX", "NHI"));
    assertThat(RatesRules.fold(2025, stored(rows)).corrupt()).isTrue();
  }

  @Test
  void before2014TheFeesWereStoredUnderRti() {
    List<Row> rows = new ArrayList<>(RatesRules.ROWS.subList(0, 18));
    rows.add(new Row("E01", "GLI", "RTI", "GLF"));
    rows.add(new Row("E02", "GPI", "RTI", "GPF"));
    rows.add(new Row("H01", "HLI", "RTI", "HLF"));
    rows.add(new Row("H02", "HPI", "RTI", "HPF"));
    rows.add(new Row("E03", "NGI", "RTI", "NGI"));
    rows.add(new Row("H03", "NHI", "RTI", "NHI"));
    assertThat(RatesRules.fold(2013, stored(rows)).corrupt()).isFalse();
    assertThat(RatesRules.fold(2014, stored(rows)).corrupt()).isTrue();
    assertThat(RatesRules.fold(2013, stored(RatesRules.ROWS)).corrupt()).isTrue();
  }
}
