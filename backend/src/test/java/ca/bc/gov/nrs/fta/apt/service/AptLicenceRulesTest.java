package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.apt.dto.AptLicenceDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceUpdateRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | AptLicenceRules")
class AptLicenceRulesTest {

  @Test
  void theFoaTypeIsRequired() {
    assertThat(AptLicenceRules.problem(null)).isEqualTo("FOA Type is required.");
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest(" ", null, null, null)))
        .isEqualTo("FOA Type is required.");
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("TOOLONG", null, null, null)))
        .isNotNull();
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("A", null, null, null)))
        .isNull();
  }

  @Test
  void theReplacementTermIsZeroTo999() {
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("A", null, -1, null)))
        .contains("999");
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("A", null, 1000, null)))
        .contains("999");
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("A", null, 0, null))).isNull();
    assertThat(AptLicenceRules.problem(new AptLicenceUpdateRequest("A", null, 999, null)))
        .isNull();
  }

  @Test
  void commentsFitTheColumn() {
    assertThat(AptLicenceRules.problem(
        new AptLicenceUpdateRequest("A", null, null, "x".repeat(2000)))).isNull();
    assertThat(AptLicenceRules.problem(
        new AptLicenceUpdateRequest("A", null, null, "x".repeat(2001)))).contains("2000");
  }

  @Test
  void aLicenceWithTwoClientsIsListedOnce() {
    AptLicenceDto a = row("A1", "First");
    AptLicenceDto b = row("A1", "Second");
    AptLicenceDto c = row("A2", "First");
    assertThat(AptLicenceRules.distinctByForestFileId(List.of(a, b, c))).containsExactly(a, c);
  }

  private static AptLicenceDto row(String ffid, String client) {
    return new AptLicenceDto(null, null, null, null, null, null, null, ffid, null, null, null,
        null, null, client, null, null, null, null, null, null, null, null, null);
  }
}
