package ca.bc.gov.nrs.fta.mark.dto;

/**
 * Request body for submitting a new private mark application ({@code POST
 * /api/fta/marks}). Fields mirror the insertable columns of {@code
 * THE.PRIVATE_MARK_CERTIFICATE} used by the legacy add path of {@code
 * fta_510_private_mark} ({@code add_new} → {@code create_certificate}).
 */
public record MarkApplicationRequest(
    String markNumber,
    String holderName,
    String holderClient,
    String orgUnit,
    String timberOrigin) {}
