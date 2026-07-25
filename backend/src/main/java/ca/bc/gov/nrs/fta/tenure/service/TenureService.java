package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenure search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_001_TENR_SRCH} (the common
 * tenure search) to a native query against the shared {@code THE} schema. The
 * WHERE clause mirrors the package's {@code mainline} parameters — each filter
 * is applied only when its bind value is supplied (NVL-style), matching the
 * legacy behaviour. Column selection matches the package's
 * {@code rec_tenure_results} record.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class TenureService {

  private final NamedParameterJdbcTemplate jdbc;

  public TenureService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT ou.org_unit_code            AS org_unit_code,
             cli.client_number           AS client_number,
             ffc.forest_file_client_locn AS client_locn_code,
             cli.client_name             AS client_name,
             ff.forest_file_id           AS forest_file_id,
             ff.file_type_code           AS file_type_code,
             fctc.description            AS file_client_type_desc,
             ff.file_status_st           AS file_status_code,
             fsc.description             AS file_status_desc,
             ff.file_issue_date          AS issue_date,
             ff.file_expiry_date         AS expiry_date
        FROM the.forest_file ff
        JOIN the.org_unit ou              ON ou.org_unit_no = ff.admin_district_no
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = ff.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli          ON cli.client_number = ffc.client_number
        LEFT JOIN the.file_status_code fsc ON fsc.file_status_st = ff.file_status_st
        LEFT JOIN the.file_client_type_code fctc
               ON fctc.file_client_type_code = ffc.forest_file_client_type_code
       WHERE (:forestFileId IS NULL OR ff.forest_file_id LIKE :forestFileId || '%')
         AND (:fileTypeCode IS NULL OR ff.file_type_code = :fileTypeCode)
         AND (:orgUnitCode  IS NULL OR ou.org_unit_code = :orgUnitCode)
         AND (:clientName   IS NULL OR UPPER(cli.client_name) LIKE UPPER(:clientName) || '%')
         AND (:clientNumber IS NULL OR cli.client_number = :clientNumber)
         AND (:fileStatus   IS NULL OR ff.file_status_st = :fileStatus)
       ORDER BY ff.forest_file_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Common tenure search — mirrors {@code FTA_001_TENR_SRCH.mainline}.
   *
   * @param forestFileId partial forest-file id (prefix match), or null
   * @param fileTypeCode exact file-type code, or null
   * @param orgUnitCode  administrative org-unit code, or null
   * @param clientName   client name (prefix match), or null
   * @param clientNumber exact client number, or null
   * @param fileStatus   file status code, or null
   */
  public List<TenureSummaryDto> search(
      String forestFileId,
      String fileTypeCode,
      String orgUnitCode,
      String clientName,
      String clientNumber,
      String fileStatus) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("fileTypeCode", blankToNull(fileTypeCode))
        .addValue("orgUnitCode", blankToNull(orgUnitCode))
        .addValue("clientName", blankToNull(clientName))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("fileStatus", blankToNull(fileStatus));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new TenureSummaryDto(
        rs.getString("org_unit_code"),
        rs.getString("client_number"),
        rs.getString("client_locn_code"),
        rs.getString("client_name"),
        rs.getString("forest_file_id"),
        rs.getString("file_type_code"),
        rs.getString("file_client_type_desc"),
        rs.getString("file_status_code"),
        rs.getString("file_status_desc"),
        rs.getObject("issue_date", java.time.LocalDate.class),
        rs.getObject("expiry_date", java.time.LocalDate.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
