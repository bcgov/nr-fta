package ca.bc.gov.nrs.fta.shared.dto;

/**
 * One option in a code-list dropdown.
 *
 * <p>The {@code THE} code tables all share the same shape — a code column, a
 * {@code DESCRIPTION}, and an effective/expiry pair — so one envelope serves
 * every list rather than a DTO per table.
 *
 * @param code        the value submitted back as search criteria
 * @param description the label shown to the user
 */
public record CodeOptionDto(String code, String description) {}
