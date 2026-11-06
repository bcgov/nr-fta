package ca.bc.gov.nrs.fta.apt.dto;

import java.time.LocalDate;

/**
 * The body of {@code PUT /api/fta/apt/licences/{forestFileId}}, bound to
 * {@code APT_LICENCE_INS_UPD}, which inserts APT's row for the licence or updates it. APT2's
 * field names.
 *
 * @param foaType         form of agreement type code, required
 * @param replacementDate optional
 * @param replacementTerm months, 0 to 999, optional
 * @param comments        up to 2000 characters, optional
 */
public record AptLicenceUpdateRequest(
    String foaType, LocalDate replacementDate, Integer replacementTerm, String comments) {}
