package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import ca.bc.gov.nrs.fta.tenure.tab.MissingTable;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppCpRejectionDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppCpRequestDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppProfDecDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppRowDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppTabDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads for the tenure "Tenure application" tab — legacy FTA950 (Tenure Application List).
 *
 * <p>Ports the three GETs that screen ran:
 *
 * <ul>
 *   <li>{@code FTA_950X_TEN_APP.GET} — the "Spatial Submissions" (the ESF tenure applications
 *       submitted for the file), one SQL per file-type branch of the package, with its
 *       {@code (+)} joins as ANSI joins. Legacy's join to {@code PRFSNL_PARTY_DCLRTN} for the
 *       Prof Dec flag is an EXISTS here (a second declaration no longer doubles the row), and
 *       the image / regeneration columns needed only by the Exhibit A actions are left out.
 *   <li>{@code FTA_950X_TEN_APP_CUT_PERM_REQ.GET} — the "Smart Form Application List".
 *   <li>{@code FTA_950X_TEN_APP_CP_REJ.GET_REJECTS} — the "CP Submission Rejection List".
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class TenureAppService {

  private static final String FILE_SQL =
      """
      SELECT pfu.file_type_code,
             CASE WHEN EXISTS (SELECT 1 FROM the.minor_tsl_file_type_code c
                                WHERE c.minor_tsl_file_type_code = pfu.file_type_code)
                  THEN 'Y' ELSE 'N' END AS minor_tsl,
             CASE WHEN EXISTS (SELECT 1 FROM the.xml_permit_file_type_code c
                                WHERE c.xml_permit_file_type_code = pfu.file_type_code)
                  THEN 'Y' ELSE 'N' END AS permit_type,
             CASE WHEN EXISTS (SELECT 1 FROM the.single_mark_file_type_code c
                                WHERE c.single_mark_file_type_code = pfu.file_type_code)
                  THEN 'Y' ELSE 'N' END AS single_mark,
             CASE WHEN EXISTS (SELECT 1 FROM the.range_file_type_code c
                                WHERE c.range_file_type_code = pfu.file_type_code)
                  THEN 'Y' ELSE 'N' END AS range_type,
             CASE WHEN EXISTS (SELECT 1 FROM the.ftc_file_type_code c
                                WHERE c.ftc_file_type_code = pfu.file_type_code)
                  THEN 'Y' ELSE 'N' END AS ftc_type,
             CASE WHEN EXISTS (SELECT 1 FROM the.tenure_application t
                                WHERE t.forest_file_id = pfu.forest_file_id
                                  AND t.tenure_application_type_code = 'RP')
                  THEN 'Y' ELSE 'N' END AS special_road
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  /** Prof Dec flag, per application (legacy outer-joined PRFSNL_PARTY_DCLRTN). */
  private static final String PROF_DEC =
      "CASE WHEN EXISTS (SELECT 1 FROM the.prfsnl_party_dclrtn ppd"
          + " WHERE ppd.tenure_app_id = ta.tenure_app_id) THEN 'Y' ELSE 'N' END";

  private static final String SNC =
      "CASE WHEN ta.clearance_status_request_uri IS NOT NULL THEN 'Y' ELSE 'N' END";

  /** The application's own columns, the same in every branch. */
  private static final String APP_COLUMNS =
      """
             ta.submission_id                  AS submission_id,
             ta.submission_date                AS submission_date,
             org.org_unit_code                 AS org_unit_code,
             org.org_unit_name                 AS org_unit_name,
             ta.tenure_app_id                  AS tenure_app_id,
             ta.tenure_application_state_code  AS state_code,
             sts.description                   AS status_desc,
             ta.tenure_application_type_code   AS app_type_code,
             tatc.description                  AS app_type_desc,
             tapc.description                  AS purpose_desc,
             ta.description                    AS description,
             ta.image_created_ind              AS exh_a_image_ind,
             ta.image_create_in_progress_ind   AS regen_in_progress_ind,
             %s AS prof_dec_ind,
             %s AS snc_ind,
             ta.decision_date                  AS decision_date,
             ta.issuance_date                  AS issuance_date,
      """.formatted(PROF_DEC, SNC);

  /** The map feature's columns. */
  private static final String FEATURE_COLUMNS =
      """
             tamf.reference_name               AS location,
             tamf.point_of_commencement        AS p_of_c,
             tamf.object_area                  AS object_area,
             tamf.object_length                AS object_length,
             tamf.chart_area_id                AS chart_area_id,
             tamf.map_block_id                 AS chart_block_id,
             tamf.chart_volume                 AS chart_volume,
             tamf.map_feature_id               AS map_feature_id
      """;

  /** No map feature (application-level rows). */
  private static final String NO_FEATURE_COLUMNS =
      """
             NULL AS location, NULL AS p_of_c, NULL AS object_area, NULL AS object_length,
             NULL AS chart_area_id, NULL AS chart_block_id, NULL AS chart_volume,
             NULL AS map_feature_id
      """;

  private static final String STATE_JOINS =
      """
        JOIN the.org_unit org ON org.org_unit_no = ta.org_unit_no
        JOIN the.tenure_application_state_code sts
          ON sts.tenure_application_state_code = ta.tenure_application_state_code
      """;

  private static final String CODE_LEFT_JOINS =
      """
        LEFT JOIN the.tenure_application_purp_code tapc
               ON tapc.tenure_application_purp_code = ta.tenure_app_purp_code
        LEFT JOIN the.tenure_application_type_code tatc
               ON tatc.tenure_application_type_code = ta.tenure_application_type_code
      """;

  private static final String CODE_INNER_JOINS =
      """
        JOIN the.tenure_application_purp_code tapc
          ON tapc.tenure_application_purp_code = ta.tenure_app_purp_code
        JOIN the.tenure_application_type_code tatc
          ON tatc.tenure_application_type_code = ta.tenure_application_type_code
      """;

  private static final String ORDER = " ORDER BY submission_id DESC, tenure_app_id, map_feature_id";

  /** C01: the application's chart (CH) features. */
  private static final String C01_SQL =
      "SELECT " + APP_COLUMNS
          + " fac.description AS feature_type_desc, NULL AS cutting_permit_id,"
          + " NULL AS hva_skey, NULL AS timber_mark, " + FEATURE_COLUMNS
          + """
            FROM the.tenure_application_map_feature tamf
            JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
            """
          + STATE_JOINS
          + " LEFT JOIN the.fta_map_feature_code fac"
          + " ON fac.fta_map_feature_code = tamf.feature_type_code "
          + CODE_LEFT_JOINS
          + """
           WHERE tamf.forest_file_id = :forestFileId
             AND tamf.feature_type_code = 'CH'
             AND ta.tenure_application_state_code != 'FAI'
          """
          + ORDER;

  /**
   * Timber files (A…): the cutting-permit (CP) features with the permit's primary mark, the
   * Fort St. John harvesting-authority (TU) features, and the application-level TL / TLE / CHR /
   * ML applications. Legacy's TU branch matched every harvesting authority of the file, and
   * required an Exhibit A image record; both kept.
   */
  private static final String TIMBER_SQL =
      "SELECT " + APP_COLUMNS
          + " fac.description AS feature_type_desc, cp.cutting_permit_id AS cutting_permit_id,"
          + " cp.hva_skey AS hva_skey, cp.timber_mark AS timber_mark, " + FEATURE_COLUMNS
          + """
            FROM the.tenure_application_map_feature tamf
            JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
            LEFT JOIN (SELECT hva.forest_file_id, hva.hva_skey, hva.cutting_permit_id,
                              xref.timber_mark
                         FROM the.harvesting_authority hva
                         JOIN the.harvesting_hauling_xref xref
                           ON xref.hva_skey = hva.hva_skey AND xref.primary_mark_ind = 'Y') cp
                   ON cp.forest_file_id = tamf.forest_file_id
                  AND cp.cutting_permit_id = tamf.cutting_permit_id
            """
          + STATE_JOINS
          + " JOIN the.fta_map_feature_code fac"
          + " ON fac.fta_map_feature_code = tamf.feature_type_code "
          + CODE_INNER_JOINS
          + """
           WHERE tamf.forest_file_id = :forestFileId
             AND tamf.cutting_permit_id IS NOT NULL
             AND ta.tenure_application_state_code <> 'FAI'
             AND tamf.feature_type_code = 'CP'
          UNION
          SELECT
          """
          + APP_COLUMNS
          + " fac.description, UPPER(hva.harvesting_authority_id), hva.hva_skey,"
          + " hva.timber_mark, tamf.reference_name, tamf.point_of_commencement, NULL,"
          + " tamf.object_length, tamf.chart_area_id, tamf.map_block_id, tamf.chart_volume,"
          + " tamf.map_feature_id "
          + """
            FROM the.tenure_application ta
            JOIN the.tenure_application_map_feature tamf ON tamf.tenure_app_id = ta.tenure_app_id
            JOIN (SELECT hva.forest_file_id, hva.hva_skey, hva.harvesting_authority_id,
                         xref.timber_mark
                    FROM the.harvesting_authority hva
                    LEFT JOIN the.harvesting_hauling_xref xref
                           ON xref.hva_skey = hva.hva_skey AND xref.primary_mark_ind = 'Y') hva
              ON hva.forest_file_id = tamf.forest_file_id
            """
          + STATE_JOINS
          + " JOIN the.fta_map_feature_code fac"
          + " ON fac.fta_map_feature_code = tamf.feature_type_code "
          + CODE_INNER_JOINS
          + """
           WHERE tamf.forest_file_id = :forestFileId
             AND ta.tenure_application_state_code <> 'FAI'
             AND tamf.feature_type_code = 'TU'
             AND EXISTS (SELECT 1 FROM the.tenure_application_image tai
                          WHERE tai.tenure_app_id = ta.tenure_app_id)
          UNION
          SELECT
          """
          + APP_COLUMNS
          + " NULL, NULL, NULL, NULL, " + NO_FEATURE_COLUMNS
          + " FROM the.tenure_application ta "
          + STATE_JOINS
          + CODE_LEFT_JOINS
          + """
           WHERE ta.forest_file_id = :forestFileId
             AND ta.tenure_application_type_code IN ('TL', 'TLE', 'CHR', 'ML')
             AND ta.tenure_application_state_code != 'FAI'
          """
          + ORDER;

  /** F05: one row per application, its features' area and length summed. */
  private static final String F05_SQL =
      """
      SELECT MAX(ta.submission_id)                 AS submission_id,
             MAX(ta.submission_date)               AS submission_date,
             MAX(org.org_unit_code)                AS org_unit_code,
             MAX(org.org_unit_name)                AS org_unit_name,
             ta.tenure_app_id                      AS tenure_app_id,
             MAX(ta.tenure_application_state_code) AS state_code,
             MAX(sts.description)                  AS status_desc,
             MAX(ta.tenure_application_type_code)  AS app_type_code,
             MAX(tatc.description)                 AS app_type_desc,
             MAX(tapc.description)                 AS purpose_desc,
             MAX(ta.description)                   AS description,
             MAX(ta.image_created_ind)             AS exh_a_image_ind,
             MAX(ta.image_create_in_progress_ind)  AS regen_in_progress_ind,
             CASE WHEN EXISTS (SELECT 1 FROM the.prfsnl_party_dclrtn ppd
                                WHERE ppd.tenure_app_id = ta.tenure_app_id)
                  THEN 'Y' ELSE 'N' END            AS prof_dec_ind,
             MAX(CASE WHEN ta.clearance_status_request_uri IS NOT NULL
                      THEN 'Y' ELSE 'N' END)       AS snc_ind,
             MAX(ta.decision_date)                 AS decision_date,
             MAX(ta.issuance_date)                 AS issuance_date,
             NULL AS feature_type_desc, NULL AS cutting_permit_id, NULL AS hva_skey,
             NULL AS timber_mark,
             MAX(tamf.reference_name)              AS location,
             NULL                                  AS p_of_c,
             SUM(tamf.object_area)                 AS object_area,
             SUM(tamf.object_length)               AS object_length,
             NULL AS chart_area_id, NULL AS chart_block_id, NULL AS chart_volume,
             NULL AS map_feature_id
        FROM the.tenure_application ta
        JOIN the.tenure_application_map_feature tamf ON tamf.tenure_app_id = ta.tenure_app_id
      """
          + STATE_JOINS
          + CODE_LEFT_JOINS
          + """
           WHERE ta.forest_file_id = :forestFileId
             AND ta.tenure_application_state_code != 'FAI'
           GROUP BY ta.tenure_app_id
           ORDER BY submission_id DESC, tenure_app_id
          """;

  /** Legacy's application-level branch: E01–E03, H01–H03, S01. */
  private static final Set<String> APPLICATION_LEVEL =
      Set.of("E01", "E02", "E03", "H01", "H02", "H03", "S01");

  private static final String APPLICATION_LEVEL_SQL =
      "SELECT " + APP_COLUMNS
          + " NULL AS feature_type_desc, ta.cutting_permit_id AS cutting_permit_id,"
          + " NULL AS hva_skey, NULL AS timber_mark, " + NO_FEATURE_COLUMNS
          + " FROM the.tenure_application ta "
          + STATE_JOINS
          + CODE_LEFT_JOINS
          + """
           WHERE ta.forest_file_id = :forestFileId
             AND ta.tenure_application_state_code != 'FAI'
          """
          + ORDER;

  /**
   * Every other file type: the application's tenure features, with the file's harvesting
   * authority and primary mark (legacy matched the authority on the file alone).
   */
  private static final String OTHER_SQL =
      "SELECT " + APP_COLUMNS
          + " fac.description AS feature_type_desc, tamf.cutting_permit_id AS cutting_permit_id,"
          + " hva.hva_skey AS hva_skey, xref.timber_mark AS timber_mark, " + FEATURE_COLUMNS
          + """
            FROM the.tenure_application_map_feature tamf
            JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
            LEFT JOIN the.harvesting_authority hva ON hva.forest_file_id = tamf.forest_file_id
            LEFT JOIN the.harvesting_hauling_xref xref
                   ON xref.hva_skey = hva.hva_skey AND xref.primary_mark_ind = 'Y'
            """
          + STATE_JOINS
          + " LEFT JOIN the.fta_map_feature_code fac"
          + " ON fac.fta_map_feature_code = tamf.feature_type_code "
          + CODE_LEFT_JOINS
          + """
           WHERE tamf.forest_file_id = :forestFileId
             AND ta.tenure_application_state_code != 'FAI'
             AND tamf.feature_type_code IN ('TL', 'TS', 'TU', 'RT', 'CP', 'CS', 'RTR', 'MN',
                                            'CFA', 'CFB', 'WLA', 'WLB', 'RPP', 'FUP', 'RR',
                                            'SIT', 'IF')
          """
          + ORDER;

  /**
   * FTA_950X_TEN_APP_CUT_PERM_REQ.GET as FTA950 called it: by file only. Its other filters
   * ({@code col = NVL(:p, col)} with every {@code :p} null, and the same through
   * {@code FTA_UTILS.CONVERT_TO_DATE}) still drop a request whose column is null — notably one
   * not yet accepted — so they are kept as IS NOT NULL.
   */
  private static final String CP_REQUEST_SQL =
      """
      SELECT '' || cpr.cutting_permit_request_guid AS request_guid,
             cpr.hva_skey, cpr.cutting_permit_id, cpr.request_date,
             cpr.cutting_permit_request_code, cpr.cp_request_status_code,
             cpr.accepted_user_id, cpr.accepted_date, cpr.rationale_detail
        FROM the.cutting_permit_request cpr
       WHERE cpr.forest_file_id = :forestFileId
         AND cpr.hva_skey IS NOT NULL
         AND cpr.cutting_permit_id IS NOT NULL
         AND cpr.request_date IS NOT NULL
         AND cpr.cutting_permit_request_code IS NOT NULL
         AND cpr.cp_request_status_code IS NOT NULL
         AND cpr.create_user IS NOT NULL
         AND cpr.accepted_date IS NOT NULL
       ORDER BY cpr.request_date DESC, cpr.cutting_permit_id
      """;

  /*
   * The package's joins for a request's descriptions and rationale document, read as separate
   * lookups: not every FTA database has these tables (CP_REQUEST_STATUS_CODE is missing from
   * some), and a missing one should cost only its column, not the whole list.
   */
  private static final String CP_REQUEST_CODES_SQL =
      "SELECT cutting_permit_request_code AS code, description FROM the.cutting_permit_request_code";

  private static final String CP_REQUEST_STATUSES_SQL =
      "SELECT cp_request_status_code AS code, description FROM the.cp_request_status_code";

  private static final String CP_REQUEST_DOCUMENTS_SQL =
      """
      SELECT DISTINCT '' || tpd.support_cp_request_guid AS request_guid
        FROM the.tenure_permit_document tpd
        JOIN the.cp_external_document_sdw ceds
          ON ceds.cp_external_document_sdw_guid = tpd.cp_external_document_sdw_guid
        JOIN the.cutting_permit_request cpr
          ON cpr.cutting_permit_request_guid = tpd.support_cp_request_guid
       WHERE cpr.forest_file_id = :forestFileId
      """;

  /** FTA_950X_TEN_APP_CP_REJ.GET_REJECTS for the file (any CP). */
  private static final String CP_REJECTION_SQL =
      """
      SELECT ta.cutting_permit_id, ta.submission_id, ta.submission_date,
             NVL(ta.decision_date, ta.update_timestamp) AS rejection_date,
             ta.application_decision_message AS rejection_message
        FROM the.tenure_application ta
       WHERE ta.forest_file_id = :forestFileId
         AND ta.cutting_permit_id IS NOT NULL
         AND ta.tenure_application_state_code = 'REJ'
         AND ta.application_decision_message IS NOT NULL
       ORDER BY ta.submission_date DESC
      """;

  /** FTA_950X_TEN_APP_PROF_DEC.GET — the declarations on one application of the file. */
  private static final String PROF_DEC_SQL =
      """
      SELECT ta.tenure_app_id, ta.forest_file_id, ta.submission_id,
             ppd.declaration_type_code, dtc.description AS declaration_type_desc,
             ppd.declaration_date, ppd.client_number, ppd.client_location_code,
             %s AS client_name,
             pp.last_name || ', ' || pp.first_name || ' ' || pp.middle_name AS declarant_name,
             pp.certification_reference_id, pp.professional_identifier_code,
             pp.phone_number, pp.email_address, pp.web_site_address,
             ppd.declarant_comments,
             (SELECT LISTAGG(cb.cut_block_id, ', ') WITHIN GROUP (ORDER BY cb.cut_block_id)
                FROM the.cut_block cb
               WHERE cb.forest_file_id = :forestFileId
                 AND cb.cutting_permit_id = :cuttingPermitId
                 AND cb.hva_skey = :hvaSkey) AS cut_block_ids
        FROM the.prfsnl_party_dclrtn ppd
        JOIN the.professional_party pp ON pp.professional_party_guid = ppd.professional_party_guid
        JOIN the.tenure_application ta ON ta.tenure_app_id = ppd.tenure_app_id
        JOIN the.declaration_type_code dtc
          ON dtc.declaration_type_code = ppd.declaration_type_code
        LEFT JOIN the.forest_client fc ON fc.client_number = ppd.client_number
       WHERE ta.tenure_app_id = :tenureAppId
         AND ta.forest_file_id = :forestFileId
       ORDER BY ppd.declaration_date DESC
      """.formatted(ClientNameSql.displayName("fc"));

  private static final Logger LOGGER = LoggerFactory.getLogger(TenureAppService.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final TenureAppCpRequestLegacy cpRequestLegacy;

  public TenureAppService(
      NamedParameterJdbcTemplate jdbc, TenureAppCpRequestLegacy cpRequestLegacy) {
    this.jdbc = jdbc;
    this.cpRequestLegacy = cpRequestLegacy;
  }

  /**
   * The tab's lists.
   *
   * @throws ResponseStatusException 404 when the tenure does not exist
   */
  public TenureAppTabDto tab(String forestFileId) {
    Map<String, Object> file;
    try {
      file = jdbc.queryForMap(FILE_SQL, Map.of("forestFileId", forestFileId));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    String fileType = (String) file.get("file_type_code");
    TenureAppColumns columns = TenureAppColumns.of(
        fileType,
        yes(file.get("minor_tsl")),
        yes(file.get("permit_type")),
        yes(file.get("single_mark")),
        yes(file.get("range_type")),
        yes(file.get("special_road")));
    String warning = TenureAppColumns.warning(fileType, yes(file.get("ftc_type")));

    MapSqlParameterSource p = new MapSqlParameterSource("forestFileId", forestFileId);
    List<String> unavailable = new ArrayList<>();
    List<TenureAppRowDto> applications = MissingTable.orEmpty(
        () -> jdbc.query(applicationsSql(fileType), p, this::row),
        "Spatial submissions", unavailable);
    List<TenureAppCpRequestDto> requests = cpRequests(forestFileId, p, unavailable);
    List<TenureAppCpRejectionDto> rejections = MissingTable.orEmpty(
        () -> jdbc.query(CP_REJECTION_SQL, p, (rs, n) ->
        new TenureAppCpRejectionDto(
            rs.getString("cutting_permit_id"),
            rs.getObject("submission_id", Long.class),
            date(rs, "submission_date"),
            date(rs, "rejection_date"),
            rs.getString("rejection_message"))),
        "CP submission rejections", unavailable);
    return new TenureAppTabDto(
        fileType, columns, warning, requests, rejections, applications, unavailable);
  }

  /** The smart-form CP requests, with their descriptions and rationale flag where readable. */
  private List<TenureAppCpRequestDto> cpRequests(
      String forestFileId, MapSqlParameterSource p, List<String> unavailable) {
    List<String> missing = new ArrayList<>();
    List<Map<String, Object>> rows = MissingTable.orEmpty(
        () -> jdbc.queryForList(CP_REQUEST_SQL, p), "Smart form applications", missing);
    if (!missing.isEmpty()) {
      // Our account can't read CUTTING_PERMIT_REQUEST here; legacy's own package can, as it
      // runs with its owner's rights.
      try {
        return cpRequestLegacy.requests(forestFileId);
      } catch (DataAccessException | IllegalStateException e) {
        LOGGER.warn("FTA_950X_TEN_APP_CUT_PERM_REQ.GET failed too: {}",
            e instanceof DataAccessException d ? d.getMostSpecificCause().getMessage()
                : e.getMessage());
        unavailable.addAll(missing);
        return List.of();
      }
    }
    if (rows.isEmpty()) {
      return List.of();
    }
    // A missing code table only leaves its codes undescribed, as legacy's outer joins would.
    Map<String, String> requestDescs = descriptions(CP_REQUEST_CODES_SQL, "CP request types");
    Map<String, String> statusDescs = descriptions(CP_REQUEST_STATUSES_SQL, "CP request states");
    Set<String> withDocument = new HashSet<>(MissingTable.orEmpty(
        () -> jdbc.queryForList(CP_REQUEST_DOCUMENTS_SQL, p, String.class),
        "Rationale documents", unavailable));
    return rows.stream().map(r -> {
      String guid = (String) r.get("request_guid");
      String requestCode = (String) r.get("cutting_permit_request_code");
      String statusCode = (String) r.get("cp_request_status_code");
      return new TenureAppCpRequestDto(
          guid,
          r.get("hva_skey") instanceof Number n ? n.longValue() : null,
          (String) r.get("cutting_permit_id"),
          localDate(r.get("request_date")),
          requestCode,
          requestDescs.get(requestCode),
          statusCode,
          statusDescs.get(statusCode),
          (String) r.get("accepted_user_id"),
          localDate(r.get("accepted_date")),
          (String) r.get("rationale_detail"),
          guid != null && withDocument.contains(guid));
    }).toList();
  }

  /** A code table as code → description; empty (logged) when this database lacks it. */
  private Map<String, String> descriptions(String sql, String what) {
    Map<String, String> m = new HashMap<>();
    MissingTable.orEmpty(() -> jdbc.query(sql, Map.of(), (rs, n) -> {
      m.put(rs.getString("code"), rs.getString("description"));
      return n;
    }), what, new ArrayList<>());
    return m;
  }

  private static LocalDate localDate(Object v) {
    if (v instanceof java.sql.Timestamp t) {
      return t.toLocalDateTime().toLocalDate();
    }
    if (v instanceof java.sql.Date d) {
      return d.toLocalDate();
    }
    if (v instanceof java.time.LocalDateTime t) {
      return t.toLocalDate();
    }
    return v instanceof LocalDate d ? d : null;
  }

  /** The professional declarations on one of the file's applications. */
  public List<TenureAppProfDecDto> professionalDeclarations(
      String forestFileId, long tenureAppId, String cuttingPermitId, Long hvaSkey) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("tenureAppId", tenureAppId, Types.NUMERIC)
        .addValue("cuttingPermitId",
            cuttingPermitId == null || cuttingPermitId.isBlank() ? null : cuttingPermitId.trim(),
            Types.VARCHAR)
        .addValue("hvaSkey", hvaSkey, Types.NUMERIC);
    return jdbc.query(PROF_DEC_SQL, p, (rs, n) -> new TenureAppProfDecDto(
        rs.getObject("tenure_app_id", Long.class),
        rs.getString("forest_file_id"),
        rs.getObject("submission_id", Long.class),
        rs.getString("declaration_type_code"),
        rs.getString("declaration_type_desc"),
        date(rs, "declaration_date"),
        rs.getString("client_number"),
        rs.getString("client_location_code"),
        rs.getString("client_name"),
        trimToNull(rs.getString("declarant_name")),
        rs.getString("certification_reference_id"),
        rs.getString("professional_identifier_code"),
        rs.getString("phone_number"),
        rs.getString("email_address"),
        rs.getString("web_site_address"),
        rs.getString("declarant_comments"),
        rs.getString("cut_block_ids")));
  }

  /** The branch of FTA_950X_TEN_APP.GET for the file type. */
  static String applicationsSql(String fileType) {
    String ft = fileType == null ? "" : fileType.trim();
    if ("C01".equals(ft)) {
      return C01_SQL;
    }
    if (ft.startsWith("A")) {
      return TIMBER_SQL;
    }
    if ("F05".equals(ft)) {
      return F05_SQL;
    }
    if (APPLICATION_LEVEL.contains(ft)) {
      return APPLICATION_LEVEL_SQL;
    }
    return OTHER_SQL;
  }

  private TenureAppRowDto row(ResultSet rs, int n) throws SQLException {
    String state = rs.getString("state_code");
    return new TenureAppRowDto(
        rs.getObject("submission_id", Long.class),
        date(rs, "submission_date"),
        rs.getString("org_unit_code"),
        rs.getString("org_unit_name"),
        rs.getObject("tenure_app_id", Long.class),
        state,
        rs.getString("status_desc"),
        rs.getString("app_type_code"),
        rs.getString("app_type_desc"),
        rs.getString("purpose_desc"),
        rs.getString("description"),
        rs.getString("feature_type_desc"),
        rs.getString("cutting_permit_id"),
        rs.getObject("hva_skey", Long.class),
        rs.getString("timber_mark"),
        rs.getString("location"),
        rs.getString("p_of_c"),
        rs.getString("chart_area_id"),
        rs.getString("chart_block_id"),
        rs.getBigDecimal("chart_volume"),
        rs.getBigDecimal("object_length"),
        rs.getBigDecimal("object_area"),
        rs.getObject("map_feature_id", Long.class),
        yes(rs.getString("exh_a_image_ind")),
        yes(rs.getString("regen_in_progress_ind")),
        yes(rs.getString("prof_dec_ind")),
        yes(rs.getString("snc_ind")),
        date(rs, "decision_date"),
        date(rs, "issuance_date"),
        TenureAppRules.canIssue(state),
        TenureAppRules.issueBlockedReason(state));
  }

  private static LocalDate date(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toLocalDateTime().toLocalDate();
  }

  private static boolean yes(Object v) {
    return v != null && "Y".equals(v.toString().trim());
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() || ",".equals(t) ? null : t;
  }
}
