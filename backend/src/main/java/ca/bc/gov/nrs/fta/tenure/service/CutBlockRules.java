package ca.bc.gov.nrs.fta.tenure.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a cut block allows — legacy FTA904's gating, computed once and served both to the screen
 * (which buttons and fields are enabled) and to the writes (which refuse what it refuses).
 *
 * <p>Legacy also decided Save by organization ({@code SET_AUTHORITY_IND}). This app has no
 * organization levels: a harvest role holder may act on any district's blocks, so only the
 * role, the statuses and the block's own data decide. Oil and gas (A11) files stay read-only.
 *
 * @param canEdit           Save enabled ({@code p_disable_save_ind}, without the org check)
 * @param statusLocked      the status can't change: {@code getIsStatusDisabled}
 * @param plannedLocked     planned gross and start can't change: the block has a map feature
 * @param disturbanceLocked logged area and dates can't change: the block has an opening
 * @param spExemptLocked    SP Exempt can't change: the block has an opening
 * @param canRelabel        Re-label shown: {@code getRelabelEnabled}
 * @param canSuspend        Suspend Cut Block shown: {@code getAllowSuspend}
 * @param canEditSuspension Edit Suspension shown: {@code getShowEditSuspend}
 * @param seniorAuthority   rollbacks are open to this user, who must then give a reason
 */
public record CutBlockRules(
    boolean canEdit,
    boolean statusLocked,
    boolean plannedLocked,
    boolean disturbanceLocked,
    boolean spExemptLocked,
    boolean canRelabel,
    boolean canSuspend,
    boolean canEditSuspension,
    boolean seniorAuthority) {

  static final String MSG_STATUS_PE =
      "No updates can be performed on this file when the status is PE.";
  static final String MSG_NOT_EDITABLE = "This cut block cannot be edited.";
  static final String MSG_STATUS_LOCKED = "The status of this cut block cannot be changed.";
  static final String MSG_CANNOT_RELABEL = "This cut block cannot be re-labelled.";
  static final String MSG_OVERRIDE_REASON = "District Override Reason is mandatory.";

  private static final int MAX_DESCRIPTION = 120;
  private static final BigDecimal MAX_AREA = new BigDecimal("9999999.9999");
  private static final BigDecimal MAX_LOGGED_AREA = new BigDecimal("999999.9999");
  private static final Set<String> SUSPENDABLE = Set.of("HB", "PP", "PE");
  private static final String OIL_AND_GAS_FILE_TYPE = "A11";

  /** The block's state, as the rules need it. */
  public record Facts(
      String fileTypeCode,
      String fileStatusCode,
      String markStatusCode,
      String blockStatusCode,
      boolean hasAdminRow,
      boolean hasMapFeature,
      boolean hasOpening,
      boolean hasSuspension,
      boolean permitRetired) {}

  /** The fields FTA904's Save takes. */
  public record Edit(
      String blockStatus,
      String salvageTypeCode,
      String cutRegulationCode,
      BigDecimal plannedGrossBlockArea,
      BigDecimal plannedNetBlockArea,
      BigDecimal disturbanceGrossArea,
      LocalDate disturbanceStartDate,
      LocalDate disturbanceEndDate,
      String spExemptInd,
      String cutBlockDescription) {}

  /**
   * @param harvestEditor FTA_ADMIN or either harvest role
   * @param senior        FTA_ADMIN or the senior harvest role
   */
  public static CutBlockRules of(Facts f, boolean harvestEditor, boolean senior) {
    boolean canEdit = harvestEditor
        && f.hasAdminRow()
        && !"PE".equals(f.blockStatusCode())
        && !OIL_AND_GAS_FILE_TYPE.equals(f.fileTypeCode());
    return new CutBlockRules(
        canEdit,
        "PE".equals(f.blockStatusCode())
            || "HX".equals(f.fileStatusCode())
            || "HC".equals(f.fileStatusCode()),
        f.hasMapFeature(),
        f.hasOpening(),
        f.hasOpening(),
        senior && canEdit && !"PE".equals(f.fileStatusCode()) && !f.permitRetired(),
        senior && canEdit && SUSPENDABLE.contains(f.blockStatusCode()) && !f.hasSuspension(),
        senior && canEdit && "HS".equals(f.blockStatusCode()),
        senior);
  }

  /** FTA904's Save validators: what is wrong with the edit, none when it may go to the package. */
  public static List<String> editProblems(Edit e, Facts f) {
    List<String> problems = new ArrayList<>();
    boolean noOpening = !f.hasOpening();
    boolean statusChanged = e.blockStatus() != null && !e.blockStatus().equals(f.blockStatusCode());
    if (isBlank(e.blockStatus())) {
      problems.add("Status is mandatory.");
    }
    if (e.plannedGrossBlockArea() == null) {
      problems.add("Planned Gross Area (ha) is mandatory.");
    } else if (outOfRange(e.plannedGrossBlockArea(), MAX_AREA)) {
      problems.add("Planned Gross Area (ha) must be between 0 and 9,999,999.9999.");
    }
    if (e.plannedNetBlockArea() == null) {
      problems.add("Planned Net Area (ha) is mandatory.");
    } else if (outOfRange(e.plannedNetBlockArea(), MAX_AREA)) {
      problems.add("Planned Net Area (ha) must be between 0 and 9,999,999.9999.");
    }
    if (e.plannedGrossBlockArea() != null
        && e.plannedNetBlockArea() != null
        && e.plannedNetBlockArea().compareTo(e.plannedGrossBlockArea()) > 0) {
      problems.add("Planned net area must be less than or equal to planned gross area.");
    }
    if (isBlank(e.spExemptInd())) {
      problems.add("SP Exempt is mandatory.");
    }
    if (noOpening) {
      if (e.disturbanceGrossArea() != null
          && outOfRange(e.disturbanceGrossArea(), MAX_LOGGED_AREA)) {
        problems.add("Actual Logged Area (ha) must be between 0 and 999,999.9999.");
      }
      if ("LC".equals(e.blockStatus()) && e.disturbanceGrossArea() == null) {
        problems.add("Actual Logged Area (ha) is required if status is LC.");
      }
      if (statusChanged
          && ("S".equals(e.blockStatus()) || "LC".equals(e.blockStatus()))
          && e.disturbanceEndDate() == null) {
        problems.add("Complete Date is mandatory.");
      }
      if (e.disturbanceStartDate() != null
          && e.disturbanceEndDate() != null
          && !e.disturbanceStartDate().isBefore(e.disturbanceEndDate())) {
        problems.add("Start Date must be less than Complete Date.");
      }
    }
    if (e.cutBlockDescription() != null && e.cutBlockDescription().length() > MAX_DESCRIPTION) {
      problems.add("Block Description must not exceed " + MAX_DESCRIPTION + " characters.");
    }
    if (isBlank(e.salvageTypeCode()) && !isBlank(e.cutRegulationCode())) {
      problems.add(
          "Cut Regulation Code must be blank for Cut Blocks that belong to non-salvage"
              + " Cutting Permits.");
    }
    return problems;
  }

  private static boolean outOfRange(BigDecimal value, BigDecimal max) {
    return value.signum() < 0 || value.compareTo(max) > 0;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
