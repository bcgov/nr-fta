package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRowRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the rotation tabs' field checks and calculations, ported from the legacy forms and
 * actions with their message texts.
 */
@DisplayName("Unit Test | RotationsFieldChecks")
class RotationsFieldChecksTest {

  @Nested
  @DisplayName("integer fields")
  class Integers {

    @Test
    @DisplayName("required, integer and range messages are legacy's")
    void messages() {
      List<String> e = new ArrayList<>();
      assertThat(RotationsFieldChecks.integer(" ", "No.", "Livestock Count", 0, 9999, e)).isNull();
      assertThat(RotationsFieldChecks.integer("x", "No.", "Livestock Count", 0, 9999, e)).isNull();
      assertThat(RotationsFieldChecks.integer("10000", "No.", "Livestock Count", 0, 9999, e))
          .isNull();
      assertThat(e).containsExactly(
          "No. is mandatory.",
          "Livestock Count must be an integer.",
          "Livestock Count field must be between 0 and 9999.");
    }

    @Test
    @DisplayName("an optional blank passes; a good value parses")
    void ok() {
      List<String> e = new ArrayList<>();
      assertThat(RotationsFieldChecks.integer("", null, "TTL AUMs", 0, 99999, e)).isNull();
      assertThat(RotationsFieldChecks.integer(" 42 ", null, "TTL AUMs", 0, 99999, e)).isEqualTo(42);
      assertThat(e).isEmpty();
    }
  }

  @Nested
  @DisplayName("MM-DD rotation days")
  class MonthDays {

    @Test
    @DisplayName("a real day of the year parses")
    void ok() {
      assertThat(RotationsFieldChecks.monthDay("05-15", 2024)).isEqualTo(LocalDate.of(2024, 5, 15));
      assertThat(RotationsFieldChecks.monthDay("02-29", 2024)).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    @DisplayName("bad shapes and days that do not exist that year fail")
    void bad() {
      assertThat(RotationsFieldChecks.monthDay("02-29", 2023)).isNull();
      assertThat(RotationsFieldChecks.monthDay("13-01", 2024)).isNull();
      assertThat(RotationsFieldChecks.monthDay("5-15", 2024)).isNull();
      assertThat(RotationsFieldChecks.monthDay("2024-05-15", 2024)).isNull();
      assertThat(RotationsFieldChecks.monthDay(null, 2024)).isNull();
    }
  }

  @Nested
  @DisplayName("TTL AUMs (setTTLAUM)")
  class Aums {

    @Test
    @DisplayName("head × (days + 1) ÷ 30.43685, rounded")
    void cattle() {
      // 100 head, May 1 – Jun 30: 60 days apart, counted 61.
      assertThat(RotationsFieldChecks.aums(
          "CA", 100, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 6, 30))).isEqualTo(200);
    }

    @Test
    @DisplayName("sheep count a quarter")
    void sheep() {
      assertThat(RotationsFieldChecks.aums(
          "SH", 100, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 6, 30))).isEqualTo(50);
    }

    @Test
    @DisplayName("a same-day rotation is 0 days, as legacy counted it")
    void sameDay() {
      assertThat(RotationsFieldChecks.aums(
          "CA", 100, LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 1))).isZero();
    }
  }

  @Nested
  @DisplayName("hay grid")
  class Hay {

    private HayRowRequest row(String block, String ru, String meadow, String harvest) {
      return new HayRowRequest(null, null, false, block, ru, meadow, harvest);
    }

    @Test
    @DisplayName("required fields are listed by row; blank and deleted rows are skipped")
    void required() {
      List<HayRowRequest> rows = List.of(
          row("1", null, "NORTH", "10"),
          row(null, null, null, null),
          new HayRowRequest(5L, 1L, true, null, null, null, null),
          row(null, "RU1", null, "x"));
      assertThat(RotationsFieldChecks.hayRowErrors(rows, i -> true)).containsExactly(
          "Range Unit is required on row 1.",
          "Meadow Name is required on row 4.",
          "Permit Block is required on row 4.",
          "Auth Harvest must be an integer. Check row 4.");
    }

    @Test
    @DisplayName("range and lengths; lengths only on changed rows")
    void rangeAndLength() {
      List<HayRowRequest> rows = List.of(
          row("12345", "RU1", "A", "100000"),
          row("12345", "RU1", "B", "5"));
      assertThat(RotationsFieldChecks.hayRowErrors(rows, i -> i == 0)).containsExactly(
          "Auth Harvest field must be in the range [0,99999]. Check row 1.",
          "Permit Block can be at most 4 characters. Check row 1.");
    }

    @Test
    @DisplayName("harvest plus non-use must equal the authorized tonnes")
    void total() {
      assertThat(RotationsFieldChecks.hayTotalError(120, 120)).isNull();
      assertThat(RotationsFieldChecks.hayTotalError(110, 120))
          .startsWith("The total harvest plus the non-use (110) must equal the Authorized");
    }

    @Test
    @DisplayName("an unparseable harvest counts 0 in the total, as legacy's")
    void harvestOrZero() {
      assertThat(RotationsFieldChecks.harvestOrZero("x")).isZero();
      assertThat(RotationsFieldChecks.harvestOrZero(" 7 ")).isEqualTo(7);
    }
  }
}
