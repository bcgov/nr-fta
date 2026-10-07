package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The field checks of a new cut block — FTA904's "Save" validators that apply in add mode
 * ({@code Fta904CutblkdetailForm}), with legacy's message texts. The checks that need the
 * database (the File/CP/Block combination, an expired fire code) are the write service's.
 */
final class CutBlocksFieldChecks {

  private CutBlocksFieldChecks() {}

  /** The JSP's {@code maxlength} on Cut Block. */
  static final int MAX_BLOCK_ID = 10;
  /** {@code block120}: Block Description, 120 characters. */
  static final int MAX_DESCRIPTION = 120;
  /** FTA904's FloatRangeValidator bound on both planned areas. */
  static final BigDecimal MAX_AREA = new BigDecimal("9999999.9999");

  private static final Set<String> YES_NO = Set.of("Y", "N");

  /**
   * Legacy's add-mode checks, in its order; empty when the block is acceptable.
   *
   * @param q               the request (its ids already trimmed and upper-cased)
   * @param fileTypeCode    the tenure's file type
   * @param salvageTypeCode the chosen permit's salvage type
   */
  static List<String> problems(
      CutBlocksDtos.CutBlockCreateRequest q, String fileTypeCode, String salvageTypeCode) {
    List<String> e = new ArrayList<>();
    String blockId = q.cutBlockId();
    if (blockId == null || blockId.isBlank()) {
      e.add("Cut Block is mandatory.");
    } else if (blockId.length() > MAX_BLOCK_ID) {
      e.add("Cut Block must not exceed " + MAX_BLOCK_ID + " characters.");
    }
    BigDecimal gross = q.plannedGrossArea();
    BigDecimal net = q.plannedNetArea();
    area(e, gross, "Planned Gross Area (ha)");
    area(e, net, "Planned Net Area (ha)");
    if (q.description() != null && q.description().length() > MAX_DESCRIPTION) {
      e.add("Block Description must not exceed " + MAX_DESCRIPTION + " characters.");
    }
    if (q.spExempt() == null || q.spExempt().isBlank()) {
      e.add("SP Exempt is mandatory.");
    } else if (!YES_NO.contains(q.spExempt())) {
      e.add("SP Exempt must be Y or N.");
    }
    if (q.wasteAssessmentRequired() != null && !YES_NO.contains(q.wasteAssessmentRequired())) {
      e.add("Waste Assessment Required must be Y or N.");
    }
    if (q.underPartitionOrder() != null && !YES_NO.contains(q.underPartitionOrder())) {
      e.add("Under Partition Order must be Y or N.");
    }
    // areaLessThanOneGross / areaLessThanOneNet: salvage blocks are small. Legacy's BBR
    // gross check is ">= 1" though its text says "cannot be greater than"; kept as legacy.
    if ("SSS".equals(salvageTypeCode) || "B07".equals(fileTypeCode)) {
      if (gross != null && gross.compareTo(BigDecimal.ONE) >= 0) {
        e.add("For salvage blocks the planned gross area must be less than one hectare.");
      }
      if (net != null && net.compareTo(BigDecimal.ONE) >= 0) {
        e.add("For salvage blocks the planned net area must be less than one hectare.");
      }
    } else if ("BBR".equals(salvageTypeCode)) {
      if (gross != null && gross.compareTo(BigDecimal.ONE) >= 0) {
        e.add("For salvage blocks the planned gross area cannot be greater than 1 hectare.");
      }
      if (net != null && net.compareTo(BigDecimal.ONE) > 0) {
        e.add("For salvage blocks the planned net area cannot be greater than 1 hectares.");
      }
    }
    if (gross != null && net != null && gross.compareTo(net) < 0) {
      e.add("Planned net area must be less than or equal to planned gross area.");
    }
    return e;
  }

  private static void area(List<String> e, BigDecimal v, String label) {
    if (v == null) {
      e.add(label + " is mandatory.");
    } else if (v.signum() < 0 || v.compareTo(MAX_AREA) > 0) {
      e.add(label + " field must be between 0 and 9999999.9999.");
    }
    // More than 4 decimals is not an error: the insert truncates to 4, as legacy's does.
  }
}
