package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Request body for creating a new forest file / tenure ({@code POST
 * /api/fta/tenures}). Fields mirror the insertable columns of {@code
 * THE.FOREST_FILE} used by the legacy add-tenure flow (FTA_010 / the
 * add path in {@code fta_100_tenure}).
 */
public record CreateTenureRequest(
    String forestFileId,
    String fileTypeCode,
    String orgUnitCode,
    String clientNumber,
    String clientName,
    LocalDate issueDate) {}
