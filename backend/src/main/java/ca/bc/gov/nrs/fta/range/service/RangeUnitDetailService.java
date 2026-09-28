package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitDetailDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Range unit / pasture detail business logic.
 *
 * <p>Ports the {@code GET} procedure of the legacy Oracle package
 * {@code THE.FTA_630_MN_RG_UN_PST} to native queries against the shared
 * {@code THE} schema. The first query mirrors the tombstone {@code SELECT} that
 * populates the mainline OUT parameters (status, region, district, admin zone,
 * revision count); the second mirrors the {@code p_m_r_u_p_results} pasture
 * cursor. Derived description strings are assembled exactly as the package body
 * does after the fetch.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RangeUnitDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public RangeUnitDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Region resolution follows the package body, which calls
  // SIL_GET_USER_REGION(admin_forest_district_no) and then get_region_name().
  // That function is expressed as joins instead: find the district's org unit,
  // follow its rollup_dist_code to that org unit, and take its
  // rollup_region_code. Null when any link is missing, as the function returns
  // null on NO_DATA_FOUND. The region-name join then matches that code against
  // ORG_UNIT.ROLLUP_DIST_CODE, as get_region_name() does.
  private static final String UNIT_SQL =
      """
      SELECT ru.range_unit_id                              AS range_unit_id,
             ru.range_unit_name                            AS range_unit_name,
             ru.range_unit_status_code                     AS status_code,
             rusc.description                              AS status_raw_desc,
             ru.status_date                                AS status_date,
             rollup_dist.rollup_region_code                AS region,
             rgn.org_unit_name                             AS region_name,
             TO_CHAR(ru.admin_forest_district_no)          AS district,
             dist.org_unit_code || ' - ' || dist.org_unit_name AS district_description,
             ru.district_admn_zone                         AS district_admin_zone,
             ru.revision_count                             AS revision_count
        FROM the.range_unit ru
        JOIN the.org_unit dist ON dist.org_unit_no = ru.admin_forest_district_no
        LEFT JOIN the.org_unit rollup_dist
               ON rollup_dist.org_unit_code = dist.rollup_dist_code
        LEFT JOIN the.range_unit_status_code rusc
               ON rusc.range_unit_status_code = ru.range_unit_status_code
        LEFT JOIN the.org_unit rgn
               ON rgn.rollup_dist_code = rollup_dist.rollup_region_code
       WHERE ru.range_unit_id = :rangeUnitId
      """;

  private static final String PASTURES_SQL =
      """
      SELECT pasture_id                    AS pasture_id,
             pasture_name                  AS pasture_name,
             revision_count                AS pasture_revision_count
        FROM the.range_unit_pasture
       WHERE range_unit_id = :rangeUnitId
       ORDER BY pasture_id
      """;

  /**
   * Range unit detail — mirrors {@code FTA_630_MN_RG_UN_PST.GET}.
   *
   * @param rangeUnitId the range unit id (e.g. {@code RU0576})
   * @return the range unit detail with its pastures, or {@code null} when no
   *     range unit matches the id
   */
  public RangeUnitDetailDto getRangeUnitDetail(String rangeUnitId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("rangeUnitId", rangeUnitId);

    List<RangeUnitDetailDto.Pasture> pastures =
        jdbc.query(PASTURES_SQL, params, (rs, rowNum) -> new RangeUnitDetailDto.Pasture(
            rs.getString("pasture_id"),
            rs.getString("pasture_name"),
            rs.getObject("pasture_revision_count", Integer.class)));

    List<RangeUnitDetailDto> units =
        jdbc.query(UNIT_SQL, params, (rs, rowNum) -> {
          String statusCode = rs.getString("status_code");
          String statusRawDesc = rs.getString("status_raw_desc");
          // Mirrors GET: status_description := status || ' - ' || description.
          String statusDescription =
              statusCode != null ? statusCode + " - " + (statusRawDesc == null ? "" : statusRawDesc) : " ";

          String region = rs.getString("region");
          String regionName = rs.getString("region_name");
          // Mirrors GET: region_description := region || ' - ' || get_region_name(region).
          String regionDescription = region + " - " + (regionName == null ? "" : regionName);

          return new RangeUnitDetailDto(
              rs.getString("range_unit_id"),
              rs.getString("range_unit_name"),
              statusCode,
              statusDescription,
              rs.getObject("status_date", java.time.LocalDate.class),
              region,
              regionDescription,
              rs.getString("district"),
              rs.getString("district_description"),
              rs.getString("district_admin_zone"),
              rs.getObject("revision_count", Integer.class),
              pastures);
        });

    return units.isEmpty() ? null : units.get(0);
  }
}
