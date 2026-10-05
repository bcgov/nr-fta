package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads the Rec project tab — legacy FTA701 (Recreation Project Details).
 *
 * <p>Ports {@code FTA_701_PROJECT_DETAILS.GET}: the {@code RECREATION_PROJECT} row (null fields
 * when there is none — legacy then lets Save create it), the project tombstone
 * ({@code fta_rec_project_tombstone}: type, length and area from the current recreation map
 * feature), the defined-campsite count, whether associated files exist, the AIA comment
 * ({@code RECREATION_COMMENT} type AIA) and the fees; plus {@code GET_DISTRICT},
 * {@code GET_ATTACHMENT} and {@code FTA_701_PROJECT_ACCESS.GET}. Legacy's comma joins are ANSI
 * joins, and its code columns are joined to their tables for the descriptions the selects
 * show.
 *
 * <p>The gate is {@link RecProjectRules}; FTA701's applicability (a file type starting with F,
 * a RECnnnn file id) is checked here too.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RecProjectService {

  static final String NOT_RECREATION =
      "Recreation project details apply only to recreation files (file type F…).";
  static final String NOT_REC_FORMAT =
      "Recreation information may only be captured for files using the new Recreation file "
          + "format. (ie. RECnnnn)";

  /** What every write needs to know about the file — the gate's inputs. */
  record Context(
      String fileTypeCode,
      String fileStatus,
      boolean spatialApproved,
      boolean exists,
      Long revisionCount,
      String projectTypeCode,
      String featureCode,
      BigDecimal utmZone,
      BigDecimal utmEasting,
      BigDecimal utmNorthing,
      BigDecimal rightOfWay) {

    /** Null when FTA701 serves the file, else why not. */
    String notApplicableReason(String forestFileId) {
      if (fileTypeCode == null || !fileTypeCode.startsWith("F")) {
        return NOT_RECREATION;
      }
      if (!forestFileId.startsWith("REC")) {
        return NOT_REC_FORMAT;
      }
      return null;
    }

    RecProjectRules rules(String forestFileId) {
      String reason = notApplicableReason(forestFileId);
      return reason != null
          ? RecProjectRules.none(reason)
          : RecProjectRules.of(fileStatus, spatialApproved, exists);
    }

    boolean trailProject() {
      return projectTypeCode != null && RecProjectChecks.TRAIL_TYPES.contains(projectTypeCode);
    }
  }

  private static final String FILE_SQL =
      """
      SELECT pfu.file_type_code, pfu.file_status_st
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :id
      """;

  /**
   * FTA_RECREATION_SECURITY's spatial gate: {@code get_rec_proj_spatial_info} finds the
   * latest APP/RET tenure application with map features — none means no approved spatial.
   */
  private static final String SPATIAL_SQL =
      """
      SELECT COUNT(*)
        FROM the.tenure_application ta
        JOIN the.tenure_application_type_code tatc
          ON tatc.tenure_application_type_code = ta.tenure_application_type_code
        JOIN the.tenure_application_map_feature tamf ON tamf.tenure_app_id = ta.tenure_app_id
        JOIN the.fta_map_feature_code fmfc ON fmfc.fta_map_feature_code = tamf.feature_type_code
       WHERE ta.forest_file_id = :id
         AND ta.tenure_application_state_code IN ('APP', 'RET')
      """;

  /** fta_rec_project_tombstone: the current, unretired recreation map feature's type and sums. */
  private static final String TOMBSTONE_SQL =
      """
      SELECT type_code, type_desc, project_length, project_area
        FROM (SELECT rmf.recreation_map_feature_code AS type_code,
                     rmfc.description               AS type_desc,
                     SUM(rmfg.feature_length)       AS project_length,
                     SUM(rmfg.feature_area)         AS project_area
                FROM the.recreation_map_feature rmf
                JOIN the.recreation_map_feature_code rmfc
                  ON rmfc.recreation_map_feature_code = rmf.recreation_map_feature_code
                JOIN the.recreation_map_feature_geom rmfg ON rmfg.rmf_skey = rmf.rmf_skey
               WHERE rmf.forest_file_id = :id
                 AND rmf.current_ind = 'Y'
                 AND rmf.retirement_date IS NULL
               GROUP BY rmf.recreation_map_feature_code, rmfc.description)
       WHERE ROWNUM = 1
      """;

  private static final String PROJECT_SQL =
      """
      SELECT rp.project_name, rp.project_established_date, rp.recreation_view_ind,
             rp.overflow_campsites, rp.utm_zone, rp.utm_northing, rp.utm_easting,
             rp.right_of_way,
             rp.recreation_control_access_code, cac.description AS control_access_desc,
             rp.recreation_feature_code,        rfc.description AS feature_desc,
             rp.recreation_maintain_std_code,   msc.description AS maintain_std_desc,
             rp.recreation_user_days_code,      udc.description AS user_days_desc,
             rp.recreation_risk_rating_code,    rrc.description AS risk_rating_desc,
             rp.last_rec_inspection_date, rp.arch_impact_assess_ind, rp.arch_impact_date,
             rp.borden_no, rp.camp_host_ind, rp.low_mobility_access_ind,
             rp.resource_feature_ind, rp.last_hzrd_tree_assess_date, rp.site_description,
             rp.site_location, rp.revision_count
        FROM the.recreation_project rp
        LEFT JOIN the.recreation_control_access_code cac
               ON cac.recreation_control_access_code = rp.recreation_control_access_code
        LEFT JOIN the.recreation_feature_code rfc
               ON rfc.recreation_feature_code = rp.recreation_feature_code
        LEFT JOIN the.recreation_maintain_std_code msc
               ON msc.recreation_maintain_std_code = rp.recreation_maintain_std_code
        LEFT JOIN the.recreation_user_days_code udc
               ON udc.recreation_user_days_code = rp.recreation_user_days_code
        LEFT JOIN the.recreation_risk_rating_code rrc
               ON rrc.recreation_risk_rating_code = rp.recreation_risk_rating_code
       WHERE rp.forest_file_id = :id
      """;

  private static final String CAMPSITES_SQL =
      "SELECT COUNT(campsite_number) FROM the.recreation_defined_campsite WHERE forest_file_id = :id";

  private static final String ASSOC_SQL =
      """
      SELECT COUNT(*) FROM the.associated_use
       WHERE forest_file_id = :id OR associated_file_id = :id
      """;

  private static final String AIA_SQL =
      """
      SELECT project_comment
        FROM (SELECT project_comment FROM the.recreation_comment
               WHERE forest_file_id = :id AND rec_comment_type_code = 'AIA')
       WHERE ROWNUM = 1
      """;

  private static final String FEES_SQL =
      """
      SELECT rf.fee_id, rf.recreation_fee_code, rfc.description AS fee_desc, rf.fee_amount,
             rf.fee_start_date, rf.fee_end_date,
             rf.monday_ind, rf.tuesday_ind, rf.wednesday_ind, rf.thursday_ind,
             rf.friday_ind, rf.saturday_ind, rf.sunday_ind, rf.revision_count
        FROM the.recreation_fee rf
        JOIN the.recreation_fee_code rfc ON rfc.recreation_fee_code = rf.recreation_fee_code
       WHERE rf.forest_file_id = :id
       ORDER BY rf.recreation_fee_code ASC, rf.fee_end_date DESC
      """;

  private static final String DISTRICTS_SQL =
      """
      SELECT x.recreation_district_code, c.description
        FROM the.recreation_district_xref x
        JOIN the.recreation_district_code c
          ON c.recreation_district_code = x.recreation_district_code
       WHERE x.forest_file_id = :id
       ORDER BY x.recreation_district_code
      """;

  private static final String ACCESS_SQL =
      """
      SELECT ra.recreation_access_code, rac.description AS access_desc,
             ra.recreation_sub_access_code, rsac.description AS sub_access_desc,
             ra.revision_count
        FROM the.recreation_access ra
        JOIN the.recreation_access_code rac
          ON rac.recreation_access_code = ra.recreation_access_code
        JOIN the.recreation_sub_access_code rsac
          ON rsac.recreation_sub_access_code = ra.recreation_sub_access_code
       WHERE ra.forest_file_id = :id
       ORDER BY ra.recreation_access_code, ra.recreation_sub_access_code
      """;

  private static final String ATTACHMENTS_SQL =
      """
      SELECT ra.recreation_attachment_id, ra.attachment_file_name,
             DBMS_LOB.GETLENGTH(rac.attachment_content) AS size_bytes
        FROM the.recreation_attachment ra
        JOIN the.recreation_attachment_content rac
          ON rac.forest_file_id = ra.forest_file_id
         AND rac.recreation_attachment_id = ra.recreation_attachment_id
       WHERE ra.forest_file_id = :id
       ORDER BY ra.recreation_attachment_id
      """;

  private static final String CURRENT = "SYSDATE BETWEEN effective_date AND expiry_date";

  private final NamedParameterJdbcTemplate jdbc;

  public RecProjectService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static MapSqlParameterSource id(String forestFileId) {
    return new MapSqlParameterSource("id", forestFileId);
  }

  /** The gate's inputs; empty when the file does not exist. */
  Optional<Context> context(String forestFileId) {
    List<String[]> file = jdbc.query(FILE_SQL, id(forestFileId),
        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)});
    if (file.isEmpty()) {
      return Optional.empty();
    }
    Long spatial = jdbc.queryForObject(SPATIAL_SQL, id(forestFileId), Long.class);
    List<String> type = jdbc.query(TOMBSTONE_SQL, id(forestFileId), (rs, n) -> rs.getString("type_code"));
    List<Object[]> project = jdbc.query(
        """
        SELECT revision_count, recreation_feature_code, utm_zone, utm_easting, utm_northing,
               right_of_way
          FROM the.recreation_project WHERE forest_file_id = :id
        """,
        id(forestFileId),
        (rs, n) -> new Object[] {
            rs.getLong("revision_count"), rs.getString("recreation_feature_code"),
            rs.getBigDecimal("utm_zone"), rs.getBigDecimal("utm_easting"),
            rs.getBigDecimal("utm_northing"), rs.getBigDecimal("right_of_way")});
    Object[] p = project.isEmpty() ? null : project.get(0);
    return Optional.of(new Context(
        file.get(0)[0],
        file.get(0)[1],
        spatial != null && spatial > 0,
        p != null,
        p == null ? null : (Long) p[0],
        type.isEmpty() ? null : type.get(0),
        p == null ? null : (String) p[1],
        p == null ? null : (BigDecimal) p[2],
        p == null ? null : (BigDecimal) p[3],
        p == null ? null : (BigDecimal) p[4],
        p == null ? null : (BigDecimal) p[5]));
  }

  /** The tab; empty when the file does not exist. */
  public Optional<RecProjectDto> find(String forestFileId) {
    Optional<Context> ctx = context(forestFileId);
    if (ctx.isEmpty()) {
      return Optional.empty();
    }
    Context c = ctx.get();
    String reason = c.notApplicableReason(forestFileId);
    if (reason != null) {
      return Optional.of(RecProjectDto.notApplicable(reason));
    }
    MapSqlParameterSource p = id(forestFileId);

    Object[] tomb = jdbc.query(TOMBSTONE_SQL, p, (rs, n) -> new Object[] {
        rs.getString("type_code"), rs.getString("type_desc"),
        rs.getBigDecimal("project_length"), rs.getBigDecimal("project_area")})
        .stream().findFirst().orElse(new Object[4]);
    Long campsites = jdbc.queryForObject(CAMPSITES_SQL, p, Long.class);
    Long assoc = jdbc.queryForObject(ASSOC_SQL, p, Long.class);
    String aia = jdbc.query(AIA_SQL, p, (rs, n) -> rs.getString(1)).stream()
        .findFirst().orElse(null);

    List<RecProjectDto.Fee> fees = jdbc.query(FEES_SQL, p, (rs, n) -> new RecProjectDto.Fee(
        rs.getLong("fee_id"),
        rs.getString("recreation_fee_code"),
        rs.getString("fee_desc"),
        rs.getBigDecimal("fee_amount"),
        rs.getObject("fee_start_date", LocalDate.class),
        rs.getObject("fee_end_date", LocalDate.class),
        "Y".equals(rs.getString("monday_ind")),
        "Y".equals(rs.getString("tuesday_ind")),
        "Y".equals(rs.getString("wednesday_ind")),
        "Y".equals(rs.getString("thursday_ind")),
        "Y".equals(rs.getString("friday_ind")),
        "Y".equals(rs.getString("saturday_ind")),
        "Y".equals(rs.getString("sunday_ind")),
        rs.getLong("revision_count")));
    List<RecProjectDto.District> districts = jdbc.query(DISTRICTS_SQL, p,
        (rs, n) -> new RecProjectDto.District(rs.getString(1), rs.getString(2)));
    List<RecProjectDto.Access> accesses = jdbc.query(ACCESS_SQL, p,
        (rs, n) -> new RecProjectDto.Access(
            rs.getString("recreation_access_code"),
            rs.getString("access_desc"),
            rs.getString("recreation_sub_access_code"),
            rs.getString("sub_access_desc"),
            rs.getLong("revision_count")));
    List<RecProjectDto.Attachment> attachments = jdbc.query(ATTACHMENTS_SQL, p,
        (rs, n) -> new RecProjectDto.Attachment(
            rs.getLong("recreation_attachment_id"),
            rs.getString("attachment_file_name"),
            rs.getObject("size_bytes") == null ? null : rs.getLong("size_bytes")));

    RecProjectRules rules = c.rules(forestFileId);
    String typeCode = (String) tomb[0];
    String typeDesc = (String) tomb[1];
    BigDecimal length = (BigDecimal) tomb[2];
    BigDecimal area = (BigDecimal) tomb[3];
    long campsiteCount = campsites == null ? 0 : campsites;
    boolean hasAssoc = assoc != null && assoc > 0;
    boolean trail = c.trailProject();

    List<RecProjectDto> rows = jdbc.query(PROJECT_SQL, p, (rs, n) -> new RecProjectDto(
        true, null, true, rs.getLong("revision_count"),
        typeCode, typeDesc, length, area, trail,
        rs.getString("project_name"),
        rs.getObject("project_established_date", LocalDate.class),
        rs.getString("recreation_risk_rating_code"), rs.getString("risk_rating_desc"),
        rs.getString("site_location"),
        rs.getBigDecimal("utm_zone"), rs.getBigDecimal("utm_easting"),
        rs.getBigDecimal("utm_northing"), rs.getBigDecimal("right_of_way"),
        rs.getString("recreation_feature_code"), rs.getString("feature_desc"),
        rs.getString("recreation_user_days_code"), rs.getString("user_days_desc"),
        rs.getString("recreation_maintain_std_code"), rs.getString("maintain_std_desc"),
        campsiteCount,
        rs.getString("camp_host_ind"),
        rs.getBigDecimal("overflow_campsites"),
        rs.getString("low_mobility_access_ind"),
        rs.getString("recreation_view_ind"),
        rs.getString("resource_feature_ind"),
        hasAssoc,
        rs.getString("recreation_control_access_code"), rs.getString("control_access_desc"),
        rs.getObject("last_rec_inspection_date", LocalDate.class),
        rs.getObject("last_hzrd_tree_assess_date", LocalDate.class),
        rs.getString("arch_impact_assess_ind"),
        rs.getObject("arch_impact_date", LocalDate.class),
        rs.getString("borden_no"),
        aia,
        rs.getString("site_description"),
        rules, districts, fees, accesses, attachments));
    if (!rows.isEmpty()) {
      return Optional.of(rows.get(0));
    }
    // No RECREATION_PROJECT row: legacy shows the tombstone with empty fields (camp host and
    // low mobility default to N) and lets Save create the row.
    return Optional.of(new RecProjectDto(
        true, null, false, null, typeCode, typeDesc, length, area, trail,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        campsiteCount, "N", null, "N", null, null, hasAssoc, null, null, null, null, null, null,
        null, null, null, rules, districts, fees, accesses, attachments));
  }

  /** FTA701's dropdowns. */
  public RecProjectLookupsDto lookups() {
    return new RecProjectLookupsDto(
        codes("recreation_risk_rating_code"),
        codes("recreation_feature_code"),
        codes("recreation_user_days_code"),
        codes("recreation_control_access_code"),
        codes("recreation_maintain_std_code"),
        codes("recreation_fee_code"),
        codes("recreation_access_code"),
        codes("recreation_sub_access_code"),
        jdbc.query(
            """
            SELECT recreation_access_code, recreation_sub_access_code
              FROM the.recreation_access_xref
             ORDER BY recreation_access_code, recreation_sub_access_code
            """,
            (rs, n) -> new RecProjectLookupsDto.AccessPair(rs.getString(1), rs.getString(2))),
        codes("recreation_district_code"));
  }

  /** A code table's current codes as "CODE - Description". The table name is a constant. */
  private List<CodeOptionDto> codes(String table) {
    return jdbc.query(
        "SELECT " + table + " AS code, " + table + " || ' - ' || description AS description"
            + " FROM the." + table + " WHERE " + CURRENT + " ORDER BY " + table,
        (rs, n) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }
}
