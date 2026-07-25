package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Full detail of a single cut block for the FTA904 Cut Block Detail screen.
 *
 * <p>Mirrors the row assembled by the legacy Oracle package
 * {@code THE.FTA_904_CUTBLKDETAIL.GET} (see
 * {@code fta-archive/fta/database/ddl/pkg/fta_904_cutblkdetail.pks}). The
 * package returns its data through the {@code mainline}/{@code GET} OUT
 * parameters rather than a named record type; the fields below correspond to
 * those output columns (the primary, non-private-mark, non-FSJ branch of
 * {@code GET}). Dates use {@link LocalDate}; block/schedule areas use
 * {@code Double}.
 */
public record CutblockDetailDto(
    String forestFileId,
    String cuttingPermitId,
    String cutBlockId,
    String timberMark,
    String forestDistrict,
    String markStatus,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    String markTerm,
    String blockStatus,
    LocalDate blockStatusDate,
    String cutBlockDescription,
    String spExemptInd,
    Double plannedGrossBlockArea,
    Double plannedNetBlockArea,
    Double disturbanceGrossArea,
    LocalDate disturbanceStartDate,
    LocalDate disturbanceEndDate,
    LocalDate plannedHarvestDate,
    String opening,
    Long openingId,
    String referenceName,
    String salvageTypeCode,
    String cutRegulationCode,
    String reforestDeclareTypeCode,
    String harvestTypeCode,
    LocalDate decisionDate,
    LocalDate issuanceDate,
    String fireHarvestingReasonCode,
    String underPartitionOrder,
    LocalDate reportedFireDate) {}
