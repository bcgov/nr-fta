package ca.bc.gov.nrs.fta.apt.dto;

/**
 * One client relationship ({@code APT_RELATED_CLIENTS}, read as APT2's
 * {@code APT_RELATED_CLIENTS_GET} read it): a licensee and the corporation that controls it.
 * The three codes together are the row's key.
 *
 * @param clientNumber       the controlled licensee ({@code CLIENT_NUMBER})
 * @param clientName         its name
 * @param parentClientNumber the controlling corporation ({@code CLIENT_NUMBER_PARENTED_BY})
 * @param parentClientName   its name
 * @param relationshipCode   {@code APT_CLIENT_REL_CD}; APT2 only ever wrote {@code CORP}
 *                           ("controls")
 */
public record AptRelatedClientDto(
    String clientNumber,
    String clientName,
    String parentClientNumber,
    String parentClientName,
    String relationshipCode) {}
