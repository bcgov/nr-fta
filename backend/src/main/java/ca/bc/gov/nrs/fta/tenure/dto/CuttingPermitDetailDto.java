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
 *
 * @param hvaSkey         the key every write addresses the permit by
 * @param cuttingPermitId the id shown: the harvesting authority id on an FSJ permit
 * @param harvestArea     Schedule B (ha) on an A02, A04, A44, A28 or A29, Area (ha) otherwise
 * @param scheduleAArea   Schedule A (ha), the private-land blocks; those five types only
 * @param revisionCount   HARVESTING_AUTHORITY's, sent back on a write
 * @param blockSuspensionsExist a block of the permit has a suspension, ended or not (GET's
 *                        {@code p_cb_susp_cnt}), so FTA902 asks for the Expiry Date to be
 *                        checked
 * @param postponementExpired the permit is HP and its latest postponement expired before today
 * @param fsj             a Fort St. John authority ({@code Fta_Get_Fsj_Ind}): FTA902 offers its
 *                        FTA413 Letter
 * @param rules           what the caller may do to this permit
 */
public record CuttingPermitDetailDto(
    Long hvaSkey,
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
    Double scheduleAArea,
    String location,
    String mgmtUnitId,
    String mgmtUnitTypeCode,
    String licenceToCutCode,
    String extAppFeeWaived,
    String extAppFeePaid,
    String withinFibreRecoveryZone,
    LocalDate retirementDate,
    Long revisionCount,
    boolean blockSuspensionsExist,
    boolean postponementExpired,
    boolean fsj,
    CuttingPermitRules rules) {}
