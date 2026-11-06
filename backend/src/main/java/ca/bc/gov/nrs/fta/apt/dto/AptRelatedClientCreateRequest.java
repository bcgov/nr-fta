package ca.bc.gov.nrs.fta.apt.dto;

/**
 * Body of {@code POST /api/fta/apt/related-clients}: the arguments APT2 passed to
 * {@code APT_RELATED_CLIENTS_INS}.
 *
 * @param clientNumber       the controlled licensee ({@code in_client_no})
 * @param parentClientNumber the controlling corporation ({@code in_client_no_parent})
 * @param relationshipCode   {@code in_client_rel_cd}; blank means {@code CORP}, the only
 *                           relationship APT2 created
 */
public record AptRelatedClientCreateRequest(
    String clientNumber, String parentClientNumber, String relationshipCode) {}
