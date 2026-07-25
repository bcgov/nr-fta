package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cut Block detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_904_CUTBLKDETAIL} (FTA904
 * Cut Block Detail) to a native query against the shared {@code THE} schema.
 * The query is derived from the package body {@code GET} procedure — the
 * primary branch that reads a non-private-mark, non-FSJ block (joining
 * {@code CUT_BLOCK} / {@code CUT_BLOCK_OPEN_ADMIN} / {@code HARVESTING_AUTHORITY}
 * plus the opening and map-feature/tenure-application tables). Each optional
 * filter is applied only when its bind value is supplied (NVL-style), matching
 * the legacy behaviour.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}); there is no
 * local database, so it is exercised only in a deployed environment.
 */
@Service
public class CutblockDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public CutblockDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String DETAIL_SQL =
      """
      SELECT cb.forest_file_id             AS forest_file_id,
             cb.cutting_permit_id          AS cutting_permit_id,
             cb.cut_block_id               AS cut_block_id,
             cb.timber_mark                AS timber_mark,
             ou.org_unit_code || ' - ' || ou.org_unit_name AS forest_district,
             hva.harvest_auth_status_code
               || DECODE(hva.harvest_auth_status_code, NULL, NULL, ' - ')
               || sts.description          AS mark_status,
             hva.issue_date                AS mark_issue_date,
             NVL(hva.extend_date, hva.expiry_date) AS mark_expiry_date,
             TO_CHAR(TRUNC(hva.tenure_term / 12), '999') || ' yr '
               || TO_CHAR(MOD(hva.tenure_term, 12)) || ' mo' AS mark_term,
             cb.block_status_st            AS block_status,
             cb.block_status_date          AS block_status_date,
             cb.cut_block_description       AS cut_block_description,
             cb.sp_exempt_ind              AS sp_exempt_ind,
             cboa.planned_gross_block_area AS planned_gross_block_area,
             cboa.planned_net_block_area   AS planned_net_block_area,
             cboa.disturbance_gross_area   AS disturbance_gross_area,
             cboa.disturbance_start_date   AS disturbance_start_date,
             cboa.disturbance_end_date     AS disturbance_end_date,
             cboa.planned_harvest_date     AS planned_harvest_date,
             LPAD(o.mapsheet_grid, 3) || o.mapsheet_letter || ' '
               || LPAD(o.mapsheet_square, 3, 0) || ' '
               || DECODE(o.mapsheet_quad, NULL, NULL, o.mapsheet_quad || '.' || o.mapsheet_sub_quad) || ' '
               || LPAD(o.opening_number, 4) AS opening,
             cboa.opening_id               AS opening_id,
             SUBSTR(NVL(tamf.reference_name, ta.description), 1, 50) AS reference_name,
             hva.salvage_type_code         AS salvage_type_code,
             cb.cut_regulation_code        AS cut_regulation_code,
             cb.reforest_declare_type_code AS reforest_declare_type_code,
             hva.harvest_type_code         AS harvest_type_code,
             ta.decision_date              AS decision_date,
             ta.issuance_date              AS issuance_date,
             cb.fire_harvesting_reason_code AS fire_harvesting_reason_code,
             cb.is_under_partition_order   AS under_partition_order,
             cb.reported_fire_date         AS reported_fire_date
        FROM the.cut_block cb
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
        LEFT JOIN the.harvesting_authority hva
               ON (cb.hva_skey IS NOT NULL AND hva.hva_skey = cb.hva_skey)
               OR (cb.hva_skey IS NULL AND cb.timber_mark IS NOT NULL
                   AND EXISTS (SELECT 'x' FROM the.harvesting_hauling_xref xref
                                WHERE xref.hva_skey = hva.hva_skey
                                  AND xref.timber_mark = cb.timber_mark))
        LEFT JOIN the.harvest_auth_status_code sts
               ON sts.harvest_auth_status_code = hva.harvest_auth_status_code
        LEFT JOIN the.opening o ON o.opening_id = cboa.opening_id
        LEFT JOIN the.cut_block_geom geom ON geom.cb_skey = cb.cb_skey
        LEFT JOIN the.tenure_application_map_feature tamf
               ON tamf.map_feature_id = geom.map_feature_id
        LEFT JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = hva.forest_district
       WHERE cb.cut_block_id = :blockId
         AND (:forestFileId IS NULL OR cb.forest_file_id = :forestFileId)
         AND (:cuttingPermitId IS NULL OR cb.cutting_permit_id = :cuttingPermitId)
       ORDER BY cb.cb_skey DESC
       FETCH FIRST 1 ROWS ONLY
      """;

  /**
   * Cut block detail — mirrors {@code FTA_904_CUTBLKDETAIL.GET}.
   *
   * @param blockId         the cut block id (path key)
   * @param forestFileId    forest-file id to disambiguate, or null
   * @param cuttingPermitId cutting-permit id to disambiguate, or null
   * @return the matching cut block detail, or empty if none
   */
  public Optional<CutblockDetailDto> find(String blockId, String forestFileId, String cuttingPermitId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("blockId", blankToNull(blockId))
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("cuttingPermitId", blankToNull(cuttingPermitId));

    List<CutblockDetailDto> rows = jdbc.query(DETAIL_SQL, params, (rs, rowNum) -> new CutblockDetailDto(
        rs.getString("forest_file_id"),
        rs.getString("cutting_permit_id"),
        rs.getString("cut_block_id"),
        rs.getString("timber_mark"),
        rs.getString("forest_district"),
        rs.getString("mark_status"),
        rs.getObject("mark_issue_date", java.time.LocalDate.class),
        rs.getObject("mark_expiry_date", java.time.LocalDate.class),
        rs.getString("mark_term"),
        rs.getString("block_status"),
        rs.getObject("block_status_date", java.time.LocalDate.class),
        rs.getString("cut_block_description"),
        rs.getString("sp_exempt_ind"),
        getDouble(rs, "planned_gross_block_area"),
        getDouble(rs, "planned_net_block_area"),
        getDouble(rs, "disturbance_gross_area"),
        rs.getObject("disturbance_start_date", java.time.LocalDate.class),
        rs.getObject("disturbance_end_date", java.time.LocalDate.class),
        rs.getObject("planned_harvest_date", java.time.LocalDate.class),
        rs.getString("opening"),
        rs.getObject("opening_id", Long.class),
        rs.getString("reference_name"),
        rs.getString("salvage_type_code"),
        rs.getString("cut_regulation_code"),
        rs.getString("reforest_declare_type_code"),
        rs.getString("harvest_type_code"),
        rs.getObject("decision_date", java.time.LocalDate.class),
        rs.getObject("issuance_date", java.time.LocalDate.class),
        rs.getString("fire_harvesting_reason_code"),
        rs.getString("under_partition_order"),
        rs.getObject("reported_fire_date", java.time.LocalDate.class)));

    return rows.stream().findFirst();
  }

  private static Double getDouble(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.doubleValue();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
