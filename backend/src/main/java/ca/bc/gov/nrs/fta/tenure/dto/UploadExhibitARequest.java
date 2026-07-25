package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * Request body for uploading the Exhibit A image for a tenure application
 * ({@code POST /api/fta/exhibit-a/{esfId}/upload}). Fields mirror the input
 * parameters of the legacy {@code FTA_307_UPLOAD_EXHIBIT_A.UPLOAD} procedure;
 * the tenure application id comes from the path, not the body.
 *
 * <p>{@code imageBase64} carries the Exhibit A submission (PDF map) as a
 * base64-encoded string; it is decoded to the {@code exhibit_a_image} BLOB.
 * {@code revisionCount} / {@code taiRevisionCount} are the optimistic-lock
 * revision counts of the {@code TENURE_APPLICATION} and
 * {@code TENURE_APPLICATION_IMAGE} rows respectively.
 */
public record UploadExhibitARequest(
    Integer revisionCount,
    Integer taiRevisionCount,
    String imageBase64) {}
