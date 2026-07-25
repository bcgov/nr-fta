package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Client search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_SIL_21_CLIENT_SEARCH_V002}
 * (SIL21 client search) to a native query against the shared {@code THE} schema.
 * The SELECT column list and WHERE clause mirror the package body's
 * {@code get_client_search} cursor — each filter is applied only when its bind
 * value is supplied (NVL-style), matching the legacy behaviour. Column selection
 * matches the package's {@code rec_client_search_results} record.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class ClientSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public ClientSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT t2.client_number                              AS client_number,
             t1.client_acronym                             AS client_acronym,
             NVL(t1.client_acronym, t2.client_number)      AS display_client_number,
             TRIM(t2.client_name
                  || ', ' || TRIM(t2.legal_middle_name)
                  || ', ' || TRIM(t2.legal_first_name))    AS client_name,
             t2.legal_first_name                           AS legal_first_name,
             t2.legal_middle_name                          AS legal_middle_name,
             t3.client_locn_code                           AS client_locn_code,
             t3.client_locn_name                           AS client_locn_name,
             t3.city                                       AS city,
             t2.client_status_code                         AS client_status_code
        FROM the.forest_client t2
        JOIN the.client_location t3    ON t3.client_number = t2.client_number
        LEFT JOIN the.client_acronym t1 ON t1.client_number = t2.client_number
       WHERE (:clientAcronym  IS NULL OR t1.client_acronym LIKE UPPER(:clientAcronym) || '%')
         AND (:clientNumber   IS NULL OR t2.client_number = :clientNumber)
         AND (:clientName     IS NULL OR UPPER(t2.client_name) LIKE UPPER(:clientName) || '%')
         AND (:legalFirstName IS NULL OR UPPER(t2.legal_first_name) LIKE UPPER(:legalFirstName) || '%')
         AND (:legalMiddleName IS NULL OR UPPER(t2.legal_middle_name) LIKE UPPER(:legalMiddleName) || '%')
       ORDER BY t2.client_name,
                t2.legal_first_name,
                t2.legal_middle_name,
                t3.client_locn_code
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Client search — mirrors {@code FTA_SIL_21_CLIENT_SEARCH_V002.get_client_search}.
   *
   * @param clientNumber    exact client number, or null
   * @param clientAcronym   client acronym (prefix match), or null
   * @param clientName      client name (prefix match), or null
   * @param legalFirstName  legal first name (prefix match), or null
   * @param legalMiddleName legal middle name (prefix match), or null
   */
  public List<ClientSearchDto> search(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientAcronym", blankToNull(clientAcronym))
        .addValue("clientName", blankToNull(clientName))
        .addValue("legalFirstName", blankToNull(legalFirstName))
        .addValue("legalMiddleName", blankToNull(legalMiddleName));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new ClientSearchDto(
        rs.getString("client_number"),
        rs.getString("client_acronym"),
        rs.getString("display_client_number"),
        rs.getString("client_name"),
        rs.getString("legal_first_name"),
        rs.getString("legal_middle_name"),
        rs.getString("client_locn_code"),
        rs.getString("client_locn_name"),
        rs.getString("city"),
        rs.getString("client_status_code")));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
