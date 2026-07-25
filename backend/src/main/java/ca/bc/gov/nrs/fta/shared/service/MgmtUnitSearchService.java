package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.MgmtUnitSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Management-unit-type code-list search business logic.
 *
 * <p>Ports the legacy Oracle package
 * {@code THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE} to a native query
 * against the shared {@code THE} schema. Column selection matches the package's
 * {@code rec_mgmt_unit_type_results} record and the body's cursor (which returns
 * {@code MGMT_UNIT_TYPE_CODE || ' - ' || DESCRIPTION} as the description). The
 * mainline procedure exposes no filter inputs — it returns the full ordered
 * list — so the optional {@code mgmtUnitTypeCode}/{@code description} filters are
 * applied NVL-style (only when supplied), preserving the legacy "return all"
 * default.
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

  private static final String SEARCH_SQL =
      """
      SELECT mut.mgmt_unit_type_code                            AS mgmt_unit_type_code,
             mut.mgmt_unit_type_code || ' - ' || mut.description AS description,
             mut.effective_date                                 AS effective_date,
             mut.expiry_date                                    AS expiry_date
        FROM the.mgmt_unit_type_code mut
       WHERE (:mgmtUnitTypeCode IS NULL
              OR UPPER(mut.mgmt_unit_type_code) LIKE UPPER(:mgmtUnitTypeCode) || '%')
         AND (:description IS NULL
              OR UPPER(mut.description) LIKE '%' || UPPER(:description) || '%')
       ORDER BY mut.mgmt_unit_type_code
      """;

  /**
   * Management-unit-type code-list search — mirrors
   * {@code PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE}.
   *
   * @param mgmtUnitTypeCode partial management-unit-type code (prefix match), or null
   * @param description      management-unit-type description (contains match), or null
   */
  public List<MgmtUnitSearchDto> search(String mgmtUnitTypeCode, String description) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("mgmtUnitTypeCode", blankToNull(mgmtUnitTypeCode))
        .addValue("description", blankToNull(description));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new MgmtUnitSearchDto(
        rs.getString("mgmt_unit_type_code"),
        rs.getString("description"),
        rs.getObject("effective_date", java.time.LocalDate.class),
        rs.getObject("expiry_date", java.time.LocalDate.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
