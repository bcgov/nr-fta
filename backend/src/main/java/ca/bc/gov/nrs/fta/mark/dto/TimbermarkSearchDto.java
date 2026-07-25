package ca.bc.gov.nrs.fta.mark.dto;

import java.time.LocalDate;

/**
 * Lightweight summary of a timber mark for search-result rendering.
 *
 * <p>Mirrors the {@code rec_tenure_results} record returned by the legacy
 * Oracle package {@code THE.FTA_002_MARK_SRCH} (see
 * {@code fta-archive/fta/database/ddl/pkg/Fta_002_Mark_Srch.pks}): the
 * identifying columns the timber-mark search returns.
 */
public record TimbermarkSearchDto(
    String orgUnitCode,
    String clientNumber,
    String clientLocnCode,
    String clientName,
    String fileTypeCode,
    String forestFileId,
    String cuttingPermitId,
    String timberMark,
    String certificate,
    String markStatusSt,
    LocalDate markIssueDate,
    LocalDate markExpiryDate,
    String salvageInd,
    Long hvaSkey) {}
