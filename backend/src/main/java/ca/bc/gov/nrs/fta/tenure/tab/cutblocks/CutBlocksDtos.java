package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Cut block tab's request and response shapes (legacy FTA903 Cut Block List). */
public final class CutBlocksDtos {

  private CutBlocksDtos() {}

  /**
   * One cut block as {@code FTA_903_CUTBLK_LST.GET} lists it, plus what the tab needs to link
   * to the block and to delete it.
   *
   * @param cbSkey             CUT_BLOCK.CB_SKEY — the block's key (delete, React key)
   * @param cuttingPermitId    legacy's "CP/HVA ID" column: the CP, or a Fort St. John
   *                           authority's harvesting authority id
   * @param timberMark         CUT_BLOCK.TIMBER_MARK ("ORG" for a BCTS B07 DT with no mark)
   * @param salvageTypeCode    the permit's salvage type
   * @param markStatusCode     the permit's (or private mark's) status code
   * @param cutBlockId         CUT_BLOCK_ID
   * @param blockStatusCode    BLOCK_STATUS_ST
   * @param blockStatus        "code - description" of the block status
   * @param startDate          harvest start (DISTURBANCE_START_DATE)
   * @param endDate            harvest complete (DISTURBANCE_END_DATE)
   * @param plannedGross       planned gross area, legacy-formatted (4 decimals)
   * @param plannedNet         planned net area, legacy-formatted
   * @param actualGross        actual gross (disturbance) area, legacy-formatted
   * @param authorizedFileId   legacy's "Authorized File" — set for blocks managed by, or
   *                           owned by, another file
   * @param authorizedCpId     legacy's "Authorized CP"
   * @param blockForestFileId  CUT_BLOCK.FOREST_FILE_ID — what the cut block detail page keys on
   * @param blockCuttingPermitId CUT_BLOCK.CUTTING_PERMIT_ID (trimmed; null for a blank CP)
   * @param revisionCount      CUT_BLOCK.REVISION_COUNT, for the delete's optimistic lock
   * @param canDelete          whether legacy's Delete button is enabled for the row
   * @param deleteReason       why not, when {@code canDelete} is false
   */
  public record CutBlockRow(
      Long cbSkey,
      String cuttingPermitId,
      String timberMark,
      String salvageTypeCode,
      String markStatusCode,
      String cutBlockId,
      String blockStatusCode,
      String blockStatus,
      LocalDate startDate,
      LocalDate endDate,
      String plannedGross,
      String plannedNet,
      String actualGross,
      String authorizedFileId,
      String authorizedCpId,
      String blockForestFileId,
      String blockCuttingPermitId,
      Long revisionCount,
      boolean canDelete,
      String deleteReason) {}

  /** One suspension of one of the tenure's blocks ({@code FTA_903_CB_SUSP_LIST.GET}). */
  public record CutBlockSuspension(
      String cuttingPermitId,
      String cutBlockId,
      String suspensionOrderNo,
      /* "code - description" of the under-partition code. */
      String underPartition,
      LocalDate startDate,
      LocalDate endDate) {}

  /**
   * A harvesting authority (CP) of the tenure that a block could be added to, with legacy's
   * {@code FTA903_ADD_NEW} verdict on it.
   *
   * @param hvaSkey         the authority's key — what an add names
   * @param cuttingPermitId the CP; null for a single-mark (B04/B07) authority
   * @param timberMark      its primary timber mark — the new block's mark
   * @param statusCode      HARVEST_AUTH_STATUS_CODE
   * @param salvageTypeCode SALVAGE_TYPE_CODE
   * @param eligible        whether a block may be added to it
   * @param reason          why not, when {@code eligible} is false
   */
  public record CutBlockPermitOption(
      Long hvaSkey,
      String cuttingPermitId,
      String timberMark,
      String statusCode,
      String salvageTypeCode,
      boolean eligible,
      String reason) {}

  /** The tab: the blocks, their suspensions, and what may be done. */
  public record CutBlocksResponse(
      CutBlocksRules rules,
      List<CutBlockRow> blocks,
      List<CutBlockSuspension> suspensions,
      List<CutBlockPermitOption> permits) {}

  /**
   * A block to add — FTA904's add mode as FTA903's Add New opened it.
   *
   * @param hvaSkey                 the permit (from {@code permits}) the block goes on
   * @param cutBlockId              the block id, at most 10 characters
   * @param blockStatusDate         "As of" date; today when null
   * @param description             at most 120 characters
   * @param plannedGrossArea        hectares
   * @param plannedNetArea          hectares, at most the gross
   * @param plannedStartDate        optional
   * @param spExempt                Y or N
   * @param wasteAssessmentRequired Y or N (legacy default Y)
   * @param underPartitionOrder     Y, N or null
   * @param fireHarvestingReasonCode optional code
   * @param reportedFireDate        optional
   */
  public record CutBlockCreateRequest(
      Long hvaSkey,
      String cutBlockId,
      LocalDate blockStatusDate,
      String description,
      BigDecimal plannedGrossArea,
      BigDecimal plannedNetArea,
      LocalDate plannedStartDate,
      String spExempt,
      String wasteAssessmentRequired,
      String underPartitionOrder,
      String fireHarvestingReasonCode,
      LocalDate reportedFireDate) {}

  /** What an add made. */
  public record CutBlockCreated(Long cbSkey, String cutBlockId, String timberMark) {}

  /** A delete: the row's revision count and legacy's mandatory deletion comment. */
  public record CutBlockDeleteRequest(Long revisionCount, String comment) {}

  /** A code-list entry ("code - description" label, bare code value). */
  public record CutBlocksCodeOption(String code, String description) {}
}
