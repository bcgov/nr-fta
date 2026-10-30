package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * Request body for re-labelling a cut block ({@code POST /api/fta/cut-blocks/{cbSkey}/relabel}),
 * legacy FTA231. The revision count is the one the user read.
 */
public record CutBlockRelabelRequest(String newCutBlockId, String reason, long revisionCount) {}
