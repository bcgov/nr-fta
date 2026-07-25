package ca.bc.gov.nrs.fta.mark.dto;

import java.time.LocalDate;

/**
 * A single row in the Private Mark Application/Amendment list.
 *
 * <p>Mirrors the {@code rec_mark_list} record returned by the legacy Oracle
 * package {@code THE.FTA_500_MARK_LIST} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_500_MARK_LIST.PKS}): the columns
 * the {@code GET} procedure's cursor exposes for the marks/amendments list.
 */
public record MarkListDto(
    String processType,
    String certificate,
    String timberMark,
    LocalDate markApplDate,
    String orgUnitCode,
    String markStatusSt,
    String clientName,
    String disablePrintInd,
    String disableAckInd,
    Long tmRevisionCount,
    Long amendRevisionCount,
    String idir) {}
