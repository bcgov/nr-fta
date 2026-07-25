package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Range unit / pasture search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_006_RU_SRCH} (range unit /
 * pasture search) to a native query against the shared {@code THE} schema. The
 * WHERE clause mirrors the package body's {@code get_list} cursor — each filter
 * is applied only when its bind value is supplied (NVL/LIKE-style), matching the
 * legacy behaviour. Column selection matches the package's
 * {@code rec_range_unit_results} record.
 *
 * <p>The legacy body first resolves {@code p_org_unit_no} to an
 * {@code org_unit_code}, then restricts to range units whose administering
 * district rolls up to (or equals) that org unit; that lookup is expressed here
 * as the correlated {@code IN (...)} sub-query.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RangeUnitSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public RangeUnitSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT ru.range_unit_id     AS range_unit_id,
             rp.pasture_id        AS pasture_id,
             ru.range_unit_name   AS range_unit_name,
             rp.pasture_name      AS pasture_name,
             rusc.description     AS range_unit_status_desc
        FROM the.range_unit ru
        JOIN the.range_unit_pasture rp
              ON rp.range_unit_id = ru.range_unit_id
        JOIN the.org_unit ou
              ON ou.org_unit_no = ru.admin_forest_district_no
        JOIN the.range_unit_status_code rusc
              ON rusc.range_unit_status_code = ru.range_unit_status_code
       WHERE (:rangeStatus   IS NULL OR ru.range_unit_status_code LIKE UPPER(:rangeStatus) || '%')
         AND (:pastureName   IS NULL OR UPPER(rp.pasture_name) LIKE UPPER(:pastureName) || '%')
         AND (:rangeUnitName IS NULL OR UPPER(ru.range_unit_name) LIKE UPPER(:rangeUnitName) || '%')
         AND (:orgUnitNo     IS NULL OR ru.admin_forest_district_no IN (
                SELECT o2.org_unit_no
                  FROM the.org_unit o2
                 WHERE o2.rollup_region_code = (SELECT o3.org_unit_code FROM the.org_unit o3 WHERE o3.org_unit_no = :orgUnitNo)
                    OR o2.org_unit_code     = (SELECT o3.org_unit_code FROM the.org_unit o3 WHERE o3.org_unit_no = :orgUnitNo)))
       ORDER BY ou.org_unit_code, rp.range_unit_id, rp.pasture_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Range unit / pasture search — mirrors {@code FTA_006_RU_SRCH.get_list}.
   *
   * @param orgUnitNo     administering org-unit number (rollup/exact match), or null
   * @param rangeUnitName range unit name (prefix match), or null
   * @param pastureName   pasture name (prefix match), or null
   * @param rangeStatus   range unit status code (prefix match), or null
   */
  public List<RangeUnitSearchDto> search(
      String orgUnitNo,
      String rangeUnitName,
      String pastureName,
      String rangeStatus) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("orgUnitNo", blankToNull(orgUnitNo))
        .addValue("rangeUnitName", blankToNull(rangeUnitName))
        .addValue("pastureName", blankToNull(pastureName))
        .addValue("rangeStatus", blankToNull(rangeStatus));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new RangeUnitSearchDto(
        rs.getString("range_unit_id"),
        rs.getString("pasture_id"),
        rs.getString("range_unit_name"),
        rs.getString("pasture_name"),
        rs.getString("range_unit_status_desc")));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
