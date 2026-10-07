package ca.bc.gov.nrs.fta.mark.dto;

import java.math.BigDecimal;

/**
 * Request body for requesting an amendment to a private mark ({@code POST
 * /api/fta/marks/{id}/amendments}) — the FTA512 form.
 *
 * @param permitBlockArea  Requested Area (ha): 0 to 9999.9, one decimal; optional (legacy
 *                         stored 0.0 when blank)
 * @param requestedChanges Requested Amendment Changes, up to 2000 characters; required
 */
public record MarkAmendmentRequest(BigDecimal permitBlockArea, String requestedChanges) {}
