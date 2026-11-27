package ca.bc.gov.nrs.fta.range.dto;

/**
 * FTA630's Save Range: the range unit's fields, for adding one ({@code POST}) or saving one
 * ({@code PUT}).
 *
 * @param rangeUnitId       only to be refused on an add: legacy generates the id and told a user
 *                          who typed one to leave it blank
 * @param rangeUnitName     the name (INITCAP'd by the package)
 * @param status            RANGE_UNIT_STATUS_CODE; ignored on an add, which is always P
 * @param asOf              the status date, {@code yyyy-MM-dd}; blank means today
 * @param district          the administering district's ORG_UNIT_NO
 * @param districtAdminZone the zone, upper-cased on save
 * @param revisionCount     the range unit's revision count as read; required on a save
 */
public record RangeUnitSaveRequest(
    String rangeUnitId,
    String rangeUnitName,
    String status,
    String asOf,
    String district,
    String districtAdminZone,
    Integer revisionCount) {}
