package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cut-block search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_003_CUTBLK_SRCH} (the cut
 * block search) to a native query against the shared {@code THE} schema. The
 * legacy body assembles the SQL dynamically from the {@code get} procedure's
 * parameters; here the same joins/columns and per-filter predicates are
 * expressed as a static native query, with each filter applied only when its
 * bind value is supplied (NVL-style), mirroring the package's behaviour. Column
 * selection matches the package's {@code rec_cut_block_results} record.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class CutblockSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public CutblockSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT DISTINCT cb.cb_skey                                                        AS cb_skey,
             ou.org_unit_code                                                           AS org_unit_code,
             TO_CHAR(the.sil_get_cp_licensee_number(cb.forest_file_id, cb.cutting_permit_id)) AS client_number,
             the.sil_get_cp_licensee(cb.forest_file_id, cb.cutting_permit_id)           AS client_name,
             pfu.forest_file_id                                                         AS forest_file_id,
             cb.cutting_permit_id                                                       AS cutting_permit_id,
             cb.timber_mark                                                             AS timber_mark,
             cb.cut_block_id                                                            AS cut_block_id,
             cb.block_status_st                                                         AS block_status_st,
             cboa.disturbance_start_date                                                AS disturbance_start_date,
             cboa.disturbance_end_date                                                  AS disturbance_end_date
        FROM the.prov_forest_use pfu,
             the.cut_block cb,
             the.cut_block_open_admin cboa,
             the.harvesting_authority hva,
             the.org_unit ou
       WHERE pfu.forest_file_id = hva.forest_file_id
         AND hva.hva_skey = cb.hva_skey
         AND ou.org_unit_no = hva.forest_district
         AND cb.cb_skey = cboa.cb_skey
         AND (:forestFileId IS NULL OR cb.forest_file_id LIKE :forestFileId || '%')
         AND (:cutBlockId IS NULL OR cb.cut_block_id = :cutBlockId)
         AND (:cuttingPermitId IS NULL OR cb.cutting_permit_id = :cuttingPermitId)
         AND (:timberMark IS NULL OR cb.timber_mark = :timberMark)
         AND (:blockStatusSt IS NULL OR cb.block_status_st = :blockStatusSt)
         AND (:districtAdminZone IS NULL OR hva.district_admn_zone = :districtAdminZone)
         AND (:orgUnitNo IS NULL OR ou.org_unit_no = :orgUnitNo)
         AND ((:managedByFile IS NULL OR :managedByCp IS NOT NULL)
              OR hva.forest_file_id = UPPER(:managedByFile))
         AND (:managedByCp IS NULL OR hva.cutting_permit_id = UPPER(:managedByCp))
         AND ((:harvestStartDateFrom IS NULL AND :harvestStartDateTo IS NULL)
              OR cboa.disturbance_start_date
                   BETWEEN TO_DATE(NVL(:harvestStartDateFrom, '0001-01-01'), 'YYYY-MM-DD')
                       AND TO_DATE(NVL(:harvestStartDateTo, '9999-12-31'), 'YYYY-MM-DD'))
         AND (:clientNumber IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_number = :clientNumber))
         AND (:clientLocnCode IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_locn_code = :clientLocnCode))
         AND (:clientName IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_number IN (
                        SELECT fc.client_number
                          FROM the.forest_client fc
                         WHERE fc.client_name LIKE UPPER(:clientName) || '%')))
       ORDER BY ou.org_unit_code, pfu.forest_file_id, cb.cutting_permit_id, cb.cut_block_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Cut-block search — mirrors {@code FTA_003_CUTBLK_SRCH.get}.
   *
   * @param forestFileId partial forest-file id (prefix match), or null
   * @param cuttingPermitId exact cutting-permit id, or null
   * @param timberMark exact timber mark, or null
   * @param cutBlockId exact cut-block id, or null
   * @param blockStatusSt exact block status code, or null
   * @param orgUnitNo administrative org-unit number, or null
   * @param clientNumber exact client number, or null
   * @param clientLocnCode exact client location code, or null
   * @param clientName client name (prefix match), or null
   * @param managedByFile managing forest-file id, or null
   * @param managedByCp managing cutting-permit id, or null
   * @param harvestStartDateFrom disturbance-start lower bound (YYYY-MM-DD), or null
   * @param harvestStartDateTo disturbance-start upper bound (YYYY-MM-DD), or null
   * @param districtAdminZone district admin zone, or null
   */
  public List<CutblockSearchDto> search(
      String forestFileId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String blockStatusSt,
      String orgUnitNo,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String managedByFile,
      String managedByCp,
      String harvestStartDateFrom,
      String harvestStartDateTo,
      String districtAdminZone) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("cuttingPermitId", blankToNull(cuttingPermitId))
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("cutBlockId", blankToNull(cutBlockId))
        .addValue("blockStatusSt", blankToNull(blockStatusSt))
        .addValue("orgUnitNo", blankToNull(orgUnitNo))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientLocnCode", blankToNull(clientLocnCode))
        .addValue("clientName", blankToNull(clientName))
        .addValue("managedByFile", blankToNull(managedByFile))
        .addValue("managedByCp", blankToNull(managedByCp))
        .addValue("harvestStartDateFrom", blankToNull(harvestStartDateFrom))
        .addValue("harvestStartDateTo", blankToNull(harvestStartDateTo))
        .addValue("districtAdminZone", blankToNull(districtAdminZone));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new CutblockSearchDto(
        rs.getObject("cb_skey", Long.class),
        rs.getString("org_unit_code"),
        rs.getString("client_number"),
        rs.getString("client_name"),
        rs.getString("forest_file_id"),
        rs.getString("cutting_permit_id"),
        rs.getString("timber_mark"),
        rs.getString("cut_block_id"),
        rs.getString("block_status_st"),
        rs.getObject("disturbance_start_date", java.time.LocalDate.class),
        rs.getObject("disturbance_end_date", java.time.LocalDate.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
