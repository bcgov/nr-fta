package ca.bc.gov.nrs.fta.apt.dto;

import java.time.LocalDate;

/**
 * The filters of {@code APT_MANAGEMENT_UNITS_GET}, in its parameter order. Every one is optional;
 * none returns every unit.
 *
 * @param id                     one unit by id
 * @param managementUnitType     the type code, exact
 * @param managementUnitNumber   the number, exact
 * @param status                 the status code, exact
 * @param effectiveDate          the status effective date, exact
 * @param orgUnitNumber          the org unit, exact
 * @param orgUnitName            part of the org unit's code or name, any case
 * @param orcsFileNumber         part of the ORCS file number, any case
 * @param sourceManagementUnitId units that came from this one
 */
public record AptManagementUnitSearchCriteria(
    Long id,
    String managementUnitType,
    String managementUnitNumber,
    String status,
    LocalDate effectiveDate,
    Long orgUnitNumber,
    String orgUnitName,
    String orcsFileNumber,
    Long sourceManagementUnitId) {

  /** Criteria that find one unit by id. */
  public static AptManagementUnitSearchCriteria byId(Long id) {
    return new AptManagementUnitSearchCriteria(id, null, null, null, null, null, null, null, null);
  }
}
