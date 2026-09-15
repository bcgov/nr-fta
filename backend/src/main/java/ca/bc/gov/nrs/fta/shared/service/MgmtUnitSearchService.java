package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.MgmtUnitSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Management-unit-type code-list search.
 *
 * <p>Ports {@code THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE}, which takes
 * no filters of its own — the legacy screen offers only a type dropdown. The
 * code and description filters here narrow that list rather than reproducing a
 * legacy behaviour.
 *
 * <p>This is a small reference table, so paging rarely engages; it is applied
 * anyway so every search screen returns the same envelope and renders the same
 * results panel.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class MgmtUnitSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public MgmtUnitSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT_COLUMNS =
      """
      SELECT mut.mgmt_unit_type_code                            AS mgmt_unit_type_code,
             mut.mgmt_unit_type_code || ' - ' || mut.description AS description,
             mut.effective_date                                 AS effective_date,
             mut.expiry_date                                    AS expiry_date
      """;

  /**
   * The table and predicates, shared verbatim by the page query and the count,
   * so a count can never filter differently from the rows it is counting.
   */
  private static final String FROM_WHERE =
      """
        FROM the.mgmt_unit_type_code mut
       WHERE (:mgmtUnitTypeCode IS NULL
              OR UPPER(mut.mgmt_unit_type_code) LIKE UPPER(:mgmtUnitTypeCode) || '%')
         AND (:description IS NULL
              OR UPPER(mut.description) LIKE '%' || UPPER(:description) || '%')
      """;

  private static final String ORDER_BY = " ORDER BY mut.mgmt_unit_type_code\n";

  private static final RowMapper<MgmtUnitSearchDto> ROW_MAPPER =
      (rs, rowNum) -> new MgmtUnitSearchDto(
          rs.getString("mgmt_unit_type_code"),
          rs.getString("description"),
          rs.getObject("effective_date", java.time.LocalDate.class),
          rs.getObject("expiry_date", java.time.LocalDate.class));

  /**
   * Management-unit-type code-list search — mirrors
   * {@code PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE}.
   *
   * @param mgmtUnitTypeCode partial management-unit-type code (prefix match), or null
   * @param description      management-unit-type description (contains match), or null
   * @param page             0-indexed page number
   * @param size             rows per page
   */
  public PagedResponse<MgmtUnitSearchDto> search(
      String mgmtUnitTypeCode, String description, int page, int size) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("mgmtUnitTypeCode", blankToNull(mgmtUnitTypeCode))
        .addValue("description", blankToNull(description));

    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + FROM_WHERE, params, Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(params.getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<MgmtUnitSearchDto> rows = jdbc.query(
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
