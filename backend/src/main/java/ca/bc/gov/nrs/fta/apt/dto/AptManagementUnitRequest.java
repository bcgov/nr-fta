package ca.bc.gov.nrs.fta.apt.dto;

import java.time.LocalDate;

/**
 * A management unit to create or update ({@code APT_MANAGEMENT_UNIT_INS_UPD}). The id comes from
 * the path and the audit user from the token, never from the body.
 *
 * @param orgUnitNo      the region or district; required
 * @param muType         the unit type code; required, one character
 * @param muNo           the unit number; required, up to 4 characters
 * @param status         the status code; required
 * @param effectiveDate  when the status took effect; required
 * @param muId           the source management unit, if any
 * @param orcsFileNumber the ORCS file number, up to 13 characters
 * @param comments       free text, up to 2000 characters
 */
public record AptManagementUnitRequest(
    Long orgUnitNo,
    String muType,
    String muNo,
    String status,
    LocalDate effectiveDate,
    Long muId,
    String orcsFileNumber,
    String comments) {}
