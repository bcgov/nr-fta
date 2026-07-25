package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Timber-mark search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_002_MARK_SRCH} (the timber
 * mark search) to a native query against the shared {@code THE} schema. The
 * SELECT/FROM/WHERE mirror the package body's {@code GET_LIST} /
 * {@code BUILD_WHERE_CLAUSE} — each filter is applied only when its bind value
 * is supplied (NVL-style), matching the legacy behaviour. Column selection
 * matches the package's {@code rec_tenure_results} record.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class TimbermarkSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public TimbermarkSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT org.org_unit_code                                                        AS org_unit_code,
             the.fta_utils.get_cp_licensee_number(tm.forest_file_id, tm.cutting_permit_id) AS client_number,
             NULL                                                                      AS client_locn_code,
             the.fta_utils.get_cp_licensee_name(tm.forest_file_id, tm.cutting_permit_id)   AS client_name,
             pfu.file_type_code                                                        AS file_type_code,
             pfu.forest_file_id                                                        AS forest_file_id,
             tm.cutting_permit_id                                                      AS cutting_permit_id,
             tm.timber_mark                                                            AS timber_mark,
             tm.certificate                                                            AS certificate,
             tm.mark_status_st                                                         AS mark_status_st,
             tm.mark_issue_date                                                        AS mark_issue_date,
             NVL(tm.mark_extend_date, tm.mark_expiry_date)                             AS mark_expiry_date,
             tm.salvage_type_code                                                      AS salvage_ind,
             tm.hva_skey                                                               AS hva_skey
        FROM the.prov_forest_use pfu,
             the.v_fta_timber_mark_vj tm,
             the.org_unit org
       WHERE pfu.forest_file_id(+) = tm.forest_file_id
         AND org.org_unit_no = tm.forest_district
         AND (:forestFileId   IS NULL OR pfu.forest_file_id LIKE :forestFileId || '%')
         AND (:timberMark     IS NULL OR tm.timber_mark LIKE :timberMark || '%')
         AND (:cuttingPermitId IS NULL OR tm.cutting_permit_id = :cuttingPermitId)
         AND (:fileTypeCode   IS NULL OR pfu.file_type_code = :fileTypeCode)
         AND (:markStatusSt   IS NULL OR tm.mark_status_st = :markStatusSt)
         AND (:certificate    IS NULL OR tm.certificate = :certificate)
         AND (:salvageInd     IS NULL OR tm.salvage_type_code = :salvageInd)
         AND (:clientNumber   IS NULL OR tm.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'B')
                   AND ffc.client_number = :clientNumber))
         AND (:clientName     IS NULL OR tm.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'B')
                   AND ffc.client_number IN (
                         SELECT vcp.client_number
                           FROM the.v_client_public vcp
                          WHERE UPPER(vcp.client_name) LIKE UPPER(:clientName) || '%')))
       ORDER BY org.org_unit_code, pfu.forest_file_id, tm.cutting_permit_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Timber-mark search — mirrors {@code FTA_002_MARK_SRCH.mainline}.
   *
   * @param forestFileId    partial forest-file id (prefix match), or null
   * @param timberMark      partial timber mark (prefix match), or null
   * @param cuttingPermitId exact cutting-permit id, or null
   * @param fileTypeCode    exact file-type code, or null
   * @param markStatusSt    exact mark-status code, or null
   * @param certificate     exact private-mark certificate, or null
   * @param salvageInd      exact salvage-type code, or null
   * @param clientNumber    exact client number, or null
   * @param clientName      client name (prefix match), or null
   */
  public List<TimbermarkSearchDto> search(
      String forestFileId,
      String timberMark,
      String cuttingPermitId,
      String fileTypeCode,
      String markStatusSt,
      String certificate,
      String salvageInd,
      String clientNumber,
      String clientName) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("cuttingPermitId", blankToNull(cuttingPermitId))
        .addValue("fileTypeCode", blankToNull(fileTypeCode))
        .addValue("markStatusSt", blankToNull(markStatusSt))
        .addValue("certificate", blankToNull(certificate))
        .addValue("salvageInd", blankToNull(salvageInd))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientName", blankToNull(clientName));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new TimbermarkSearchDto(
        rs.getString("org_unit_code"),
        rs.getString("client_number"),
        rs.getString("client_locn_code"),
        rs.getString("client_name"),
        rs.getString("file_type_code"),
        rs.getString("forest_file_id"),
        rs.getString("cutting_permit_id"),
        rs.getString("timber_mark"),
        rs.getString("certificate"),
        rs.getString("mark_status_st"),
        rs.getObject("mark_issue_date", java.time.LocalDate.class),
        rs.getObject("mark_expiry_date", java.time.LocalDate.class),
        rs.getString("salvage_ind"),
        rs.getObject("hva_skey", Long.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
