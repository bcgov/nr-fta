package ca.bc.gov.nrs.fta.recreation.service;

import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Access;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Attachment;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.District;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Fee;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Permissions;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Tombstone;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationProjectDetailDto;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationProjectDto;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA701 Recreation Project — the read path.
 *
 * <p>Ports the GET side of {@code THE.FTA_701_PROJECT_DETAILS} and
 * {@code THE.FTA_701_PROJECT_ACCESS}. Legacy assembles this screen from four
 * package round-trips; this returns it in one.
 *
 * <h2>Departures from legacy</h2>
 *
 * <ul>
 *   <li><b>The derived values are computed here</b> rather than by calling
 *       {@code FTA_REC_PROJECT_TOMBSTONE}. Its cursor is a plain sum over the
 *       project's current map features, reproduced below, so the round trip buys
 *       nothing — and inlining it keeps the whole read in one statement set.
 *   <li><b>No {@code SQL%ROWCOUNT} guards.</b> Legacy checks {@code SQL%ROWCOUNT
 *       <= 0} immediately after {@code OPEN … FOR} in three places and raises
 *       "record modified". Opening a ref cursor does not set that variable — it
 *       holds whatever the last DML left — so those guards are meaningless and
 *       can fire spuriously. They are not reproduced.
 *   <li><b>Everything is bound.</b>
 * </ul>
 */
@Service
public class RecreationProjectService {

  private final NamedParameterJdbcTemplate jdbc;

  public RecreationProjectService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The file header strip, shared in shape with the other file screens. */
  private static final String TOMBSTONE_SQL =
      """
      SELECT pfu.file_status_st                       AS file_status_code,
             tfsc.description                         AS file_status_desc,
             TO_CHAR(pfu.file_status_date,'YYYY-MM-DD') AS file_status_date,
             pfu.file_type_code                       AS file_type_code,
             ftc.description                          AS file_type_desc,
             ou.org_unit_code                         AS admin_org_code,
             ou.org_unit_name                         AS admin_org_name
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_file_status_code tfsc
               ON tfsc.tenure_file_status_code = pfu.file_status_st
        LEFT JOIN the.file_type_code ftc ON ftc.file_type_code = pfu.file_type_code
        LEFT JOIN the.org_unit ou        ON ou.org_unit_no = pfu.forest_region
       WHERE pfu.forest_file_id = :fileId
      """;

  /**
   * The project row, its AIA comment, and the four derived values.
   *
   * <p>{@code projectLength}/{@code projectArea}/{@code projectType} reproduce
   * {@code FTA_REC_PROJECT_TOMBSTONE}: a sum over the file's map features
   * restricted to the current, unretired ones. The AIA comment lives in
   * {@code RECREATION_COMMENT} under the {@code AIA} type rather than on the
   * project row.
   */
  private static final String PROJECT_SQL =
      """
      SELECT rp.forest_file_id                          AS forest_file_id,
             rp.project_name                            AS project_name,
             spatial.project_type_code                  AS project_type_code,
             spatial.project_type                       AS project_type,
             spatial.project_length                     AS project_length,
             spatial.project_area                       AS project_area,
             (SELECT COUNT(rdc.campsite_number)
                FROM the.recreation_defined_campsite rdc
               WHERE rdc.forest_file_id = rp.forest_file_id) AS defined_campsites,
             (SELECT DECODE(COUNT(*), 0, 'N', 'Y') FROM dual
               WHERE EXISTS (SELECT 1 FROM the.associated_use au
                              WHERE au.forest_file_id = rp.forest_file_id
                              UNION
                             SELECT 1 FROM the.associated_use au2
                              WHERE au2.associated_file_id = rp.forest_file_id)) AS assoc_files_exist,
             rp.recreation_risk_rating_code             AS risk_rating_code,
             TO_CHAR(rp.project_established_date,'YYYY-MM-DD') AS project_established_date,
             rp.site_location                           AS site_location,
             rp.utm_zone                                AS utm_zone,
             rp.utm_northing                            AS utm_northing,
             rp.utm_easting                             AS utm_easting,
             rp.right_of_way                            AS right_of_way,
             rp.recreation_feature_code                 AS feature_code,
             rp.recreation_user_days_code               AS user_days_code,
             rp.recreation_maintain_std_code            AS maintain_std_code,
             rp.camp_host_ind                           AS camp_host_ind,
             rp.overflow_campsites                      AS overflow_campsites,
             rp.low_mobility_access_ind                 AS low_mobility_access_ind,
             rp.recreation_view_ind                     AS recreation_view_ind,
             rp.resource_feature_ind                    AS resource_feature_ind,
             rp.recreation_control_access_code          AS control_access_code,
             TO_CHAR(rp.last_rec_inspection_date,'YYYY-MM-DD')  AS last_rec_inspection_date,
             TO_CHAR(rp.last_hzrd_tree_assess_date,'YYYY-MM-DD') AS last_hzrd_tree_assess_date,
             rp.arch_impact_assess_ind                  AS arch_impact_assess_ind,
             TO_CHAR(rp.arch_impact_date,'YYYY-MM-DD')  AS arch_impact_date,
             rp.borden_no                               AS borden_no,
             (SELECT rc.project_comment
                FROM the.recreation_comment rc
               WHERE rc.forest_file_id = rp.forest_file_id
                 AND rc.rec_comment_type_code = 'AIA'
                 AND ROWNUM = 1)                        AS aia_comment,
             rp.site_description                        AS site_description,
             rp.revision_count                          AS revision_count
        FROM the.recreation_project rp
        LEFT JOIN (SELECT rmf.forest_file_id,
                          MIN(rmf.recreation_map_feature_code) AS project_type_code,
                          MIN(rmfc.description)                AS project_type,
                          SUM(rmfg.feature_length)             AS project_length,
                          SUM(rmfg.feature_area)               AS project_area
                     FROM the.recreation_map_feature rmf
                     JOIN the.recreation_map_feature_code rmfc
                       ON rmfc.recreation_map_feature_code = rmf.recreation_map_feature_code
                     JOIN the.recreation_map_feature_geom rmfg ON rmfg.rmf_skey = rmf.rmf_skey
                    WHERE rmf.current_ind = 'Y'
                      AND rmf.retirement_date IS NULL
                    GROUP BY rmf.forest_file_id) spatial
               ON spatial.forest_file_id = rp.forest_file_id
       WHERE rp.forest_file_id = :fileId
      """;

