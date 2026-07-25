package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Manage Range Zone business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_631_RANGE_ZONE} (its
 * {@code GET} action) to a native query against the shared {@code THE} schema.
 * The SQL and column selection are taken directly from the package body's
 * {@code get} procedure — {@code RANGE_ZONE} left-joined to {@code ORG_UNIT} on
 * the administering forest district. The WHERE clause mirrors the package
 * filters (district, and optionally the zone code) — each filter is applied
 * only when its bind value is supplied (NVL-style), matching the legacy
 * behaviour.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class ManageZoneService {

  private final NamedParameterJdbcTemplate jdbc;

  public ManageZoneService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT ranz.range_zone_code           AS range_zone_code,
             ranz.zone_description          AS zone_description,
             ranz.admin_forest_district_no  AS admin_forest_district_no,
             ranz.contact                   AS contact,
             ranz.contact_user_id           AS contact_user_id,
             ranz.contact_phone_number      AS contact_phone_number,
             ranz.contact_email_address     AS contact_email_address,
             ranz.revision_count            AS revision_count,
             org.org_unit_code              AS org_unit_code,
             org.org_unit_name              AS org_unit_name
        FROM the.range_zone ranz
        LEFT JOIN the.org_unit org
               ON ranz.admin_forest_district_no = org.org_unit_no
       WHERE (:adminForestDistrictNo IS NULL OR ranz.admin_forest_district_no = :adminForestDistrictNo)
         AND (:rangeZoneCode IS NULL OR ranz.range_zone_code = :rangeZoneCode)
       ORDER BY ranz.range_zone_code
      """;

  /**
   * Retrieve range zones — mirrors {@code FTA_631_RANGE_ZONE.get}.
   *
   * @param adminForestDistrictNo administering forest-district org-unit number, or null
   * @param rangeZoneCode         exact range-zone code, or null
   */
  public List<ManageZoneDto> search(String adminForestDistrictNo, String rangeZoneCode) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("adminForestDistrictNo", blankToNull(adminForestDistrictNo))
        .addValue("rangeZoneCode", blankToNull(rangeZoneCode));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new ManageZoneDto(
        rs.getString("range_zone_code"),
        rs.getString("zone_description"),
        rs.getObject("admin_forest_district_no", Long.class),
        rs.getString("contact"),
        rs.getString("contact_user_id"),
        rs.getString("contact_phone_number"),
        rs.getString("contact_email_address"),
        rs.getObject("revision_count", Long.class),
        rs.getString("org_unit_code"),
        rs.getString("org_unit_name")));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
