package ca.bc.gov.nrs.fta.apt.service;

import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitRules.blank;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitRules.commentsProblem;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitRules.wholeProblem;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionRequest;
import java.math.BigDecimal;

/**
 * APT2's checks on a partition and a partition apportionment (APTM2100 and APTM2200), plus the
 * limits of {@code APT_PARTITIONS} and {@code APT_PART_APPORTIONMENTS}. Pure, so unit-tested.
 */
public final class AptPartitionRules {

  private AptPartitionRules() {}

  /**
   * Null when the partition can be saved, else the first problem.
   *
   * @param creating true for a new partition, which must name its management unit history
   */
  public static String partitionProblem(AptPartitionRequest r, boolean creating) {
    if (r == null) {
      return "A partition is required.";
    }
    if (creating && r.muhId() == null) {
      return "The management unit history is required.";
    }
    if (blank(r.partitionCode())) {
      return "Partition Type is required.";
    }
    if (r.partitionCode().trim().length() > 3) {
      return "Partition Type must be a partition type code.";
    }
    return aacProblem(r.allowableAnnualCut(), r.allowableAnnualCutHa(), r.comments());
  }

  /**
   * Null when the apportionment can be saved, else the first problem.
   *
   * @param creating true for a new apportionment, which must name its partition
   */
  public static String apportionmentProblem(AptPartitionApportionmentRequest r,
      boolean creating) {
    if (r == null) {
      return "A partition apportionment is required.";
    }
    if (creating && r.partitionId() == null) {
      return "The partition is required.";
    }
    if (blank(r.foaTypeCode())) {
      return "Form of Agreement is required.";
    }
    if (r.foaTypeCode().trim().length() > 50) {
      return "Form of Agreement must be a form of agreement type code.";
    }
    return aacProblem(r.allowableAnnualCut(), r.allowableAnnualCutHa(), r.comments());
  }

  private static String aacProblem(BigDecimal aac, BigDecimal aacHa, String comments) {
    if (aac == null) {
      return "AAC (m³) is required.";
    }
    String p = wholeProblem("AAC (m³)", aac);
    if (p == null) {
      p = wholeProblem("AAC (ha)", aacHa);
    }
    return p != null ? p : commentsProblem(comments);
  }
}
