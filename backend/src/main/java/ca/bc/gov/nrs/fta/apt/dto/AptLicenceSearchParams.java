package ca.bc.gov.nrs.fta.apt.dto;

/**
 * The criteria of {@code GET /api/fta/apt/licences}, which are {@code APT_LICENCES_GET}'s
 * arguments, in order.
 *
 * @param forestFileId         required; matched with LIKE, so {@code %} and {@code _} work
 * @param clientName           licensee name contains
 * @param orgUnitName          region name contains
 * @param managementUnitName   management unit name contains
 * @param managementUnitType   management unit type, exact
 * @param managementUnitNumber management unit number, exact
 */
public record AptLicenceSearchParams(
    String forestFileId,
    String clientName,
    String orgUnitName,
    String managementUnitName,
    String managementUnitType,
    String managementUnitNumber) {}