  private static final String DISTRICTS_SQL =
      """
      SELECT rdx.recreation_district_code AS district_code,
             rdc.description              AS description
        FROM the.recreation_district_xref rdx
        JOIN the.recreation_district_code rdc
          ON rdc.recreation_district_code = rdx.recreation_district_code
       WHERE rdx.forest_file_id = :fileId
       ORDER BY rdx.recreation_district_code
      """;

  private static final String FEES_SQL =
      """
      SELECT rf.fee_id                                AS fee_id,
             rf.fee_amount                            AS fee_amount,
             TO_CHAR(rf.fee_start_date,'YYYY-MM-DD')  AS fee_start_date,
             TO_CHAR(rf.fee_end_date,'YYYY-MM-DD')    AS fee_end_date,
             rf.recreation_fee_code                   AS fee_code,
             rfc.description                          AS fee_description,
             rf.monday_ind, rf.tuesday_ind, rf.wednesday_ind, rf.thursday_ind,
             rf.friday_ind, rf.saturday_ind, rf.sunday_ind,
             rf.revision_count                        AS revision_count
        FROM the.recreation_fee rf
        LEFT JOIN the.recreation_fee_code rfc
               ON rfc.recreation_fee_code = rf.recreation_fee_code
       WHERE rf.forest_file_id = :fileId
       ORDER BY rf.recreation_fee_code, rf.fee_end_date DESC
      """;

  private static final String ACCESS_SQL =
      """
      SELECT ra.recreation_access_code     AS access_code,
             rac.description               AS access_description,
             ra.recreation_sub_access_code AS sub_access_code,
             rsac.description              AS sub_access_description,
             ra.revision_count             AS revision_count
        FROM the.recreation_access ra
        LEFT JOIN the.recreation_access_code rac
               ON rac.recreation_access_code = ra.recreation_access_code
        LEFT JOIN the.recreation_sub_access_code rsac
               ON rsac.recreation_sub_access_code = ra.recreation_sub_access_code
       WHERE ra.forest_file_id = :fileId
       ORDER BY ra.recreation_access_code, ra.recreation_sub_access_code
      """;

  private static final String ATTACHMENTS_SQL =
      """
      SELECT ra.recreation_attachment_id AS attachment_id,
             ra.attachment_file_name     AS file_name,
             ra.revision_count           AS revision_count
        FROM the.recreation_attachment ra
       WHERE ra.forest_file_id = :fileId
       ORDER BY ra.recreation_attachment_id
      """;

