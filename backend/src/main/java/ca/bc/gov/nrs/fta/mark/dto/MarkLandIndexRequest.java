package ca.bc.gov.nrs.fta.mark.dto;

/**
 * Request body for adding a land index to a private mark ({@code POST
 * /api/fta/marks/{id}/land-index}) — the FTA511 add row.
 *
 * @param primaryLandIndexCode   Land District/Island (PRIMARY_LAND_INDEX_CODE); required
 * @param secondaryLandIndexCode Primary ID (SECONDARY_LAND_INDEX_CODE); optional
 * @param markLandIndexDesc      free-text description, up to 40 characters; optional
 */
public record MarkLandIndexRequest(
    String primaryLandIndexCode,
    String secondaryLandIndexCode,
    String markLandIndexDesc) {}
