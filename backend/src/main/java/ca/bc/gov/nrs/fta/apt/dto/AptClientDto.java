package ca.bc.gov.nrs.fta.apt.dto;

/**
 * A forest client as the APT client picker lists it: a row of {@code APT_CLIENTS_GET} (the
 * related-client search).
 *
 * @param clientNumber {@code FOREST_CLIENT.CLIENT_NUMBER}, eight digits
 * @param clientName   {@code CLIENT_NAME}
 */
public record AptClientDto(String clientNumber, String clientName) {}
