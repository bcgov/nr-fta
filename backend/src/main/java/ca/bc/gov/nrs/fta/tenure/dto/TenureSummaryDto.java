package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Lightweight summary of a harvesting authority / tenure for search-result
 * rendering.
 *
 * <p>Mirrors the {@code rec_tenure_results} record returned by the legacy
 * Oracle package {@code THE.FTA_001_TENR_SRCH} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_001_TENR_SRCH.PKS}): the
 * identifying columns the common tenure search returns.
 */
public record TenureSummaryDto(
    String orgUnitCode,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String forestFileId,
    String fileTypeCode,
    String fileClientTypeDesc,
    /** Both come from the package's record; null on the table path until its SQL joins them. */
    String mgmtUnitType,
    String mgmtUnitId,
    String fileStatusCode,
    String fileStatusDesc,
    /** {@code VARCHAR2(10)} in the legacy record; parsed on the way out. */
    LocalDate issueDate,
    LocalDate expiryDate) {}
