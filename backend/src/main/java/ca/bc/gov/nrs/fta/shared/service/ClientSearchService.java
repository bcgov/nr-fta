package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Client search business logic.
 *
 * <p>Ports the legacy Oracle package
 * {@code THE.FTA_SIL_21_CLIENT_SEARCH_V002.get_client_search}. Every criterion
 * is a prefix match except the client number, which the legacy package matches
 * exactly — a partial client number returning near-misses would be worse than
 * returning nothing.
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

  private static final String SELECT_COLUMNS =
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
      """;

  /**
   * The tables and predicates, shared verbatim by the page query and the count,
   * so a count can never filter differently from the rows it is counting.
   */
  private static final String FROM_WHERE =
      """
        FROM the.forest_client t2
        JOIN the.client_location t3    ON t3.client_number = t2.client_number
        LEFT JOIN the.client_acronym t1 ON t1.client_number = t2.client_number
       WHERE (:clientAcronym  IS NULL OR t1.client_acronym LIKE UPPER(:clientAcronym) || '%')
         AND (:clientNumber   IS NULL OR t2.client_number = :clientNumber)
         AND (:clientName     IS NULL OR UPPER(t2.client_name) LIKE UPPER(:clientName) || '%')
         AND (:legalFirstName IS NULL
              OR UPPER(t2.legal_first_name) LIKE UPPER(:legalFirstName) || '%')
         AND (:legalMiddleName IS NULL
              OR UPPER(t2.legal_middle_name) LIKE UPPER(:legalMiddleName) || '%')
      """;

  // The legacy cursor's order. Deterministic, so a row cannot appear on two
  // different pages once OFFSET is applied.
  private static final String ORDER_BY =
      """
       ORDER BY t2.client_name,
                t2.legal_first_name,
                t2.legal_middle_name,
                t3.client_locn_code
      """;

  private static final RowMapper<ClientSearchDto> ROW_MAPPER =
      (rs, rowNum) -> new ClientSearchDto(
          rs.getString("client_number"),
          rs.getString("client_acronym"),
          rs.getString("display_client_number"),
          rs.getString("client_name"),
          rs.getString("legal_first_name"),
          rs.getString("legal_middle_name"),
          rs.getString("client_locn_code"),
          rs.getString("client_locn_name"),
          rs.getString("city"),
          rs.getString("client_status_code"));

  /**
   * Client search — mirrors {@code FTA_SIL_21_CLIENT_SEARCH_V002.get_client_search}.
   *
   * @param clientNumber    exact client number, or null
   * @param clientAcronym   client acronym (prefix match), or null
   * @param clientName      client surname / name (prefix match), or null
   * @param legalFirstName  legal first name (prefix match), or null
   * @param legalMiddleName legal middle name (prefix match), or null
   * @param page            0-indexed page number
   * @param size            rows per page
   */
  public PagedResponse<ClientSearchDto> search(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName,
      int page,
      int size) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientAcronym", blankToNull(clientAcronym))
        .addValue("clientName", blankToNull(clientName))
        .addValue("legalFirstName", blankToNull(legalFirstName))
        .addValue("legalMiddleName", blankToNull(legalMiddleName));

    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + FROM_WHERE, params, Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(params.getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<ClientSearchDto> rows = jdbc.query(
        SELECT_COLUMNS + FROM_WHERE + ORDER_BY
            + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        ROW_MAPPER);

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
