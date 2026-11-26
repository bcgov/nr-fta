package ca.bc.gov.nrs.fta.range.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rich detail of a range tenure for the FTA100Range detail screen.
 *
 * <p>Mirrors the result of the legacy Oracle package
 * {@code THE.FTA_100RANGE_TENURE}: its {@code GET} returns the common tenure
 * header plus the range-tenure fields, and its {@code GET_SPECIAL_CONDITIONS}
 * returns the special conditions listed under the header. The two range tabs
 * are supplied by the sibling packages {@code THE.FTA_613R_RANGE_USAGE}
 * ({@code GET} for the tab, bounded by the tenure term; {@code GET_HISTORY} for
 * its "View History" window) and {@code THE.FTA_615_RANGE_LAND_BASE}, so the
 * detail DTO aggregates them into one payload.
 */
public record RangeTenureDetailDto(
    // Common tenure header (FTA_100RANGE_TENURE.GET)
    String forestFileId,
    String fileTypeCode,
    String adminOrgUnitNo,
    String forestDistrictNo,
    String districtAdminZone,
    /** FTA_GET_MAIN_LICENSEE: "client# locn  full name". */
    String licensee,
    /** Y when the file has a secondary (type B) licensee. */
    String secLicenseeInd,
    /** "Note(s) Attached" when the file has notes, else blank. */
    String notesLabel,
    String fileStatusSt,
    LocalDate fileStatusDate,
    String mgmtUnitType,
    String mgmtUnitId,
    /** FTA_GET_MGMT_UNIT_DESC. */
    String mgmtUnitName,
    String fileName,
    LocalDate issueDate,
    /** TENURE_TERM.INITIAL_EXPIRY_DT. */
    LocalDate expiryDate,
    LocalDate originalIssueDate,
    Integer tenureTermYears,
    Integer replacementTermYears,
    Integer replacementCount,
    /** "This tenure will be replaced on Jan. 1, YYYY for N years", or null. */
    String replacementLabel,
    /** Y for E01/H01: the Historical and Replacement sections apply. */
    String licenceReplaceableInd,
    String rangeInd,
    /** The "tenure term is not whole calendar years" warning, or null. */
    String termWarning,
    // Sub-tab result sets
    /** Range Usage tab: FTA_613R GET, the years of the term, ascending. */
    List<RangeUsageDto> rangeUsage,
    /** Usage History: FTA_613R GET_HISTORY, every year, newest first. */
    List<RangeUsageDto> usageHistory,
    List<RangeLandBaseDto> landBase,
    List<RangeSpecialConditionDto> specialConditions) {

  /**
   * One row of range usage per calendar year — mirrors
   * {@code FTA_613R_RANGE_USAGE.rec_range_usage_results}, plus the reason code
   * descriptions the legacy form looked up.
   */
  public record RangeUsageDto(
      String forestFileId,
      Integer calendarYear,
      Double authorizedUse,
      Integer nonUseAgreementNo,
      Double nonUseNonbillable,
      Double nonUseBillable,
      String rangeNonUseReasonCode,
      String rangeNonUseReasonDesc,
      Double tempIncrease,
      String rangeIncreaseReasonCode,
      String rangeIncreaseReasonDesc,
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
      /** Shown as "Hay Field (ha)" on the legacy screen. */
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
