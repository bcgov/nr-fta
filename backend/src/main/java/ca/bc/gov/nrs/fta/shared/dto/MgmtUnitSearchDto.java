package ca.bc.gov.nrs.fta.shared.dto;

import java.time.LocalDate;

/**
 * A management-unit-type code-list entry for search-result rendering.
 *
 * <p>Mirrors the {@code rec_mgmt_unit_type_results} record returned by the
 * legacy Oracle package {@code THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE}
 * (see {@code fta-archive/fta/database/ddl/pkg/PKG_SIL_CODE_LISTS.pks}): the
 * management-unit-type code, its description, and the effective/expiry dates.
 */
public record MgmtUnitSearchDto(
    String mgmtUnitTypeCode,
    String description,
    LocalDate effectiveDate,
    LocalDate expiryDate) {}
