package ca.bc.gov.nrs.fta.mark.dto;

import java.time.LocalDate;

/**
 * Request body for updating a land index already on a private mark ({@code PUT
 * /api/fta/marks/{id}/land-index/{skey}}) — FTA511's save of an existing row.
 *
 * @param primaryLandIndexCode   Land District/Island; required
 * @param secondaryLandIndexCode Primary ID; optional
 * @param markLandIndexDesc      free-text description, up to 40 characters; optional
 * @param indexDeactivateDate    when the index stopped applying; optional
 * @param revisionCount          the row's revision count as read, for optimistic locking
 */
public record MarkLandIndexUpdateRequest(
    String primaryLandIndexCode,
    String secondaryLandIndexCode,
    String markLandIndexDesc,
    LocalDate indexDeactivateDate,
    Integer revisionCount) {}
