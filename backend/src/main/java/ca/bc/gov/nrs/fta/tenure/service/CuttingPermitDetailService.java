package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitDetailDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cutting permit detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_902_CP_DETAIL} (the FTA902
 * Cutting Permit / Timber Mark detail screen) to a native query against the
 * shared {@code THE} schema. The column selection mirrors the package's
 * {@code GET} / {@code GET_TIMBER_MARK} procedures, which select the mark-level
 * data from {@code HARVESTING_AUTHORITY} joined to
 * {@code HARVESTING_HAULING_XREF} (primary mark), {@code HAULING_AUTHORITY},
 * {@code HARVEST_AUTHORITY_GEOM} and {@code PROV_FOREST_USE}.
 *
 * <p>NOTE: the descriptive lookups (status/file-type descriptions, licensee via
 * {@code FOREST_FILE_CLIENT}/{@code CLIENT} and admin org via
 * {@code ORG_UNIT}) are derived from the file-header helpers the package calls
 * ({@code Fta_Get_File_Header}) rather than reproduced verbatim; they follow the
 * same conventions as the tenure-search port. The lookup is filtered by
 * cutting-permit id, with an optional forest-file id filter applied only when
 * supplied (NVL-style), matching the legacy behaviour.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class CuttingPermitDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public CuttingPermitDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String DETAIL_SQL =
      """
      SELECT hva.forest_file_id                AS forest_file_id,
             hva.cutting_permit_id             AS cutting_permit_id,
             xref.timber_mark                  AS timber_mark,
             pfu.file_type_code                AS file_type_code,
             ftc.description                   AS file_type_description,
             ou.org_unit_code                  AS admin_org_code,
             cli.client_name                   AS licensee,
             hva.harvest_auth_status_code      AS status_code,
             hsc.description                   AS status_desc,
             hva.status_date                   AS status_date,
             hva.issue_date                    AS issue_date,
             hva.expiry_date                   AS expiry_date,
             hva.extend_date                   AS extend_date,
             hva.harvest_auth_extend_reas_code AS extend_reason_code,
             hva.extend_count                  AS extend_count,
             FLOOR(hva.tenure_term / 12)       AS tenure_term_years,
             MOD(hva.tenure_term, 12)          AS tenure_term_months,
             hva.forest_district               AS forest_district,
             hva.quota_type_code               AS quota_type_code,
             hva.salvage_type_code             AS salvage_type_code,
             hva.deciduous_ind                 AS deciduous_ind,
             hva.catastrophic_ind              AS catastrophic_ind,
             hva.cruise_based_ind              AS cruise_based_ind,
             hva.crown_lands_region_code       AS crown_lands_region_code,
             haa.marking_method_code           AS marking_method_code,
             haa.marking_instrument_code       AS marking_instrument_code,
             hva.district_admn_zone            AS district_admn_zone,
             hva.harvest_area                  AS harvest_area,
             hva.location                      AS location,
             hva.mgmt_unit_id                  AS mgmt_unit_id,
             hva.mgmt_unit_type_code           AS mgmt_unit_type_code
        FROM the.harvesting_authority hva
        JOIN the.harvesting_hauling_xref xref
               ON xref.hva_skey = hva.hva_skey
              AND xref.primary_mark_ind = 'Y'
        JOIN the.hauling_authority haa
               ON haa.forest_file_id = hva.forest_file_id
              AND haa.timber_mark = xref.timber_mark
        JOIN the.prov_forest_use pfu
               ON pfu.forest_file_id = hva.forest_file_id
        LEFT JOIN the.harvest_authority_geom geom
               ON geom.hva_skey = hva.hva_skey
        LEFT JOIN the.forest_file ff
               ON ff.forest_file_id = hva.forest_file_id
        LEFT JOIN the.org_unit ou
               ON ou.org_unit_no = ff.admin_district_no
        LEFT JOIN the.file_type_code ftc
               ON ftc.file_type_code = pfu.file_type_code
        LEFT JOIN the.harvest_auth_status_code hsc
               ON hsc.harvest_auth_status_code = hva.harvest_auth_status_code
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = hva.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli
               ON cli.client_number = ffc.client_number
       WHERE hva.cutting_permit_id = :cpId
         AND (:forestFileId IS NULL OR hva.forest_file_id = :forestFileId)
       FETCH FIRST 1 ROWS ONLY
      """;

  /**
   * Cutting permit detail — mirrors {@code FTA_902_CP_DETAIL.GET}.
   *
   * @param cpId         the cutting-permit id (path key)
   * @param forestFileId optional forest-file id to disambiguate, or null
   * @return the permit detail, or empty when no matching authority exists
   */
  public Optional<CuttingPermitDetailDto> findByCpId(String cpId, String forestFileId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("cpId", blankToNull(cpId))
        .addValue("forestFileId", blankToNull(forestFileId));

    List<CuttingPermitDetailDto> rows = jdbc.query(DETAIL_SQL, params, (rs, rowNum) ->
        new CuttingPermitDetailDto(
            rs.getString("forest_file_id"),
            rs.getString("cutting_permit_id"),
            rs.getString("timber_mark"),
            rs.getString("file_type_code"),
            rs.getString("file_type_description"),
            rs.getString("admin_org_code"),
            rs.getString("licensee"),
            rs.getString("status_code"),
            rs.getString("status_desc"),
            rs.getObject("status_date", java.time.LocalDate.class),
            rs.getObject("issue_date", java.time.LocalDate.class),
            rs.getObject("expiry_date", java.time.LocalDate.class),
            rs.getObject("extend_date", java.time.LocalDate.class),
            rs.getString("extend_reason_code"),
            getInteger(rs, "extend_count"),
            getInteger(rs, "tenure_term_years"),
            getInteger(rs, "tenure_term_months"),
            rs.getString("forest_district"),
            rs.getString("quota_type_code"),
            rs.getString("salvage_type_code"),
            rs.getString("deciduous_ind"),
            rs.getString("catastrophic_ind"),
            rs.getString("cruise_based_ind"),
            rs.getString("crown_lands_region_code"),
            rs.getString("marking_method_code"),
            rs.getString("marking_instrument_code"),
            rs.getString("district_admn_zone"),
            getDouble(rs, "harvest_area"),
            rs.getString("location"),
            rs.getString("mgmt_unit_id"),
            rs.getString("mgmt_unit_type_code")));

    return rows.stream().findFirst();
  }

  private static Integer getInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static Double getDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
