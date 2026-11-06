package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | AptPartitionRules")
class AptPartitionRulesTest {

  private static AptPartitionRequest partition(Long muhId, String code, BigDecimal aac,
      BigDecimal aacHa) {
    return new AptPartitionRequest(muhId, code, aac, aacHa, null);
  }

  private static AptPartitionApportionmentRequest apportionment(Long partId, String foa,
      BigDecimal aac) {
    return new AptPartitionApportionmentRequest(partId, foa, aac, null, null);
  }

  @Test
  void aCompletePartitionPasses() {
    assertThat(AptPartitionRules.partitionProblem(partition(1L, "C", BigDecimal.TEN, null), true))
        .isNull();
  }

  @Test
  void aNewPartitionNamesItsDeterminationButAnUpdateNeedNot() {
    assertThat(AptPartitionRules.partitionProblem(partition(null, "C", BigDecimal.TEN, null),
        true)).contains("management unit history");
    assertThat(AptPartitionRules.partitionProblem(partition(null, "C", BigDecimal.TEN, null),
        false)).isNull();
  }

  @Test
  void aPartitionNeedsTypeAndWholeAac() {
    assertThat(AptPartitionRules.partitionProblem(partition(1L, "", BigDecimal.TEN, null), true))
        .startsWith("Partition Type");
    assertThat(AptPartitionRules.partitionProblem(partition(1L, "C", null, null), true))
        .startsWith("AAC (m³) is required");
    assertThat(AptPartitionRules.partitionProblem(
        partition(1L, "C", new BigDecimal("0.5"), null), true)).contains("whole");
    assertThat(AptPartitionRules.partitionProblem(
        partition(1L, "C", BigDecimal.ONE, new BigDecimal("-3")), true)).startsWith("AAC (ha)");
  }

  @Test
  void anApportionmentNeedsPartitionFoaAndAac() {
    assertThat(AptPartitionRules.apportionmentProblem(apportionment(1L, "A01", BigDecimal.ONE),
        true)).isNull();
    assertThat(AptPartitionRules.apportionmentProblem(apportionment(null, "A01", BigDecimal.ONE),
        true)).contains("partition");
    assertThat(AptPartitionRules.apportionmentProblem(apportionment(1L, null, BigDecimal.ONE),
        true)).startsWith("Form of Agreement");
    assertThat(AptPartitionRules.apportionmentProblem(apportionment(1L, "A01", null), true))
        .startsWith("AAC (m³) is required");
    assertThat(AptPartitionRules.apportionmentProblem(
        apportionment(1L, "A01", new BigDecimal("1000000000")), true)).contains("999,999,999");
  }

  @Test
  void commentsAreCapped() {
    assertThat(AptPartitionRules.apportionmentProblem(new AptPartitionApportionmentRequest(1L,
        "A01", BigDecimal.ONE, null, "x".repeat(2001)), true)).contains("2000");
  }
}
