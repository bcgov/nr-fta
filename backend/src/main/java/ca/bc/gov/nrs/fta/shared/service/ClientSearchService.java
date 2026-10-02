package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.csv.CsvStreamingJdbc;
import ca.bc.gov.nrs.fta.shared.csv.CsvWriter;
import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.RowCallbackHandler;
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

  private final CsvStreamingJdbc streamingJdbc;

  public ClientSearchService(NamedParameterJdbcTemplate jdbc, CsvStreamingJdbc streamingJdbc) {
    this.jdbc = jdbc;
    this.streamingJdbc = streamingJdbc;
  }

  /**
   * The selected columns.
   *
   * <p>The client name comes from {@link ClientNameSql#displayName}, not from
   * the legacy package's own concatenation. SIL21 builds the name as
   * {@code TRIM(client_name || ', ' || middle || ', ' || first)}, which leaves a
   * company — with no legal first or middle name — reading "ABC LIMITED
   * PARTNERSHIP, ,". The shared helper appends the legal names only when there
   * are some, and it is what every other screen in this application already
   * uses, so a client reads the same everywhere.
   */
  private static final String SELECT_COLUMNS =
      """
      SELECT t2.client_number                              AS client_number,
             t1.client_acronym                             AS client_acronym,
             NVL(t1.client_acronym, t2.client_number)      AS display_client_number,
      """
          + "       " + ClientNameSql.displayName("t2") + " AS client_name,\n"
          + """
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
    MapSqlParameterSource params =
        criteria(clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName);

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

  /**
   * Streams every matching client to a CSV, the same criteria and order as
   * {@link #search} with no paging.
   *
   * <p>Columns match the Client Search table on screen (see the frontend page's
   * {@code HEADERS}); keep the two in step.
   */
  public void exportCsv(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName,
      CsvWriter csv) {

    csv.writeRow(
        "Client acronym",
        "Client number",
        "Location code",
        "Client name",
        "Location",
        "City",
        "Status");

    streamingJdbc.jdbc().query(
        SELECT_COLUMNS + FROM_WHERE + ORDER_BY,
        criteria(clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName),
        // Cast required: a void lambda body matches both the RowCallbackHandler
        // and ResultSetExtractor overloads, so the compiler cannot choose.
        (RowCallbackHandler) rs -> csv.writeRow(
            rs.getString("client_acronym"),
            rs.getString("client_number"),
            rs.getString("client_locn_code"),
            rs.getString("client_name"),
            rs.getString("client_locn_name"),
            rs.getString("city"),
            rs.getString("client_status_code")));
  }

  /**
   * The type-ahead behind the search screens' single client field: up to
   * {@code limit} client/location rows matching what the user has typed.
   *
   * <p>All digits is read as a client number, anything else as a name — rather
   * than OR-ing both, which would stop Oracle using an index for either. Both
   * are prefix matches, as the client search screen's are.
   *
   * <p>The name predicate deliberately does not wrap the column in
   * {@code UPPER()}: {@code FOREST_CLIENT} holds names upper-cased (the legacy
   * tenure and harvesting searches match them raw for the same reason), and the
   * unique index on {@code (CLIENT_NAME, ...)} only serves a prefix scan while
   * the column is left bare. The typed text is upper-cased here instead. If a
   * mixed-case name ever turns up, the fix is a function-based index on
   * {@code UPPER(client_name)} plus {@code UPPER()} on both sides here.
   */
  public List<ClientSearchDto> suggest(String query, int limit) {
    String text = blankToNull(query);
    if (text == null) {
      return List.of();
    }
    String prefix = text.trim().toUpperCase(Locale.CANADA) + "%";
    boolean byNumber = text.trim().chars().allMatch(Character::isDigit);

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("prefix", prefix)
        .addValue("limit", limit);

    String predicate = byNumber
        ? " WHERE t2.client_number LIKE :prefix\n"
        : " WHERE t2.client_name LIKE :prefix\n";

    return jdbc.query(
        SELECT_COLUMNS + SUGGEST_FROM + predicate + SUGGEST_ORDER_BY
            + " FETCH FIRST :limit ROWS ONLY",
        params,
        ROW_MAPPER);
  }

  /**
   * The suggest query's tables. Same three as the search, minus its predicates —
   * a type-ahead filters on one typed value, not the screen's five criteria.
   */
  private static final String SUGGEST_FROM =
      """
        FROM the.forest_client t2
        JOIN the.client_location t3    ON t3.client_number = t2.client_number
        LEFT JOIN the.client_acronym t1 ON t1.client_number = t2.client_number
      """;

  private static final String SUGGEST_ORDER_BY =
      " ORDER BY t2.client_name, t2.client_number, t3.client_locn_code\n";

  private static MapSqlParameterSource criteria(
      String clientNumber,
      String clientAcronym,
      String clientName,
      String legalFirstName,
      String legalMiddleName) {
    return new MapSqlParameterSource()
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientAcronym", blankToNull(clientAcronym))
        .addValue("clientName", blankToNull(clientName))
        .addValue("legalFirstName", blankToNull(legalFirstName))
        .addValue("legalMiddleName", blankToNull(legalMiddleName));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
