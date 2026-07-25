package ca.bc.gov.nrs.fta.range.dto;

import java.time.LocalDate;

/**
 * Lightweight summary of a range tenure / agreement for search-result
 * rendering.
 *
 * <p>Mirrors the {@code rec_tenure_results} record returned by the legacy
 * Oracle package {@code THE.FTA_001R_TENR_SRCH} (Range Tenure Search — see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_001R_TENR_SRCH.pks}): the
 * identifying columns the range tenure search returns.
 */
public record RangeTenureSearchDto(
    String orgUnitCode,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String forestFileId,
    String fileTypeCode,
    String fileClientTypeDesc,
    String mgmtUnitType,
    String mgmtUnitId,
    String fileStatusCode,
    String fileStatusDesc,
    LocalDate issueDate,
    LocalDate expiryDate) {}
