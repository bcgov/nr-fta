package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | AptManagementUnitRules")
class AptManagementUnitRulesTest {

  private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

  private static AptManagementUnitRequest unit(Long org, String type, String no, String status,
      LocalDate date, String orcs) {
    return new AptManagementUnitRequest(org, type, no, status, date, null, orcs, null);
  }

  private static AptManagementUnitHistoryRequest history(BigDecimal aac, BigDecimal aacHa,
      BigDecimal area) {
    return new AptManagementUnitHistoryRequest(null, DAY, DAY, aac, aacHa, null, null, null,
        area, null, null, null, null, null);
  }

  @Test
  void aCompleteUnitPasses() {
    assertThat(AptManagementUnitRules.managementUnitProblem(
        unit(1L, "U", "01", "ACT", DAY, "12345"))).isNull();
  }

  @Test
  void aUnitNeedsItsRequiredFields() {
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(null, "U", "01", "A", DAY, null)))
        .startsWith("Org Unit");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, " ", "01", "A", DAY, null)))
        .startsWith("Management Unit Type");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "U", null, "A", DAY, null)))
        .startsWith("Management Unit Number");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "U", "01", "", DAY, null)))
        .startsWith("Status");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "U", "01", "A", null, null)))
        .startsWith("Status Effective Date");
  }

  @Test
  void aUnitFitsItsColumns() {
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "TF", "01", "A", DAY, null)))
        .contains("single character");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "U", "12345", "A", DAY,
        null))).contains("at most 4");
    assertThat(AptManagementUnitRules.managementUnitProblem(unit(1L, "U", "01", "A", DAY,
        "12345678901234"))).contains("at most 13");
    assertThat(AptManagementUnitRules.managementUnitProblem(new AptManagementUnitRequest(1L, "U",
        "01", "A", DAY, null, null, "x".repeat(2001)))).contains("2000");
  }

  @Test
  void aDeterminationNeedsItsDatesAndAac() {
    assertThat(AptManagementUnitRules.historyProblem(history(BigDecimal.TEN, null, null)))
        .isNull();
    assertThat(AptManagementUnitRules.historyProblem(history(null, null, null)))
        .startsWith("AAC (m³) is required");
    assertThat(AptManagementUnitRules.historyProblem(new AptManagementUnitHistoryRequest(null,
        null, DAY, BigDecimal.ONE, null, null, null, null, null, null, null, null, null, null)))
        .startsWith("AAC Determination Date");
    assertThat(AptManagementUnitRules.historyProblem(new AptManagementUnitHistoryRequest(null,
        DAY, null, BigDecimal.ONE, null, null, null, null, null, null, null, null, null, null)))
        .startsWith("Effective Date");
  }

  @Test
  void aacIsWholeAndInRange() {
    assertThat(AptManagementUnitRules.historyProblem(history(new BigDecimal("1.5"), null, null)))
        .contains("whole number");
    assertThat(AptManagementUnitRules.historyProblem(history(new BigDecimal("2.00"), null, null)))
        .isNull();
    assertThat(AptManagementUnitRules.historyProblem(history(new BigDecimal("-1"), null, null)))
        .contains("between 0 and 999,999,999");
    assertThat(AptManagementUnitRules.historyProblem(
        history(BigDecimal.ONE, new BigDecimal("1000000000"), null))).startsWith("AAC (ha)");
  }

  @Test
  void areasTakeTwoDecimals() {
    assertThat(AptManagementUnitRules.historyProblem(
        history(BigDecimal.ONE, null, new BigDecimal("9999999.99")))).isNull();
    assertThat(AptManagementUnitRules.historyProblem(
        history(BigDecimal.ONE, null, new BigDecimal("10000000")))).startsWith("Total Area");
    assertThat(AptManagementUnitRules.historyProblem(
        history(BigDecimal.ONE, null, new BigDecimal("1.234")))).contains("2 decimal places");
  }

  @Test
  void anUnassignedCodeSplitsAfterItsOneCharacterType() {
    assertThat(AptManagementUnitRules.splitUnassignedCode("U01")).containsExactly("U", "01");
    assertThat(AptManagementUnitRules.splitUnassignedCode("T39")).containsExactly("T", "39");
    assertThat(AptManagementUnitRules.splitUnassignedCode("FN12")).containsExactly("F", "N12");
    assertThat(AptManagementUnitRules.splitUnassignedCode("Z")).containsExactly("Z", null);
    assertThat(AptManagementUnitRules.splitUnassignedCode(null)).containsExactly(null, null);
  }

  @Test
  void aUnitIdMustBePositive() {
    AptManagementUnitRules.requirePositiveId(1L);
    for (Long bad : new Long[] {null, 0L, -5L}) {
      assertThatThrownBy(() -> AptManagementUnitRules.requirePositiveId(bad))
          .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getReason()).isEqualTo("Management Unit ID must be a positive integer.");
          });
    }
  }
}
