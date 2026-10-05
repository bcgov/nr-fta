package ca.bc.gov.nrs.fta.tenure.tab.aac;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacRow;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacSaveRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA930 gating and field checks: the page enables its buttons by these rules and
 * the endpoints enforce them, so a regression here lets a write through that legacy refused.
 */
@DisplayName("Unit Test | AacRules and AacFieldChecks")
class AacRulesTest {

  private static final LocalDate D2020 = LocalDate.of(2020, 4, 1);
  private static final LocalDate D2022 = LocalDate.of(2022, 4, 1);

  private static AacRow row(long amountId, long periodId, LocalDate date, String unit,
      String area, String cut) {
    return new AacRow(amountId, periodId, date, unit, area, "", cut, "", new BigDecimal("100"),
        "CHG", "", "U", null, null, "U1", "U1", 1, 1);
  }

  /** Most recent period first, as the GET returns them. */
  private static final List<AacRow> ROWS = List.of(
      row(3, 20, D2022, "M3", "B", "C"),
      row(4, 20, D2022, "M3", "B", "D"),
      row(1, 10, D2020, "M3", "B", "C"));

  private static AacSaveRequest req(LocalDate date, String unit, String area, String cut,
      String amount, String reason, String comment, String shareable, String fra) {
    return new AacSaveRequest(date, unit, area, cut,
        amount == null ? null : new BigDecimal(amount), reason, comment, shareable,
        fra == null ? null : new BigDecimal(fra), null, null);
  }

  private static AacSaveRequest ok(LocalDate date) {
    return req(date, "M3", "B", "E", "500", "CHG", null, null, null);
  }

  private static List<String> check(AacSaveRequest q, AacRow editing, String fileType) {
    return AacFieldChecks.save(q, ROWS, editing, LocalDate.of(2010, 1, 1),
        LocalDate.of(2030, 1, 1), fileType);
  }

  @Nested
  @DisplayName("rules")
  class Rules {

    @Test
    void onlyFta930FileTypesApply() {
      AacRules r = AacRules.of("A06", true);
      assertThat(r.validFileType()).isFalse();
      assertThat(r.edit()).isFalse();
      assertThat(r.areas()).isFalse();
      assertThat(r.editReason())
          .isEqualTo("The File Type (A06) associated with the queried File is invalid for this"
              + " screen.");
    }

    @Test
    void needsATimberTenureRecord() {
      AacRules r = AacRules.of("A01", false);
      assertThat(r.validFileType()).isTrue();
      assertThat(r.edit()).isFalse();
      assertThat(r.areas()).isFalse();
      assertThat(r.areasReason()).contains("timber tenure");
    }

    @Test
    void headquartersMaySave() {
      AacRules r = AacRules.of("A29", true);
      assertThat(r.edit()).isTrue();
      assertThat(r.areas()).isTrue();
      assertThat(r.editReason()).isNull();
      assertThat(r.scheduleAAllowed()).isTrue();
      assertThat(AacRules.of("A01", true).scheduleAAllowed()).isFalse();
    }
  }

  @Nested
  @DisplayName("field checks")
  class Checks {

    @Test
    void aValidNewRowPasses() {
      assertThat(check(ok(D2022), null, "A01")).isEmpty();
    }

    @Test
    void requiredFields() {
      assertThat(check(req(null, null, null, null, null, null, null, null, null), null, "A01"))
          .containsExactly("Effective Date is mandatory.", "Amount is mandatory.",
              "Area Type is mandatory.", "Cut type is mandatory.",
              "Unit of Measure is mandatory.", "Reason is mandatory.");
    }

    @Test
    void dateWithinTheTenure() {
      assertThat(check(ok(LocalDate.of(2009, 12, 31)), null, "A01"))
          .contains("AAC Effective Date must be after Tenure Effective Date.");
      assertThat(check(ok(LocalDate.of(2010, 1, 1)), null, "A01")).isEmpty();
      assertThat(check(ok(LocalDate.of(2030, 1, 2)), null, "A01"))
          .contains("Effective Date cannot be after the tenure Expiry Date");
    }

    @Test
    void aChangedPeriodCannotPassTheNextOne() {
      AacRow older = ROWS.get(2);
      assertThat(check(ok(LocalDate.of(2023, 1, 1)), older, "A01"))
          .contains("Effective Date value cannot exceed 2022-04-01");
      assertThat(check(ok(LocalDate.of(2021, 1, 1)), older, "A01")).isEmpty();
      // The most recent period has none after it.
      assertThat(check(ok(LocalDate.of(2025, 1, 1)), ROWS.get(1), "A01")).isEmpty();
    }

    @Test
    void aNewRowTakesItsPeriodsUnit() {
      assertThat(check(req(D2022, "HA", "B", "E", "5", "CHG", null, null, null), null, "A01"))
          .contains("Unit of measure entered for the period 2022-04-01 must match its existing"
              + " M3 units");
    }

    @Test
    void duplicateKeyOnlyForNewRows() {
      AacSaveRequest dup = req(D2022, "M3", "B", "C", "5", "CHG", null, null, null);
      assertThat(check(dup, null, "A01"))
          .contains("A record already exists with the same effective date, area type and cut"
              + " type");
      assertThat(check(dup, ROWS.get(0), "A01")).isEmpty();
    }

    @Test
    void otherNeedsAComment() {
      assertThat(check(req(D2022, "M3", "B", "E", "5", "OTH", null, null, null), null, "A01"))
          .contains("Comment is mandatory when Reason is OTH-Other");
    }

    @Test
    void revenueShareAndFraVolume() {
      assertThat(check(req(D2022, "M3", "B", "E", "5", "CHG", null, "Y", null), null, "A01"))
          .contains("FRA(Bill 28,2003)m3 is mandatory when Revenue Share is Yes.");
      assertThat(check(req(D2022, "M3", "B", "E", "5", "CHG", null, "N", "1"), null, "A01"))
          .contains("FRA(Bill 28,2003)m3 must be blank unless Revenue Share is Yes.");
      assertThat(check(req(D2022, "M3", "B", "E", "5", "CHG", null, "Y", "6"), null, "A01"))
          .anyMatch(m -> m.contains("cannot be more than the Amount"));
      assertThat(check(req(D2020, "HA", "B", "E", "5", "CHG", null, "Y", "6"), null, "A01"))
          .noneMatch(m -> m.contains("cannot be more than the Amount"));
    }

    @Test
    void amountLimits() {
      assertThat(check(req(D2022, "M3", "B", "E", "1.12345", "CHG", null, null, null), null,
          "A01")).contains("No more than four decimal places are permitted for Amount");
      assertThat(check(req(D2022, "M3", "B", "E", "-1", "CHG", null, null, null), null, "A01"))
          .contains("Amount field must be between 0.0 and 9999999.9999.");
    }

    @Test
    void scheduleAOnlyForSomeFileTypes() {
      AacSaveRequest a = req(D2022, "M3", "A", "E", "5", "CHG", null, null, null);
      assertThat(check(a, null, "A01")).contains(AacRules.SCHEDULE_A_MESSAGE);
      assertThat(check(a, null, "A02")).doesNotContain(AacRules.SCHEDULE_A_MESSAGE);
    }

    @Test
    void areas() {
      assertThat(AacFieldChecks.areas(null, new BigDecimal("12.5"))).isEmpty();
      assertThat(AacFieldChecks.areas(new BigDecimal("1.00001"), new BigDecimal("10000000")))
          .containsExactly("No more than four decimal places are permitted for Private/Schedule A",
              "Crown/Schedule B field must be between 0.0 and 9999999.9999.");
    }
  }
}
