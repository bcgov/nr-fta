package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Rich detail view of a single cutting permit / timber mark authority.
 *
 * <p>Mirrors the header + mark-level columns the legacy Oracle package
 * {@code THE.FTA_902_CP_DETAIL} assembles for its FTA902 screen (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_902_CP_DETAIL.pks}). The package
 * has no single result record — its {@code mainline}/{@code GET} procedures
 * populate a long list of {@code IN OUT} header + mark parameters — so this
 * record collects the fields those procedures select from
 * {@code HARVESTING_AUTHORITY} (via {@code GET_TIMBER_MARK}) plus the file
 * header descriptors.
 */
public record CuttingPermitDetailDto(
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String fileTypeCode,
    String fileTypeDescription,
    String adminOrgCode,
    String licensee,
    String statusCode,
    String statusDesc,
    LocalDate statusDate,
    LocalDate issueDate,
    LocalDate expiryDate,
    LocalDate extendDate,
    String extendReasonCode,
    Integer extendCount,
    Integer tenureTermYears,
    Integer tenureTermMonths,
    String forestDistrict,
    String quotaTypeCode,
    String salvageTypeCode,
    String deciduousInd,
    String catastrophicInd,
    String cruiseBasedInd,
    String crownLandsRegionCode,
    String markingMethodCode,
    String markingInstrumentCode,
    String districtAdmnZone,
    Double harvestArea,
    String location,
    String mgmtUnitId,
    String mgmtUnitTypeCode) {}
