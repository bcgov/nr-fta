package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
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
}
