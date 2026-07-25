package ca.bc.gov.nrs.fta.shared.dto;

/**
 * Client search result row.
 *
 * <p>Mirrors the {@code rec_client_search_results} record returned by the legacy
 * Oracle package {@code THE.FTA_SIL_21_CLIENT_SEARCH_V002} (see
 * {@code fta-archive/fta/database/ddl/pkg/FTA_SIL_21_CLIENT_SEARCH_V002.pks}):
 * the forest-client / location columns the SIL21 client search returns.
 */
public record ClientSearchDto(
    String clientNumber,
    String clientAcronym,
    String displayClientNumber,
    String clientName,
    String legalFirstName,
    String legalMiddleName,
    String clientLocnCode,
    String clientLocnName,
    String city,
    String clientStatusCode) {}
