package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitDetailDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
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
 * populates the mainline OUT parameters (status, district, admin zone,
 * revision count), with the region as legacy's action derived it; the second
 * mirrors the {@code p_m_r_u_p_results} pasture cursor. Derived description strings are assembled exactly as the package body
 * does after the fetch.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RangeUnitDetailService {

  private final NamedParameterJdbcTemplate jdbc;
  private final LoggedUserHelper auth;

  public RangeUnitDetailService(NamedParameterJdbcTemplate jdbc, LoggedUserHelper auth) {
    this.jdbc = jdbc;
    this.auth = auth;
  }

  // Region follows legacy's action (Fta630MnRgUnPstAction.handleGet), which replaced the
  // package's region description: SIL_GET_REGION_NO(district) — the district's ROLLUP_REGION_NO —
  // described from sil.lookup.orgunit (PKG_SIL_CODE_LISTS.GET_ORG_UNIT: ORG_UNIT_CODE || ' - ' ||
  // ORG_UNIT_NAME of an unexpired region, district or HQ unit). Nothing when the region is not in
  // that list, as legacy then showed "".
  private static final String UNIT_SQL =
      """
      SELECT ru.range_unit_id                              AS range_unit_id,
             ru.range_unit_name                            AS range_unit_name,
             ru.range_unit_status_code                     AS status_code,
             rusc.description                              AS status_raw_desc,
             ru.status_date                                AS status_date,
             TO_CHAR(dist.rollup_region_no)                AS region,
             CASE WHEN rgn.org_unit_no IS NOT NULL
                  THEN rgn.org_unit_code || ' - ' || rgn.org_unit_name
             END                                           AS region_description,
             TO_CHAR(ru.admin_forest_district_no)          AS district,
             dist.org_unit_code || ' - ' || dist.org_unit_name AS district_description,
             dist.org_unit_code                            AS district_code,
             ru.district_admn_zone                         AS district_admin_zone,
             ru.revision_count                             AS revision_count
        FROM the.range_unit ru
        JOIN the.org_unit dist ON dist.org_unit_no = ru.admin_forest_district_no
        LEFT JOIN the.range_unit_status_code rusc
               ON rusc.range_unit_status_code = ru.range_unit_status_code
        LEFT JOIN the.org_unit rgn
               ON rgn.org_unit_no = dist.rollup_region_no
              AND rgn.expiry_date > SYSDATE
              AND (rgn.org_unit_no = rgn.rollup_region_no
                   OR rgn.org_unit_no = rgn.rollup_dist_no
                   OR (rgn.org_unit_code LIKE 'H%' AND LENGTH(rgn.org_unit_code) = 3))
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

          String districtCode = rs.getString("district_code");
          return new RangeUnitDetailDto(
              rs.getString("range_unit_id"),
              rs.getString("range_unit_name"),
              statusCode,
              statusDescription,
              rs.getObject("status_date", java.time.LocalDate.class),
              rs.getString("region"),
              rs.getString("region_description"),
              rs.getString("district"),
              rs.getString("district_description"),
              districtCode,
              rs.getString("district_admin_zone"),
              rs.getObject("revision_count", Integer.class),
              districtCode != null && auth.mayEditRangeDistrict(districtCode),
              pastures);
        });

    return units.isEmpty() ? null : units.get(0);
  }
}
