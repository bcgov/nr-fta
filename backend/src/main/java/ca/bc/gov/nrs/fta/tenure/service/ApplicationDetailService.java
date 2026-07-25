package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ApplicationDetailDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenure-application detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_952X_TAMF_DET} (Tenure
 * Application Map Feature detail) to a native query against the shared
 * {@code THE} schema. The {@code mainline} procedure drives a file-level header
 * (via {@code Fta_Get_File_Header}) plus the {@code tenure_application} record
 * read in the GET procedure; this service returns that header/record detail for
 * a single application, keyed on {@code p_tenure_app_id}.
 *
 * <p>NOTE: the SQL below is derived from the package's {@code mainline} header
 * parameters and the {@code tenure_application} columns referenced in the GET
 * procedure body, because the header population itself lives in the external
 * {@code Fta_Get_File_Header} procedure (not part of this package). Column
 * selection mirrors the header parameters; each filter is applied only when its
 * bind value is non-null (NVL-style), matching the legacy behaviour.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class ApplicationDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public ApplicationDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String DETAIL_SQL =
      """
      SELECT ta.tenure_app_id                 AS tenure_app_id,
             ta.forest_file_id                AS forest_file_id,
             ff.file_type_code                AS file_type_code,
             ftc.description                  AS file_type_desc,
             ou.org_unit_code                 AS admin_org,
             cli.client_name                  AS licencee,
             cli.client_number                AS client_number,
             ta.tenure_application_state_code AS status,
             ta.entry_timestamp               AS status_date,
             ff.file_issue_date               AS award_date,
             ff.file_expiry_date              AS expiry_date,
             ta.tenure_application_type_code  AS tenure_app_type,
             ta.description                   AS description,
             tapc.description                 AS purpose_desc,
             hva.harvest_type_code            AS harvest_type_code
        FROM the.tenure_application ta
        JOIN the.forest_file ff            ON ff.forest_file_id = ta.forest_file_id
        LEFT JOIN the.file_type_code ftc   ON ftc.file_type_code = ff.file_type_code
        LEFT JOIN the.org_unit ou          ON ou.org_unit_no = ff.admin_district_no
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = ff.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli           ON cli.client_number = ffc.client_number
        LEFT JOIN the.tenure_application_purp_code tapc
               ON tapc.tenure_application_purp_code = ta.tenure_app_purp_code
        LEFT JOIN the.harvesting_authority hva
               ON hva.forest_file_id = ff.forest_file_id
       WHERE ta.tenure_app_id = :tenureAppId
         AND (:forestFileId IS NULL OR ta.forest_file_id = :forestFileId)
         AND ta.tenure_application_state_code != 'FAI'
       FETCH FIRST 1 ROWS ONLY
      """;

  /**
   * Tenure-application detail — mirrors {@code FTA_952X_TAMF_DET.mainline} for a
   * single application id.
   *
   * @param esfId        tenure application id (maps to {@code p_tenure_app_id})
   * @param forestFileId optional forest-file id header filter, or null
   * @return the detail record, or {@code null} when no application matches
   */
  public ApplicationDetailDto findByEsfId(String esfId, String forestFileId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("tenureAppId", blankToNull(esfId))
        .addValue("forestFileId", blankToNull(forestFileId));

    List<ApplicationDetailDto> results = jdbc.query(DETAIL_SQL, params, (rs, rowNum) -> new ApplicationDetailDto(
        rs.getString("tenure_app_id"),
        rs.getString("forest_file_id"),
        rs.getString("file_type_code"),
        rs.getString("file_type_desc"),
        rs.getString("admin_org"),
        rs.getString("licencee"),
        rs.getString("client_number"),
        rs.getString("status"),
        rs.getObject("status_date", java.time.LocalDate.class),
        rs.getObject("award_date", java.time.LocalDate.class),
        rs.getObject("expiry_date", java.time.LocalDate.class),
        rs.getString("tenure_app_type"),
        rs.getString("description"),
        rs.getString("purpose_desc"),
        rs.getString("harvest_type_code")));

    return results.isEmpty() ? null : results.get(0);
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
