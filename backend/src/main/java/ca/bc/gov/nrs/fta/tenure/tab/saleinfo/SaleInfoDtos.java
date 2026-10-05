package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The Sale info tab's request and response shapes (legacy FTA940, Sale Info). */
public final class SaleInfoDtos {

  private SaleInfoDtos() {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/sale-info}. Descriptions are "CODE -
   * description". The revision counts are null while the record does not exist yet (the
   * first save creates it).
   *
   * @param annualRent             legacy's computed annual rent: an amount ("1234.50"),
   *                               "N/A", or null when it cannot be computed
   * @param bctsBonusBid           the awarded BCTS bidder's bonus bid (read-only)
   * @param bctsLumpSumBonusOffer  the awarded BCTS bidder's bonus offer, when it bid none
   * @param ftaBonusBid            the FTA-entered bonus bid (non-BCTS files)
   * @param ftaBonusOffer          the FTA-entered lump sum bonus offer (non-BCTS files)
   */
  public record SaleInfoResponse(
      String forestFileId,
      String fileTypeCode,
      String fileStatusCode,
      boolean recordExists,
      String saleMethodCode,
      String saleMethodDesc,
      String saleTypeCode,
      String saleTypeDesc,
      String paymentMethodCode,
      String paymentMethodDesc,
      String salvageInd,
      String minorFacilityInd,
      String adminAreaInd,
      String adminAreaFile,
      LocalDate plannedSaleDate,
      LocalDate tenderOpeningDate,
      BigDecimal cashSaleEstVol,
      BigDecimal cashSaleTotDol,
      BigDecimal saleVolume,
      Integer totalBidders,
      BigDecimal ftaBonusBid,
      BigDecimal ftaBonusOffer,
      BigDecimal bctsBonusBid,
      BigDecimal bctsLumpSumBonusOffer,
      String bctsFundInd,
      String bctsOrgUnitNo,
      String bctsOrgDesc,
      String plannedSbCatCode,
      String plannedSbCatDesc,
      String soldSbCatCode,
      String soldSbCatDesc,
      String scrtyDepositCode,
      String scrtyDepositDesc,
      BigDecimal scrtyDepositAmt,
      String otherDepositCode,
      String otherDepositDesc,
      BigDecimal otherDepositAmt,
      String annualRent,
      LocalDate legalEffectiveDate,
      Long hsRevisionCount,
      Long tdRevisionCount,
      Long auRevisionCount,
      SaleInfoRules rules) {}

  /**
   * {@code PUT …/sale-info} — the whole record as edited, with the revision counts the GET
   * returned. Fields the rules keep closed are ignored: the stored values are kept.
   *
   * @param salvageInd       Y or N
   * @param minorFacilityInd Y or N
   * @param adminAreaInd     Y or N
   */
  public record SaleInfoUpdateRequest(
      String saleMethodCode,
      String saleTypeCode,
      String salvageInd,
      String minorFacilityInd,
      String adminAreaInd,
      String adminAreaFile,
      LocalDate plannedSaleDate,
      LocalDate tenderOpeningDate,
      BigDecimal cashSaleEstVol,
      BigDecimal cashSaleTotDol,
      BigDecimal saleVolume,
      Integer totalBidders,
      BigDecimal ftaBonusBid,
      BigDecimal ftaBonusOffer,
      String plannedSbCatCode,
      String soldSbCatCode,
      String scrtyDepositCode,
      BigDecimal scrtyDepositAmt,
      String otherDepositCode,
      BigDecimal otherDepositAmt,
      Long hsRevisionCount,
      Long tdRevisionCount,
      Long auRevisionCount) {}

  /** What a save returns: legacy's non-blocking warnings (the record was saved). */
  public record SaleInfoSaveResult(List<String> warnings) {}
}
