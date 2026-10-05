package ca.bc.gov.nrs.fta.tenure.tab.amendments;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Response shapes of the tenure CP/CB amendments tab (legacy FTA905). */
public final class AmendmentsDtos {

  private AmendmentsDtos() {}

  /**
   * One cut block that has amendments, with their totals — a row of FTA905's roll-up
   * ({@code FTA_905_CP_AMEND.GET}).
   *
   * @param cuttingPermitId  the CP, or a Fort St. John authority's HVA id
   * @param fsj              whether the authority is Fort St. John (shown as "HVA ID")
   * @param timberMark       the block's timber mark
   * @param cutBlockId       the cut block
   * @param cbSkey           the block's key, for the drill-down
   * @param totalNetArea     sum of the amendments' net area (ha)
   * @param totalGrossArea   sum of the amendments' gross area (ha)
   * @param totalCruiseVolume sum of the amendments' cruise volume (m3)
   */
  public record AmendmentsBlockRow(
      String cuttingPermitId,
      boolean fsj,
      String timberMark,
      String cutBlockId,
      Long cbSkey,
      BigDecimal totalNetArea,
      BigDecimal totalGrossArea,
      BigDecimal totalCruiseVolume) {}

  /** {@code GET /api/fta/tenures/{forestFileId}/cp-cb-amendments}. */
  public record AmendmentsListDto(
      boolean available, String unavailableReason, List<AmendmentsBlockRow> blocks) {}

  /**
   * One amendment of a block — a row of FTA905's block view ({@code FTA_905_BLK_AMEND.GET}).
   * Amendment 0 is the original ("ORIG").
   */
  public record AmendmentsAmendmentRow(
      Integer amendmentId,
      BigDecimal netArea,
      BigDecimal grossArea,
      BigDecimal cruiseVolume,
      LocalDate applicationDate,
      String statusCode,
      LocalDate statusDate,
      String reasonCode,
      boolean hasImage,
      String imageMimeTypeCode,
      Long tenureAppId) {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/cp-cb-amendments/blocks/{cbSkey}} — the block's
   * header and its amendments.
   *
   * @param blockStatus           "CODE - description" of the block's status
   * @param plannedNetArea        planned net block area (ha)
   * @param plannedGrossArea      planned gross block area (ha)
   * @param disturbanceGrossArea  actual harvested gross area (ha)
   */
  public record AmendmentsBlockDetailDto(
      String cuttingPermitId,
      boolean fsj,
      String timberMark,
      String cutBlockId,
      Long cbSkey,
      String blockStatusCode,
      String blockStatus,
      LocalDate blockStatusDate,
      BigDecimal plannedNetArea,
      BigDecimal plannedGrossArea,
      BigDecimal disturbanceGrossArea,
      List<AmendmentsAmendmentRow> amendments) {}
}
