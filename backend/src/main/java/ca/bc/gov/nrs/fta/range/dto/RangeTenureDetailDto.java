package ca.bc.gov.nrs.fta.range.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rich detail of a range tenure for the FTA100Range detail screen.
 *
 * <p>Mirrors the result of the legacy Oracle package
 * {@code THE.FTA_100RANGE_TENURE} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_100RANGE_TENURE.pks}): its
 * {@code GET} mainline returns the common tenure header plus range-tenure
 * fields, and its {@code GET_SPECIAL_CONDITIONS} returns the
 * {@code rec_spec_condition_results} list. The two sub-tabs are supplied by the
 * sibling packages {@code THE.FTA_613R_RANGE_USAGE}
 * ({@code rec_range_usage_results}) and {@code THE.FTA_615_RANGE_LAND_BASE}
 * ({@code rec_range_land_base_results}), so the detail DTO aggregates all three
 * into one payload the screen's tombstone + tabs render.
 */
public record RangeTenureDetailDto(
    // Common tenure header (FTA_100RANGE_TENURE.GET)
    String forestFileId,
    String fileTypeCode,
    String adminOrgUnitNo,
    String forestDistrictNo,
    String districtAdminZone,
    String licensee,
    String fileStatusSt,
    LocalDate fileStatusDate,
    String mgmtUnitType,
    String mgmtUnitId,
    String fileName,
    LocalDate issueDate,
    LocalDate expiryDate,
    LocalDate originalIssueDate,
    Integer tenureTermYears,
    Integer replacementTermYears,
    Integer replacementCount,
    String rangeInd,
    // Sub-tab result sets
    List<RangeUsageDto> rangeUsage,
    List<RangeLandBaseDto> landBase,
    List<RangeSpecialConditionDto> specialConditions) {

  /**
   * One row of range usage per calendar year — mirrors
   * {@code FTA_613R_RANGE_USAGE.rec_range_usage_results}.
   */
  public record RangeUsageDto(
      String forestFileId,
      Integer calendarYear,
      Double authorizedUse,
      Integer nonUseAgreementNo,
      Double nonUseNonbillable,
      Double nonUseBillable,
      String rangeNonUseReasonCode,
      Double tempIncrease,
      String rangeIncreaseReasonCode,
      Double totalAnnualUse,
      Integer revisionCount) {}

  /**
   * One associated private-land parcel — mirrors
   * {@code FTA_615_RANGE_LAND_BASE.rec_range_land_base_results}.
   */
  public record RangeLandBaseDto(
      Long landBaseSkey,
      String rangeLandBasePid,
      String rangeLandBaseId,
      String description,
      String rangeLandOwnershipTypeCode,
      String rangeLandPurposeCode,
      Double fencedPasture,
      Double unfencedPasture,
      Double forageProduction,
      String landBaseComment,
      LocalDate leaseStartDate,
      LocalDate leaseEndDate,
      String rangeLandBaseActInd,
      String rangeLandOwnershipTypeDesc,
      String rangeLandPurposeDesc,
      Integer revisionCount) {}

  /**
   * One special condition on the range tenure — mirrors
   * {@code FTA_100RANGE_TENURE.rec_spec_condition_results}.
   */
  public record RangeSpecialConditionDto(
      Long specialConditionSkey,
      String rangeSpecialConditionCode,
      String conditionTitle,
      String conditionDescription,
      Integer rscRevisionCount) {}
}
