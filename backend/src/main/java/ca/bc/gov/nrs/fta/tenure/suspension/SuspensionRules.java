package ca.bc.gov.nrs.fta.tenure.suspension;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * When a cut block may be suspended, and what a suspension must carry: legacy FTA912 and FTA914's
 * form validators and FTA904's button rules. The packages check only the dates against the
 * permit's term, so everything else is refused here before they are called.
 */
public final class SuspensionRules {

  /** The suspended block status. */
  public static final String SUSPENDED = "HS";

  /** {@code FTA_912_SUSPEND_PERMIT}: the statuses a permit's blocks are suspended from. */
  private static final Set<String> PERMIT_ELIGIBLE = Set.of("HB", "PE", "PI", "PP");

  /**
   * FTA904's {@code getAllowSuspend} also names PE, but its Save rule locks a block whose status
   * is PE, so a PE block never reached FTA914.
   */
  private static final Set<String> BLOCK_ELIGIBLE = Set.of("HB", "PP");

  private static final int MAX_ORDER_DIGITS = 10;

  private SuspensionRules() {}

  /**
   * Whether FTA912 may suspend a block. A block whose earlier suspension has ended stays out: its
   * package says those are edited from the block.
   */
  public static boolean permitBlockEligible(String status, boolean hasSuspension) {
    return PERMIT_ELIGIBLE.contains(status) && !hasSuspension;
  }

  /** Whether FTA914 may suspend the block. */
  public static boolean canSuspendBlock(String status, boolean hasSuspension) {
    return BLOCK_ELIGIBLE.contains(status) && !hasSuspension;
  }

  /** FTA904's {@code getShowEditSuspend}: a suspension is edited only while the block is HS. */
  public static boolean canEditSuspension(String status, boolean hasSuspension) {
    return SUSPENDED.equals(status) && hasSuspension;
  }

  /** The fields every suspension requires: null when they are all usable, else the problem. */
  public static String fieldsProblem(
      String partitionCode, String orderNumber, LocalDate start, LocalDate end) {
    if (isBlank(partitionCode)) {
      return mandatory("Suspension Partition Code");
    }
    if (isBlank(orderNumber)) {
      return mandatory("Suspension Order Number");
    }
    // CUT_BLOCK_SUSPENSION.SUSP_ORDER_NUMBER is NUMBER(10).
    if (!orderNumber.trim().matches("\\d{1," + MAX_ORDER_DIGITS + "}")) {
      return "Suspension Order Number must be a number of at most " + MAX_ORDER_DIGITS
          + " digits.";
    }
    if (start == null) {
      return mandatory("Suspension Start Date");
    }
    if (end == null) {
      return mandatory("Suspension End Date");
    }
    return null;
  }

  /**
   * What ending a suspension also requires. {@code FTA_914_SUSPEND_BLOCK.END_SUSPENSION} finds the
   * other blocks by the order number it is given, so changing the number while ending all of them
   * would match none and report success.
   */
  public static String endProblem(
      String postStatus, boolean updateAllBlocks, String orderNumber, String currentOrderNumber) {
    if (isBlank(postStatus)) {
      return mandatory("Post Suspension Status");
    }
    if (SUSPENDED.equals(postStatus)) {
      return "Post Suspension Status must not be suspended.";
    }
    if (updateAllBlocks && !orderNumber.trim().equals(currentOrderNumber)) {
      return "Save the new order number before ending the suspension for all blocks.";
    }
    return null;
  }

  /**
   * Whole months from the start of a suspension to the end of the permit's term, the later of its
   * expiry and any extension. Legacy subtracts the other way round and shows a negative count.
   */
  public static Long monthsRemaining(LocalDate start, LocalDate expiry, LocalDate extended) {
    LocalDate term = extended != null ? extended : expiry;
    return start == null || term == null ? null : ChronoUnit.MONTHS.between(start, term);
  }

  private static String mandatory(String field) {
    return field + " is mandatory.";
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
