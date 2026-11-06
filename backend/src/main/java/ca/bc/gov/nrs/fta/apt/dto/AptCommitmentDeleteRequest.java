package ca.bc.gov.nrs.fta.apt.dto;

/**
 * The optional body of {@code DELETE /api/fta/apt/commitments/{id}}: the reason, which
 * {@code APT_COMMITMENT_DEL} writes to the commitment's journal. APT2 made it optional.
 *
 * @param reason why the commitment is being deleted; may be blank
 */
public record AptCommitmentDeleteRequest(String reason) {}
