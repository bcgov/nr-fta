package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Harvesting-authority search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_HVA_SEARCH} (the FTA
 * harvesting authority search) to a native query against the shared
 * {@code THE} schema. Column selection matches the package's
 * {@code rec_results} record; each filter is applied only when its bind value
 * is supplied (NVL-style), matching the common FTA search behaviour.
 *
 * <p>NOTE: {@code FTA_HVA_SEARCH} ships only a package spec (.PKS) declaring the
 * result record and a REF CURSOR — no package body (.PKB) is present in the
 * archive, so the SQL below is derived from the spec's record columns and the
 * base tables those columns are typed against
 * ({@code harvesting_authority}, {@code hauling_authority},
 * {@code oil_and_gas_authority}, {@code prov_forest_use}, {@code org_unit},
 * {@code forest_client}). The join keys reflect the standard FTA harvesting
 * model and may need confirmation against the live schema.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class HarvestingSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public HarvestingSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // NOTE: derived from the FTA_HVA_SEARCH.PKS spec only (no package body present).
  private static final String SEARCH_SQL =
      """
      SELECT ha.hva_skey               AS hva_skey,
             ou.org_unit_code          AS org_unit_code,
             cli.client_name           AS client_name,
             cli.client_number         AS client_number,
             pfu.file_type_code        AS file_type_code,
             pfu.forest_file_id        AS forest_file_id,
             ha.cutting_permit_id      AS cutting_permit_id,
             hla.timber_mark           AS timber_mark,
             oga.ogc_number            AS ogc_number,
             oga.nts_mapblock          AS nts_mapblock,
             oga.nts_mapunit           AS nts_mapunit,
             oga.nts_quarter           AS nts_mapquarter,
             oga.mapsheet_grid         AS nts_mapsheet_grid,
             oga.mapsheet_letter       AS nts_mapsheet_letter,
             oga.mapsheet_square       AS nts_mapsheet_square,
             oga.program_number        AS program_number,
             oga.geographic_identifier AS geographic_identifier
        FROM the.harvesting_authority ha
        JOIN the.prov_forest_use pfu      ON pfu.forest_file_id = ha.forest_file_id
        LEFT JOIN the.org_unit ou         ON ou.org_unit_no = pfu.admin_district_no
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = pfu.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.forest_client cli   ON cli.client_number = ffc.client_number
        LEFT JOIN the.hauling_authority hla       ON hla.hva_skey = ha.hva_skey
        LEFT JOIN the.oil_and_gas_authority oga   ON oga.hva_skey = ha.hva_skey
       WHERE (:cuttingPermitId IS NULL OR ha.cutting_permit_id LIKE :cuttingPermitId || '%')
         AND (:timberMark      IS NULL OR hla.timber_mark = :timberMark)
         AND (:forestFileId    IS NULL OR ha.forest_file_id LIKE :forestFileId || '%')
         AND (:clientName      IS NULL OR UPPER(cli.client_name) LIKE UPPER(:clientName) || '%')
         AND (:orgUnitCode     IS NULL OR ou.org_unit_code = :orgUnitCode)
       ORDER BY ha.forest_file_id, ha.cutting_permit_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Harvesting authority search — mirrors {@code THE.FTA_HVA_SEARCH}.
   *
   * @param cuttingPermitId partial cutting-permit id (prefix match), or null
   * @param timberMark      exact timber mark, or null
   * @param forestFileId    partial forest-file id (prefix match), or null
   * @param clientName      client name (prefix match), or null
   * @param orgUnitCode     administrative org-unit code, or null
   */
  public List<HarvestingSearchDto> search(
      String cuttingPermitId,
      String timberMark,
      String forestFileId,
      String clientName,
      String orgUnitCode) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("cuttingPermitId", blankToNull(cuttingPermitId))
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("clientName", blankToNull(clientName))
        .addValue("orgUnitCode", blankToNull(orgUnitCode));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new HarvestingSearchDto(
        rs.getObject("hva_skey", Long.class),
        rs.getString("org_unit_code"),
        rs.getString("client_name"),
        rs.getString("client_number"),
        rs.getString("file_type_code"),
        rs.getString("forest_file_id"),
        rs.getString("cutting_permit_id"),
        rs.getString("timber_mark"),
        rs.getString("ogc_number"),
        rs.getString("nts_mapblock"),
        rs.getString("nts_mapunit"),
        rs.getString("nts_mapquarter"),
        rs.getString("nts_mapsheet_grid"),
        rs.getString("nts_mapsheet_letter"),
        rs.getString("nts_mapsheet_square"),
        rs.getString("program_number"),
        rs.getString("geographic_identifier")));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