  /**
   * Whether the file is one this screen handles.
   *
   * <p>Legacy guards on file type {@code F%} and id {@code REC%} in both
   * {@code handleInit} and {@code handleGet}; older {@code 900…} recreation
   * files are routed to the tenure screen instead. The security procedure has a
   * {@code 900} branch, but nothing on this screen can reach it.
   */
  public boolean isRecreationFile(String fileId) {
    if (fileId == null || !fileId.toUpperCase().startsWith("REC")) {
      return false;
    }
    Integer found = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.prov_forest_use
         WHERE forest_file_id = :fileId AND file_type_code LIKE 'F%'
        """,
        new MapSqlParameterSource("fileId", fileId),
        Integer.class);
    return found != null && found > 0;
  }

  /**
   * Loads the whole screen.
   *
   * @param fileId       the recreation file id
   * @param permissions  what the caller may change, decided by the controller
   *                     from the user's district scopes
   */
  public RecreationProjectDetailDto findDetail(String fileId, Permissions permissions) {
    MapSqlParameterSource params = new MapSqlParameterSource("fileId", fileId);

    Tombstone tombstone = jdbc.queryForObject(TOMBSTONE_SQL, params, (rs, n) ->
        new Tombstone(
            rs.getString("file_status_code"),
            rs.getString("file_status_desc"),
            rs.getString("file_status_date"),
            rs.getString("file_type_code"),
            rs.getString("file_type_desc"),
            rs.getString("admin_org_code"),
            rs.getString("admin_org_name")));

    // Null when the file exists but has no project row yet — a real state the
    // screen has to render, prompting the user to save details first.
    RecreationProjectDto project;
    try {
      project = jdbc.queryForObject(PROJECT_SQL, params, (rs, n) ->
          new RecreationProjectDto(
              rs.getString("forest_file_id"),
              rs.getString("project_name"),
              rs.getString("project_type_code"),
              rs.getString("project_type"),
              rs.getBigDecimal("project_length"),
              rs.getBigDecimal("project_area"),
              rs.getObject("defined_campsites", Integer.class),
              rs.getString("assoc_files_exist"),
              rs.getString("risk_rating_code"),
              rs.getString("project_established_date"),
              rs.getString("site_location"),
              rs.getObject("utm_zone", Integer.class),
              rs.getObject("utm_northing", Long.class),
              rs.getObject("utm_easting", Long.class),
              rs.getBigDecimal("right_of_way"),
              rs.getString("feature_code"),
              rs.getString("user_days_code"),
              rs.getString("maintain_std_code"),
              rs.getString("camp_host_ind"),
              rs.getObject("overflow_campsites", Integer.class),
              rs.getString("low_mobility_access_ind"),
              rs.getString("recreation_view_ind"),
              rs.getString("resource_feature_ind"),
              rs.getString("control_access_code"),
              rs.getString("last_rec_inspection_date"),
              rs.getString("last_hzrd_tree_assess_date"),
              rs.getString("arch_impact_assess_ind"),
              rs.getString("arch_impact_date"),
              rs.getString("borden_no"),
              rs.getString("aia_comment"),
              rs.getString("site_description"),
              rs.getObject("revision_count", Long.class)));
    } catch (EmptyResultDataAccessException e) {
      project = null;
    }

    List<District> districts = jdbc.query(DISTRICTS_SQL, params, (rs, n) ->
        new District(rs.getString("district_code"), rs.getString("description")));

    List<Fee> fees = jdbc.query(FEES_SQL, params, (rs, n) ->
        new Fee(
            rs.getObject("fee_id", Long.class),
            rs.getBigDecimal("fee_amount"),
            rs.getString("fee_start_date"),
            rs.getString("fee_end_date"),
            rs.getString("fee_code"),
            rs.getString("fee_description"),
            rs.getString("monday_ind"),
            rs.getString("tuesday_ind"),
            rs.getString("wednesday_ind"),
            rs.getString("thursday_ind"),
            rs.getString("friday_ind"),
            rs.getString("saturday_ind"),
            rs.getString("sunday_ind"),
            rs.getObject("revision_count", Long.class)));

    List<Access> access = jdbc.query(ACCESS_SQL, params, (rs, n) ->
        new Access(
            rs.getString("access_code"),
            rs.getString("access_description"),
            rs.getString("sub_access_code"),
            rs.getString("sub_access_description"),
            rs.getObject("revision_count", Long.class)));

    List<Attachment> attachments = jdbc.query(ATTACHMENTS_SQL, params, (rs, n) ->
        new Attachment(
            rs.getObject("attachment_id", Long.class),
            rs.getString("file_name"),
            rs.getObject("revision_count", Long.class)));

    return new RecreationProjectDetailDto(
        fileId, tombstone, project, districts, fees, access, attachments, permissions);
  }

  /**
   * The two hard rules that force saving off regardless of who the user is.
   *
   * <p>From {@code FTA_RECREATION_SECURITY}: the file must be in {@code HI}
   * status, and the project must have a spatial description — legacy tests the
   * latter through the map-feature description, which is exactly what
   * {@code projectType} above resolves to.
   *
   * @return the file's administering org unit code, or null when the file is not
   *         saveable at all
   */
  public String saveableAdminOrgCode(String fileId) {
    try {
      return jdbc.queryForObject(
          """
          SELECT ou.org_unit_code
            FROM the.prov_forest_use pfu
            JOIN the.org_unit ou ON ou.org_unit_no = pfu.forest_region
           WHERE pfu.forest_file_id = :fileId
             AND pfu.file_status_st = 'HI'
             AND EXISTS (SELECT 1
                           FROM the.recreation_map_feature rmf
                          WHERE rmf.forest_file_id = pfu.forest_file_id
                            AND rmf.current_ind = 'Y'
                            AND rmf.retirement_date IS NULL)
          """,
          new MapSqlParameterSource("fileId", fileId),
          String.class);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /** Whether a {@code RECREATION_PROJECT} row exists, which child edits require. */
  public boolean projectExists(String fileId) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM the.recreation_project WHERE forest_file_id = :fileId",
        new MapSqlParameterSource("fileId", fileId),
        Integer.class);
    return count != null && count > 0;
  }
}
