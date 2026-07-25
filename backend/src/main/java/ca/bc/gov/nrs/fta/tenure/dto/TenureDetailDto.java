package ca.bc.gov.nrs.fta.tenure.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Richer, single-file tenure detail record for the FTA100 detail screen.
 *
 * <p>Mirrors the file-level "common tenure" fields returned by the legacy
 * Oracle package {@code THE.FTA_100_TENURE.mainline} (GET action — see
 * {@code fta-archive/fta/database/ddl/pkg/fta_100_tenure.pks}), enriched with
 * the AAC summary ported from {@code THE.FTA_930_AAC} and the sale-info summary
 * ported from {@code THE.FTA_940_SALE_INFO}. Those three packages back,
 * respectively, the tombstone/Tenure tab, the AAC tab and the Sale Info tab of
 * the detail page.
 *
 * <p>Dates use {@link LocalDate}; monetary/area amounts use {@link BigDecimal}.
 * Any field not present for a given file comes back null.
 */
public record TenureDetailDto(
    // file-level header / common tenure (FTA_100_TENURE)
    String forestFileId,
    String fileTypeCode,
    String fileStatusCode,
    String fileStatusDesc,
    LocalDate fileStatusDate,
    String orgUnitCode,
    String clientNumber,
    String clientLocnCode,
    String licensee,
    String mgmtUnitType,
    String mgmtUnitId,
    String managementUnit,
    LocalDate awardDate,
    LocalDate expiryDate,
    LocalDate initialExpiryDate,
    Integer tenureTermMonths,
    Integer extensionCount,
    String secLicenseeInd,
    String notesLabel,
    // AAC summary (FTA_930_AAC)
    BigDecimal scheduleAArea,
    BigDecimal scheduleBArea,
    BigDecimal allowableAnnualCut,
    // sale info summary (FTA_940_SALE_INFO)
    String saleMethodCode,
    String saleTypeCode,
    String paymentMethodCode,
    BigDecimal cashSaleEstVol,
    BigDecimal cashSaleTotDol,
    BigDecimal ftaBonusBid,
    BigDecimal ftaBonusOffer,
    String scrtyDepositCode,
    BigDecimal scrtyDepositAmt) {}
