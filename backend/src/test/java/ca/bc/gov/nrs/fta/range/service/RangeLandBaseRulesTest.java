package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeLandBaseRules.Codes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeLandBaseRules (Fta615RangeLandBaseForm)")
class RangeLandBaseRulesTest {

  static final Codes CODES = new Codes(Set.of("PL", "CG"), Set.of("HAY"), null, null);

  static SaveRequest request(String description, String pid, String type, String fenced,
      String start, String end) {
    return new SaveRequest(description, pid, "lot 1", type, "HAY", fenced, null, null, null,
        start, end, null);
  }

  static SaveRequest valid() {
    return request("Lot 1 Plan 123", "012345678", "PL", "12.5", "2025-01-01", "2025-12-31");
  }

  @Test
  void aCompleteRecordPasses() {
    assertThat(RangeLandBaseRules.validateSave(valid(), CODES)).isEmpty();
  }

  @Test
  void onlyTheOwnershipTypeIsRequired() {
    assertThat(RangeLandBaseRules.validateSave(request(null, null, "PL", null, null, null),
        CODES)).isEmpty();
    assertThat(RangeLandBaseRules.validateSave(request(null, null, "", null, null, null), CODES))
        .containsExactly(RangeLandBaseRules.OWNERSHIP_REQUIRED);
  }

  @Test
  void aPidNeedsALegalDescription() {
    assertThat(RangeLandBaseRules.validateSave(
        request(" ", "012345678", "PL", null, null, null), CODES))
        .containsExactly(RangeLandBaseRules.DESCRIPTION_WITH_PID);
  }

  @Test
  void aPidIsExactlyNineDigits() {
    assertThat(RangeLandBaseRules.validateSave(request("d", "12345678", "PL", null, null, null),
        CODES)).containsExactly(RangeLandBaseRules.PID_LENGTH);
    assertThat(RangeLandBaseRules.validateSave(request("d", "012-345-6", "PL", null, null, null),
        CODES)).containsExactly(RangeLandBaseRules.PID_INTEGER);
  }

  @Test
  void areasAreNumbersFrom0To999999Point9WithOneDecimalPlace() {
    assertThat(RangeLandBaseRules.validateSave(request("d", null, "PL", "abc", null, null),
        CODES)).containsExactly("Fenced pasture must be numeric.");
    assertThat(RangeLandBaseRules.validateSave(request("d", null, "PL", "1000000", null, null),
        CODES)).containsExactly("Fenced pasture field must be between 0 and 999999.9.");
    assertThat(RangeLandBaseRules.validateSave(request("d", null, "PL", "1.25", null, null),
        CODES)).containsExactly("Only one decimal-place is permitted for Fenced pasture");
    assertThat(RangeLandBaseRules.validateSave(request("d", null, "PL", "1.50", null, null),
        CODES)).isEmpty();
  }

  @Test
  void leaseDatesGoTogetherAndInOrder() {
    assertThat(RangeLandBaseRules.validateSave(
        request("d", null, "PL", null, "2025-01-01", null), CODES))
        .containsExactly(RangeLandBaseRules.LEASE_TOGETHER);
    assertThat(RangeLandBaseRules.validateSave(
        request("d", null, "PL", null, "2025-02-01", "2025-01-01"), CODES))
        .containsExactly(RangeLandBaseRules.LEASE_ORDER);
    assertThat(RangeLandBaseRules.validateSave(
        request("d", null, "PL", null, "2025-01-01", "2025-01-01"), CODES)).isEmpty();
  }

  @Test
  void anExpiredCodeIsRefusedUnlessTheRecordHoldsIt() {
    SaveRequest r = request("d", null, "OLD", null, null, null);
    assertThat(RangeLandBaseRules.validateSave(r, CODES))
        .containsExactly("Ownership Type (OLD) is an expired code.");
    assertThat(RangeLandBaseRules.validateSave(r,
        new Codes(Set.of(), Set.of("HAY"), "OLD", null))).isEmpty();
  }

  @Test
  void removeNeedsAComment() {
    assertThat(RangeLandBaseRules.validateRemove("  "))
        .containsExactly(RangeLandBaseRules.REMOVE_COMMENT_REQUIRED);
    assertThat(RangeLandBaseRules.validateRemove("sold")).isEmpty();
  }

  @Test
  void parseUpperCasesThePidAndIdAndReadsTheValues() {
    RangeLandBaseRules.Parsed p = RangeLandBaseRules.parse(valid());
    assertThat(p.landBaseId()).isEqualTo("LOT 1");
    assertThat(p.fencedPasture()).isEqualByComparingTo(new BigDecimal("12.5"));
    assertThat(p.leaseStartDate()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(p.unfencedPasture()).isNull();
  }
}
