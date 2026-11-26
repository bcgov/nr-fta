package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | ArchiveTenuresRules")
class ArchiveTenuresRulesTest {

  @Test
  void regionAndYearAreRequired() {
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(null, null, null)))
        .containsExactly(ArchiveTenuresRules.REGION_REQUIRED, ArchiveTenuresRules.YEAR_REQUIRED);
    assertThat(ArchiveTenuresRules.validate(null))
        .containsExactly(ArchiveTenuresRules.REGION_REQUIRED, ArchiveTenuresRules.YEAR_REQUIRED);
  }

  @Test
  void theDistrictIsOptional() {
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(1L, null, 2024))).isEmpty();
  }

  @Test
  void onlyAYearThatIsOverCanBeArchived() {
    assertThat(ArchiveTenuresRules.yearIsOver(2025, 2026)).isTrue();
    assertThat(ArchiveTenuresRules.yearIsOver(2026, 2026)).isFalse();
    assertThat(ArchiveTenuresRules.yearIsOver(2027, 2026)).isFalse();
  }

  @Test
  void theExpiryYearIsBetween1000And9999() {
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(1L, null, 999)))
        .containsExactly(ArchiveTenuresRules.YEAR_RANGE);
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(1L, null, 10000)))
        .containsExactly(ArchiveTenuresRules.YEAR_RANGE);
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(1L, null, 1000))).isEmpty();
    assertThat(ArchiveTenuresRules.validate(new ArchiveTenuresRequest(1L, null, 9999))).isEmpty();
    assertThat(ArchiveTenuresRules.YEAR_RANGE)
        .isEqualTo("Expiry Year field must be between 1000 and 9999.");
  }

  @Test
  void anExpiredCodeIsNamed() {
    assertThat(ArchiveTenuresRules.expired("District", "DXX"))
        .isEqualTo("District (DXX) is an expired code.");
  }

  @Test
  void theUserMustAdministerEveryAffectedDistrict() {
    Set<String> mine = Set.of("DCC", "DQU");
    assertThat(ArchiveTenuresRules.administersAll(List.of("DCC", "DQU"), mine::contains)).isTrue();
    assertThat(ArchiveTenuresRules.administersAll(List.of("DCC", "DKA"), mine::contains)).isFalse();
    assertThat(ArchiveTenuresRules.administersAll(List.of(), mine::contains)).isTrue();
  }
}
