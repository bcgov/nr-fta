package ca.bc.gov.nrs.fta.apt.dto;

/**
 * A management unit in the code tables that APT has no record of yet, one row of
 * {@code APT_MGMT_UNIT_UNASSIGNED_GET}: what a new management unit is picked from.
 *
 * @param managementUnitCode   type and number run together, as the function returns them, e.g.
 *                             {@code U01}
 * @param managementUnitName   the unit's name
 * @param managementUnitType   the type code, split off the code
 * @param managementUnitNumber the number, split off the code; null when the code has none
 */
public record AptManagementUnitUnassignedDto(
    String managementUnitCode,
    String managementUnitName,
    String managementUnitType,
    String managementUnitNumber) {}
