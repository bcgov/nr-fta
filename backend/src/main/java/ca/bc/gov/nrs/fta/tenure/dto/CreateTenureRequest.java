package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * Request body for creating a new forest file / tenure ({@code POST
 * /api/fta/tenures}). Fields mirror the insertable columns of {@code
 * THE.PROV_FOREST_USE} used by the legacy add-tenure flow (FTA_010 / the
 * add path in {@code fta_100_tenure}).
 *
 * <p>No client: legacy's add flow never took a licensee from the user. Clients are added
 * afterwards on the Associated Clients tab.
 */
public record CreateTenureRequest(
    String forestFileId,
    String fileTypeCode,
    String orgUnitCode,
    LocalDate issueDate) {}
