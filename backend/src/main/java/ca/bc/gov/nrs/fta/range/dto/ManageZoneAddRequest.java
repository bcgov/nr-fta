package ca.bc.gov.nrs.fta.range.dto;

/**
 * Request body for adding / saving a range zone ({@code POST
 * /api/fta/admin/range-zones}). Fields mirror the {@code SAVE} proc params of
 * the legacy {@code THE.FTA_631_RANGE_ZONE} package, which upserts a row of
 * {@code THE.RANGE_ZONE} keyed by (admin_forest_district_no, range_zone_code).
 */
public record ManageZoneAddRequest(
    String rangeZoneCode,
    String zoneDescription,
    String adminForestDistrictNo,
    String contact,
    String contactUserId,
    String contactPhoneNumber,
    String contactEmailAddress) {}
