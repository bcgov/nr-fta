package ca.bc.gov.nrs.fta.tenure.tab.aac;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The AAC tab's request and response shapes (legacy FTA930, Allowable Annual Cut). */
public final class AacDtos {

  private AacDtos() {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/aac} — the tenure's AAC history and areas.
   *
   * @param fileTypeCode       the tenure's file type
   * @param awardDate          the tenure's legal effective date (an AAC cannot start before it)
   * @param expiryDate         current expiry, else initial expiry (an AAC cannot start after it)
   * @param scheduleAArea      Private/Schedule A hectares ({@code TIMBER_TENURE})
   * @param scheduleBArea      Crown/Schedule B hectares
   * @param areaRevisionCount  {@code TIMBER_TENURE.REVISION_COUNT}; null when the tenure has no
   *                           timber tenure record
   * @param rows               the AAC history, most recent period first — legacy's order
   * @param rules              what may be changed
   */
  public record AacResponse(
      String fileTypeCode,
      LocalDate awardDate,
      LocalDate expiryDate,
      BigDecimal scheduleAArea,
      BigDecimal scheduleBArea,
      Long areaRevisionCount,
      List<AacRow> rows,
      AacRules rules) {}

  /**
   * One AAC history row: an allocation amount within its allocation period.
   *
   * @param revenueShareable Y, N, or U (unknown — legacy's value when none was chosen)
   */
  public record AacRow(
      long amountId,
      long periodId,
      LocalDate effectiveDate,
      String unitOfMeasureCode,
      String areaTypeCode,
      String areaTypeDesc,
      String cutTypeCode,
      String cutTypeDesc,
      BigDecimal amount,
      String reasonCode,
      String reasonDesc,
      String revenueShareable,
      BigDecimal fra2003Volume,
      String comment,
      String entryUserid,
      String updateUserid,
      long periodRevisionCount,
      long amountRevisionCount) {}

  /**
   * Add ({@code POST …/aac}) or change ({@code PUT …/aac/{amountId}}) an AAC history row.
   * The revision counts are needed only for a change.
   *
   * @param revenueShareable Y, N, or blank for unknown
   */
  public record AacSaveRequest(
      LocalDate effectiveDate,
      String unitOfMeasureCode,
      String areaTypeCode,
      String cutTypeCode,
      BigDecimal amount,
      String reasonCode,
      String comment,
      String revenueShareable,
      BigDecimal fra2003Volume,
      Long periodRevisionCount,
      Long amountRevisionCount) {}

  /** {@code PUT …/aac/areas} — the Schedule A and B hectares, both optional. */
  public record AacAreasRequest(
      BigDecimal scheduleAArea, BigDecimal scheduleBArea, Long revisionCount) {}
}
