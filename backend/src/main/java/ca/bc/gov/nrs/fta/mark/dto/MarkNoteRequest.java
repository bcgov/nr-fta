package ca.bc.gov.nrs.fta.mark.dto;

/**
 * Request body for adding a note to a private mark ({@code POST
 * /api/fta/marks/{id}/notes}). Mirrors the {@code p_note} input of the legacy
 * {@code FTA_970_FOREST_NOTE.ADD}.
 */
public record MarkNoteRequest(String note) {}
