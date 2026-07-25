package ca.bc.gov.nrs.fta.range.dto;

/**
 * A single range zone for the Manage Range Zone admin screen.
 *
 * <p>Mirrors the {@code rec_range_zone_results} record returned by the legacy
 * Oracle package {@code THE.FTA_631_RANGE_ZONE} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_631_MANAGE_RANGE_ZONE.pks}): the
 * range-zone columns joined to the administering org unit that the package's
 * {@code GET} action returns.
 */
public record ManageZoneDto(
    String rangeZoneCode,
    String zoneDescription,
    Long adminForestDistrictNo,
    String contact,
    String contactUserId,
    String contactPhoneNumber,
    String contactEmailAddress,
    Long revisionCount,
    String orgUnitCode,
    String orgUnitName) {}
