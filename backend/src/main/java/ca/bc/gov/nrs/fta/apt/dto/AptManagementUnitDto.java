package ca.bc.gov.nrs.fta.apt.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A management unit, one row of {@code APT_MANAGEMENT_UNITS_GET}. Field names are APT2's, which
 * the APT screens (and other APT areas) read.
 *
 * @param id                  {@code APT_MANAGEMENT_UNITS.ID}
 * @param mgmtUnitName        the unit's name from the code tables, e.g. {@code Arrow TSA}
 * @param orgUnitNo           the region or district the unit belongs to
 * @param orgUnitName         that org unit as {@code CODE - NAME}
 * @param aptMgmtUnitSt       the unit type code ({@code T} TFL, {@code U} TSA, {@code F} woodlot)
 * @param mgmtUnitNumber      the unit number within its type
 * @param mgmtUnitStatusSt    the status code
 * @param statusEffectiveDate when that status took effect
 * @param muId                the source management unit this one came from, if any
 * @param srcMgmtUnitName     that source unit's name
 * @param orcsFileNumber      the ORCS file number
 * @param comments            free text
 * @param addTimestamp        when the row was added
 * @param addUserid           who added it
 * @param updateTimestamp     always null: the APT function does not return it
 * @param updateUserid        always null: the APT function does not return it
 */
public record AptManagementUnitDto(
    Long id,
    String mgmtUnitName,
    Long orgUnitNo,
    String orgUnitName,
    String aptMgmtUnitSt,
    String mgmtUnitNumber,
    String mgmtUnitStatusSt,
    LocalDate statusEffectiveDate,
    Long muId,
    String srcMgmtUnitName,
    String orcsFileNumber,
    String comments,
    LocalDateTime addTimestamp,
    String addUserid,
    LocalDateTime updateTimestamp,
    String updateUserid) {}
