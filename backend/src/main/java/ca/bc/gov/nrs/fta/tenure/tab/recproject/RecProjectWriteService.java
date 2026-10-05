package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import static ca.bc.gov.nrs.fta.tenure.tab.recproject.RecProjectChecks.trim;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Types;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Rec project tab's writes — legacy FTA701's Save, SaveFee/DeleteFee, SaveAccess/DeleteAccess,
 * Add/Remove District and SaveAttachment/DeleteAttachment.
 *
 * <p>Each write is gated by {@link RecProjectRules} (the project's Save by its "parent" rule,
 * everything else by its "child" rule — except Remove District, which legacy gates by the
 * parent's), then checked as legacy's form ({@link RecProjectChecks}) and PL/SQL did
 * ({@code FTA_701_PROJECT_DETAILS}, {@code FTA_701_PROJECT_ACCESS}), with their message texts.
 * Unlike legacy, updates and deletes are guarded by the row's revision count (409 when someone
 * else changed it).
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RecProjectWriteService {

  /** web.xml max-upload-file-size, legacy's attachment limit. */
  static final int MAX_ATTACHMENT_BYTES = 4194304;
  /** RECREATION_ATTACHMENT.ATTACHMENT_FILE_NAME (the package's cursor type). */
  static final int MAX_ATTACHMENT_NAME = 50;

  static final String FEATURE_WARNING =
      "A Significant Recreation Feature was entered therefore Objectives should be entered.";
  static final String SITE_POINT_WARNING =
      "The project was saved, but its site point could not be placed from the UTM coordinates.";

  /** The outcome of a project save: legacy's confirmation-level messages. */
  public record SaveResult(List<String> warnings) {}

  /** A downloaded establishment order. */
  public record AttachmentFile(String fileName, byte[] content) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final RecProjectService reader;

  public RecProjectWriteService(NamedParameterJdbcTemplate jdbc, RecProjectService reader) {
    this.jdbc = jdbc;
    this.reader = reader;
  }

  // ─── Gate ────────────────────────────────────────────────────────────────

  private RecProjectService.Context context(String forestFileId) {
    return reader.context(forestFileId).orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Forest file not found."));
  }

  private RecProjectService.Context gateProject(String forestFileId) {
    RecProjectService.Context c = context(forestFileId);
    RecProjectRules rules = c.rules(forestFileId);
    if (!rules.project()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.projectReason());
    }
    return c;
  }

  private RecProjectService.Context gateChild(String forestFileId) {
    RecProjectService.Context c = context(forestFileId);
    RecProjectRules rules = c.rules(forestFileId);
    if (!rules.child()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.childReason());
    }
    return c;
  }

  private static void badRequest(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
  }

  private static ResponseStatusException changedElsewhere(String what) {
    return new ResponseStatusException(HttpStatus.CONFLICT,
        "This " + what + " was changed by someone else since you opened it. Reload and try again.");
  }

  /** The code is current in its table, or is the value already stored. Table is a constant. */
  private boolean codeOk(String table, String code, String stored) {
    if (code == null || code.equals(stored)) {
      return true;
    }
    Long n = jdbc.queryForObject(
        "SELECT COUNT(*) FROM the." + table + " WHERE " + table + " = :code"
            + " AND SYSDATE BETWEEN effective_date AND expiry_date",
        new MapSqlParameterSource("code", code), Long.class);
    return n != null && n > 0;
  }

  private void code(List<String> e, String table, String label, String code, String stored) {
    if (!codeOk(table, code, stored)) {
      e.add(label + " " + code + " is not a current code.");
    }
  }

  // ─── Project (FTA701 Save) ───────────────────────────────────────────────

  private static final String STORED_CODES_SQL =
      """
      SELECT recreation_risk_rating_code, recreation_feature_code, recreation_user_days_code,
             recreation_maintain_std_code, recreation_control_access_code
        FROM the.recreation_project WHERE forest_file_id = :forestFileId
      """;

  private static final String INSERT_PROJECT_SQL =
      """
      INSERT INTO the.recreation_project (
        forest_file_id, project_name, project_established_date, recreation_view_ind,
        overflow_campsites, utm_zone, utm_northing, utm_easting, right_of_way,
        recreation_control_access_code, recreation_feature_code, recreation_maintain_std_code,
        recreation_user_days_code, recreation_risk_rating_code, last_rec_inspection_date,
        arch_impact_assess_ind, arch_impact_date, borden_no, camp_host_ind,
        low_mobility_access_ind, resource_feature_ind, last_hzrd_tree_assess_date,
        site_description, site_location, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :forestFileId, :projectName, :established, :viewInd,
        :overflow, :utmZone, :utmNorthing, :utmEasting, :rightOfWay,
        :controlAccess, :feature, :maintainStd,
        :userDays, :riskRating, :lastRecInspection,
        :archInd, :archDate, :bordenNo, :campHost,
        :lowMobility, :resourceFeature, :lastHazardTree,
        :siteDescription, :siteLocation, 0,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  private static final String UPDATE_PROJECT_SQL =
      """
      UPDATE the.recreation_project
         SET project_name                   = :projectName,
             project_established_date       = :established,
             recreation_view_ind            = :viewInd,
             overflow_campsites             = :overflow,
             utm_zone                       = :utmZone,
             utm_northing                   = :utmNorthing,
             utm_easting                    = :utmEasting,
             right_of_way                   = :rightOfWay,
             recreation_control_access_code = :controlAccess,
             recreation_feature_code        = :feature,
             recreation_maintain_std_code   = :maintainStd,
             recreation_user_days_code      = :userDays,
             recreation_risk_rating_code    = :riskRating,
             last_rec_inspection_date       = :lastRecInspection,
             arch_impact_assess_ind         = :archInd,
             arch_impact_date               = :archDate,
             borden_no                      = :bordenNo,
             camp_host_ind                  = :campHost,
             low_mobility_access_ind        = :lowMobility,
             resource_feature_ind           = :resourceFeature,
             last_hzrd_tree_assess_date     = :lastHazardTree,
             site_description               = :siteDescription,
             site_location                  = :siteLocation,
             revision_count                 = revision_count + 1,
             update_userid                  = :userId,
             update_timestamp               = SYSDATE
       WHERE forest_file_id = :forestFileId
         AND revision_count = :revisionCount
      """;

  private static final String AIA_COUNT_SQL =
      """
      SELECT COUNT(*) FROM the.recreation_comment
       WHERE forest_file_id = :forestFileId AND rec_comment_type_code = 'AIA'
      """;

  private static final String AIA_INSERT_SQL =
      """
      INSERT INTO the.recreation_comment (
        forest_file_id, recreation_comment_id, rec_comment_type_code, closure_ind,
        project_comment, comment_date, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :forestFileId, the.recreation_comment_seq.NEXTVAL, 'AIA', 'N',
        :aiaComment, SYSDATE, 0,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  private static final String AIA_UPDATE_SQL =
      """
      UPDATE the.recreation_comment
         SET project_comment  = :aiaComment,
             comment_date     = SYSDATE,
             revision_count   = revision_count + 1,
             update_userid    = :userId,
             update_timestamp = SYSDATE
       WHERE forest_file_id = :forestFileId AND rec_comment_type_code = 'AIA'
      """;

  private static final String AIA_DELETE_SQL =
      """
      DELETE FROM the.recreation_comment
       WHERE forest_file_id = :forestFileId AND rec_comment_type_code = 'AIA'
      """;

  private static final String SITE_POINT_DELETE_SQL =
      "DELETE FROM the.recreation_site_point WHERE forest_file_id = :forestFileId";

  /**
   * Saves the project — creating it when the file has none (legacy's SAVE: ADD or CHANGE).
   *
   * @throws ResponseStatusException 404 no such file; 409 not allowed, or changed elsewhere;
   *     400 a field is invalid, or the UTM point is outside the project area
   */
  @Transactional
  public SaveResult saveProject(String forestFileId, RecProjectRequests.Save r, String userId) {
    RecProjectService.Context c = gateProject(forestFileId);
    if (c.exists()) {
      if (r.revisionCount() == null || !r.revisionCount().equals(c.revisionCount())) {
        throw changedElsewhere("project");
      }
    } else if (r.revisionCount() != null) {
      throw changedElsewhere("project");
    }

    boolean trail = c.trailProject();
    List<String> e = new ArrayList<>(RecProjectChecks.project(r, trail));
    String[] stored = c.exists()
        ? jdbc.queryForObject(STORED_CODES_SQL, new MapSqlParameterSource("forestFileId",
            forestFileId), (rs, n) -> new String[] {
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5)})
        : new String[5];
    code(e, "recreation_risk_rating_code", "Risk Rating", trim(r.riskRatingCode()), stored[0]);
    code(e, "recreation_feature_code", "Significant Recreation Feature",
        trim(r.featureCode()), stored[1]);
    code(e, "recreation_user_days_code", "User Days", trim(r.userDaysCode()), stored[2]);
    code(e, "recreation_maintain_std_code", "Maintenance Standard",
        trim(r.maintainStdCode()), stored[3]);
    code(e, "recreation_control_access_code", "Controlled Access Type",
        trim(r.controlAccessCode()), stored[4]);
    badRequest(e);

    BigDecimal utmZone = RecProjectChecks.number(r.utmZone());
    BigDecimal utmEasting = RecProjectChecks.number(r.utmEasting());
    BigDecimal utmNorthing = RecProjectChecks.number(r.utmNorthing());
    // Right of Way is entered only on a trail; otherwise it shows and keeps the stored value.
    BigDecimal rightOfWay = trail ? RecProjectChecks.number(r.rightOfWay()) : c.rightOfWay();
    String siteLocation = trim(r.siteLocation());
    String aiaComment = trim(r.aiaComment());

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("projectName", trim(r.projectName()))
        .addValue("established", r.projectEstablishedDate(), Types.DATE)
        .addValue("viewInd", trim(r.recreationViewInd()))
        .addValue("overflow", RecProjectChecks.number(r.overflowCampsites()), Types.NUMERIC)
        .addValue("utmZone", utmZone, Types.NUMERIC)
        .addValue("utmNorthing", utmNorthing, Types.NUMERIC)
        .addValue("utmEasting", utmEasting, Types.NUMERIC)
        .addValue("rightOfWay", rightOfWay, Types.NUMERIC)
        .addValue("controlAccess", trim(r.controlAccessCode()))
        .addValue("feature", trim(r.featureCode()))
        .addValue("maintainStd", trim(r.maintainStdCode()))
        .addValue("userDays", trim(r.userDaysCode()))
        .addValue("riskRating", trim(r.riskRatingCode()))
        .addValue("lastRecInspection", r.lastRecInspectionDate(), Types.DATE)
        .addValue("archInd", trim(r.archImpactAssessInd()))
        .addValue("archDate", r.archImpactDate(), Types.DATE)
        .addValue("bordenNo", trim(r.bordenNo()))
        .addValue("campHost", trim(r.campHostInd()))
        .addValue("lowMobility", trim(r.lowMobilityAccessInd()))
        .addValue("resourceFeature", trim(r.resourceFeatureInd()))
        .addValue("lastHazardTree", r.lastHzrdTreeAssessDate(), Types.DATE)
        .addValue("siteDescription", trim(r.siteDescription()))
        // Legacy's CHANGE upper-cases the closest community; done on create too here.
        .addValue("siteLocation", siteLocation == null ? null : siteLocation.toUpperCase())
        .addValue("aiaComment", aiaComment)
        .addValue("revisionCount", r.revisionCount(), Types.NUMERIC)
        .addValue("userId", userId);

    if (c.exists()) {
      if (jdbc.update(UPDATE_PROJECT_SQL, p) == 0) {
        throw changedElsewhere("project");
      }
    } else {
      try {
        jdbc.update(INSERT_PROJECT_SQL, p);
      } catch (DuplicateKeyException ex) {
        throw changedElsewhere("project");
      }
    }

    // The AIA comment lives in RECREATION_COMMENT: written, updated, or removed when blank.
    Long aiaCount = jdbc.queryForObject(AIA_COUNT_SQL, p, Long.class);
    boolean hasAia = aiaCount != null && aiaCount > 0;
    if (aiaComment != null) {
      jdbc.update(hasAia ? AIA_UPDATE_SQL : AIA_INSERT_SQL, p);
    } else if (hasAia) {
      jdbc.update(AIA_DELETE_SQL, p);
    }

    List<String> warnings = new ArrayList<>();
    if (utmEasting == null && utmNorthing == null) {
      jdbc.update(SITE_POINT_DELETE_SQL, p);
    } else if (utmEasting != null && utmNorthing != null
        && (!c.exists()
            || !same(utmZone, c.utmZone())
            || !same(utmEasting, c.utmEasting())
            || !same(utmNorthing, c.utmNorthing()))) {
      String problem = placeSitePoint(forestFileId, utmZone.intValue(), utmEasting, utmNorthing,
          userId);
      if (problem != null) {
        if (problem.startsWith(GEOMETRY_FAILED)) {
          warnings.add(SITE_POINT_WARNING);
        } else {
          // Legacy rolls the whole save back when the point is outside the project area.
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
        }
      }
    }

    if (c.featureCode() == null && trim(r.featureCode()) != null) {
      warnings.add(FEATURE_WARNING);
    }
    return new SaveResult(warnings);
  }

  private static boolean same(BigDecimal a, BigDecimal b) {
    return a == null ? b == null : b != null && a.compareTo(b) == 0;
  }

  private static final String GEOMETRY_FAILED = "GEOMETRY_FAILED:";

  /**
   * The project's site point from its UTM coordinates — legacy's UTMToBCAlbersConverter (NAD83
   * UTM zone 7-11 to BC Albers) done by Oracle Spatial, then {@code
   * FTA_701_PROJECT_DETAILS.save_site_geometry}, which checks the point lies in the project's
   * area and writes {@code RECREATION_SITE_POINT}.
   *
   * @return null when placed; the readable reason when outside the project area; a
   *     {@link #GEOMETRY_FAILED} marker when Oracle could not build the point
   */
  private String placeSitePoint(
      String forestFileId, int zone, BigDecimal easting, BigDecimal northing, String userId) {
    String error;
    try {
      error = jdbc.getJdbcTemplate().execute(
          (java.sql.Connection con) -> con.prepareCall(
              "DECLARE v_id VARCHAR2(10) := ?; v_user VARCHAR2(100) := ?;"
                  + " v_err VARCHAR2(4000); v_geom MDSYS.SDO_GEOMETRY;"
                  + " BEGIN"
                  + "  BEGIN"
                  + "   v_geom := SDO_CS.TRANSFORM(MDSYS.SDO_GEOMETRY(2001, ?,"
                  + "     MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL), 3005);"
                  // Legacy writes BC Albers under the schema's own SRID for it.
                  + "   v_geom.sdo_srid := 1000003005;"
                  + "  EXCEPTION WHEN OTHERS THEN v_err := '" + GEOMETRY_FAILED + "' || SQLERRM;"
                  + "  END;"
                  + "  IF v_err IS NULL THEN"
                  + "   the.fta_701_project_details.save_site_geometry(v_id, v_geom, v_user, v_err);"
                  + "  END IF;"
                  + "  ? := v_err;"
                  + " END;"),
          (CallableStatement cs) -> {
            cs.setString(1, forestFileId);
            cs.setString(2, userId);
            cs.setInt(3, 26900 + zone); // EPSG NAD83 / UTM zone nN
            cs.setBigDecimal(4, easting);
            cs.setBigDecimal(5, northing);
            cs.registerOutParameter(6, Types.VARCHAR);
            cs.execute();
            return cs.getString(6);
          });
    } catch (DataAccessException ex) {
      return GEOMETRY_FAILED + ex.getMessage();
    }
    if (error == null || error.isBlank()) {
      return null;
    }
    if (error.startsWith(GEOMETRY_FAILED)) {
      return error;
    }
    // 'fta.web.error.user.custom.msg:UTM point not in Recreation Project Area;'
    String text = error.replaceFirst("^[^:]*:", "").replaceAll(";\\s*$", "").trim();
    return text.isEmpty() ? "The UTM point could not be saved." : text;
  }

  // ─── Fees (SaveFee / DeleteFee) ──────────────────────────────────────────

  private static final String OVERLAP_SQL =
      """
      SELECT monday_ind, tuesday_ind, wednesday_ind, thursday_ind, friday_ind, saturday_ind,
             sunday_ind
        FROM the.recreation_fee
       WHERE forest_file_id = :forestFileId
         AND recreation_fee_code = :feeCode
         AND fee_start_date <= :endDate
         AND fee_end_date >= :startDate
         AND (:feeId IS NULL OR fee_id <> :feeId)
      """;

  private static final String INSERT_FEE_SQL =
      """
      INSERT INTO the.recreation_fee (
        fee_id, forest_file_id, fee_amount, fee_start_date, fee_end_date,
        monday_ind, tuesday_ind, wednesday_ind, thursday_ind, friday_ind, saturday_ind,
        sunday_ind, recreation_fee_code, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        the.recreation_fee_seq.NEXTVAL, :forestFileId, :amount, :startDate, :endDate,
        :mon, :tue, :wed, :thu, :fri, :sat,
        :sun, :feeCode, 1,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  private static final String UPDATE_FEE_SQL =
      """
      UPDATE the.recreation_fee
         SET fee_amount = :amount, fee_start_date = :startDate, fee_end_date = :endDate,
             monday_ind = :mon, tuesday_ind = :tue, wednesday_ind = :wed,
             thursday_ind = :thu, friday_ind = :fri, saturday_ind = :sat, sunday_ind = :sun,
             recreation_fee_code = :feeCode,
             revision_count = revision_count + 1,
             update_userid = :userId, update_timestamp = SYSDATE
       WHERE fee_id = :feeId AND forest_file_id = :forestFileId
         AND revision_count = :revisionCount
      """;

  /** Adds a fee ({@code feeId} null) or updates one. */
  @Transactional
  public void saveFee(String forestFileId, Long feeId, RecProjectRequests.Fee f, String userId) {
    gateChild(forestFileId);
    List<String> e = RecProjectChecks.feeForm(f);
    badRequest(e);
    String feeCode = trim(f.feeCode());
    String storedCode = feeId == null ? null : jdbc.query(
        "SELECT recreation_fee_code FROM the.recreation_fee"
            + " WHERE fee_id = :feeId AND forest_file_id = :forestFileId",
        new MapSqlParameterSource("feeId", feeId).addValue("forestFileId", forestFileId),
        (rs, n) -> rs.getString(1)).stream().findFirst().orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee not found."));
    code(e, "recreation_fee_code", "Fee Type", feeCode, storedCode);
    e.addAll(RecProjectChecks.feeDays(f));
    badRequest(e);

    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("feeId", feeId, Types.NUMERIC)
        .addValue("feeCode", feeCode)
        .addValue("amount", RecProjectChecks.number(f.amount()), Types.NUMERIC)
        .addValue("startDate", f.startDate(), Types.DATE)
        .addValue("endDate", f.endDate(), Types.DATE)
        .addValue("mon", yn(f.monday()))
        .addValue("tue", yn(f.tuesday()))
        .addValue("wed", yn(f.wednesday()))
        .addValue("thu", yn(f.thursday()))
        .addValue("fri", yn(f.friday()))
        .addValue("sat", yn(f.saturday()))
        .addValue("sun", yn(f.sunday()))
        .addValue("revisionCount", f.revisionCount(), Types.NUMERIC)
        .addValue("userId", userId);

    // validate_fee: fees of the same type with overlapping dates cannot share a day.
    Set<DayOfWeek> days = RecProjectChecks.days(f);
    boolean overlap = jdbc.query(OVERLAP_SQL, p, (rs, n) -> {
      Set<DayOfWeek> other = EnumSet.noneOf(DayOfWeek.class);
      DayOfWeek[] order = DayOfWeek.values();
      for (int i = 0; i < 7; i++) {
        if ("Y".equals(rs.getString(i + 1))) {
          other.add(order[i]);
        }
      }
      return RecProjectChecks.sharesDay(days, other);
    }).contains(Boolean.TRUE);
    if (overlap) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Overlapping Fees exist");
    }

    if (feeId == null) {
      jdbc.update(INSERT_FEE_SQL, p);
    } else if (f.revisionCount() == null || jdbc.update(UPDATE_FEE_SQL, p) == 0) {
      throw changedElsewhere("fee");
    }
  }

  @Transactional
  public void deleteFee(String forestFileId, long feeId, Long revisionCount) {
    gateChild(forestFileId);
    int n = jdbc.update(
        """
        DELETE FROM the.recreation_fee
         WHERE fee_id = :feeId AND forest_file_id = :forestFileId
           AND revision_count = :revisionCount
        """,
        new MapSqlParameterSource("feeId", feeId)
            .addValue("forestFileId", forestFileId)
            .addValue("revisionCount", revisionCount, Types.NUMERIC));
    if (n == 0) {
      throw changedElsewhere("fee");
    }
  }

  private static String yn(boolean b) {
    return b ? "Y" : "N";
  }

  // ─── Access (FTA_701_PROJECT_ACCESS SAVE_ITEM / REMOVE_ITEM) ─────────────

  @Transactional
  public void addAccess(String forestFileId, RecProjectRequests.Access a, String userId) {
    gateChild(forestFileId);
    String access = trim(a.accessCode());
    String sub = trim(a.subAccessCode());
    List<String> e = new ArrayList<>();
    if (access == null) {
      e.add("Access Type is required.");
    }
    if (sub == null) {
      e.add("Access Sub Type is required.");
    }
    badRequest(e);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("access", access)
        .addValue("sub", sub)
        .addValue("userId", userId);
    // validate_access
    Long valid = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.recreation_access_xref
         WHERE recreation_access_code = :access AND recreation_sub_access_code = :sub
        """, p, Long.class);
    if (valid == null || valid == 0) {
      e.add("Invalid access/sub access combination");
    }
    Long dup = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.recreation_access
         WHERE forest_file_id = :forestFileId
           AND recreation_access_code = :access AND recreation_sub_access_code = :sub
        """, p, Long.class);
    if (dup != null && dup > 0) {
      e.add("That access/sub access combination already exists for this project");
    }
    badRequest(e);
    try {
      jdbc.update(
          """
          INSERT INTO the.recreation_access (
            forest_file_id, recreation_access_code, recreation_sub_access_code, revision_count,
            entry_userid, entry_timestamp, update_userid, update_timestamp
          ) VALUES (
            :forestFileId, :access, :sub, 1, :userId, SYSDATE, :userId, SYSDATE
          )
          """, p);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "That access/sub access combination already exists for this project");
    }
  }

  @Transactional
  public void deleteAccess(
      String forestFileId, String accessCode, String subAccessCode, Long revisionCount) {
    gateChild(forestFileId);
    int n = jdbc.update(
        """
        DELETE FROM the.recreation_access
         WHERE forest_file_id = :forestFileId
           AND recreation_access_code = :access AND recreation_sub_access_code = :sub
           AND revision_count = :revisionCount
        """,
        new MapSqlParameterSource("forestFileId", forestFileId)
            .addValue("access", accessCode)
            .addValue("sub", subAccessCode)
            .addValue("revisionCount", revisionCount, Types.NUMERIC));
    if (n == 0) {
      throw changedElsewhere("access type");
    }
  }

  // ─── Districts (ADD_DISTRICT / REMOVE_DISTRICT) ──────────────────────────

  @Transactional
  public void addDistrict(String forestFileId, RecProjectRequests.District d, String userId) {
    gateChild(forestFileId);
    String code = trim(d.districtCode());
    List<String> e = new ArrayList<>();
    if (code == null) {
      e.add("Recreation District is required.");
    } else {
      code(e, "recreation_district_code", "Recreation District", code, null);
    }
    badRequest(e);
    MapSqlParameterSource p = new MapSqlParameterSource("forestFileId", forestFileId)
        .addValue("code", code)
        .addValue("userId", userId);
    Long dup = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.recreation_district_xref
         WHERE forest_file_id = :forestFileId AND recreation_district_code = :code
        """, p, Long.class);
    if (dup != null && dup > 0) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Recreation District must have unique entries.");
    }
    try {
      jdbc.update(
          """
          INSERT INTO the.recreation_district_xref (
            forest_file_id, recreation_district_code, revision_count,
            entry_userid, entry_timestamp, update_userid, update_timestamp
          ) VALUES (:forestFileId, :code, 1, :userId, SYSDATE, :userId, SYSDATE)
          """, p);
    } catch (DuplicateKeyException ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Recreation District must have unique entries.");
    }
  }

  /** Legacy gates Remove District by the project's (parent) Save, not the child rule. */
  @Transactional
  public void removeDistrict(String forestFileId, String districtCode) {
    gateProject(forestFileId);
    int n = jdbc.update(
        """
        DELETE FROM the.recreation_district_xref
         WHERE forest_file_id = :forestFileId AND recreation_district_code = :code
        """,
        new MapSqlParameterSource("forestFileId", forestFileId).addValue("code", districtCode));
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "That district is no longer on the project. Reload and try again.");
    }
  }

  // ─── Establishment orders (SaveAttachment / DeleteAttachment / View) ─────

  @Transactional
  public void addAttachment(
      String forestFileId, RecProjectRequests.Attachment a, String userId) {
    gateChild(forestFileId);
    String name = trim(a.fileName());
    List<String> e = new ArrayList<>();
    byte[] content = null;
    try {
      content = a.contentBase64() == null ? null : Base64.getDecoder().decode(a.contentBase64());
    } catch (IllegalArgumentException ex) {
      content = null;
    }
    if (name == null || content == null || content.length == 0) {
      e.add("The file specified could not be uploaded.");
    } else {
      if (!"PDF".equalsIgnoreCase(extension(name))) {
        e.add("Establishment Order must be a PDF.");
      } else if (content.length > MAX_ATTACHMENT_BYTES) {
        e.add("Maximum file upload size of " + MAX_ATTACHMENT_BYTES + " exceeded.");
      }
      if (name.length() > MAX_ATTACHMENT_NAME) {
        e.add("File name must not exceed " + MAX_ATTACHMENT_NAME + " characters.");
      }
    }
    badRequest(e);
    MapSqlParameterSource p = new MapSqlParameterSource("forestFileId", forestFileId)
        .addValue("name", name)
        .addValue("content", content)
        .addValue("userId", userId);
    // CREATE_ATTACHMENT's TOO_MANY_ROWS: the file name already names an order of the project.
    Long dup = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM the.recreation_attachment
         WHERE forest_file_id = :forestFileId AND attachment_file_name = :name
        """, p, Long.class);
    if (dup != null && dup > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Each Establishment Order document must have a unique file name.");
    }
    Long attachmentId = jdbc.queryForObject(
        "SELECT the.recreation_attachment_seq.NEXTVAL FROM dual", p, Long.class);
    p.addValue("attachmentId", attachmentId);
    jdbc.update(
        """
        INSERT INTO the.recreation_attachment (
          forest_file_id, recreation_attachment_id, revision_count,
          entry_userid, entry_timestamp, update_userid, update_timestamp, attachment_file_name
        ) VALUES (
          :forestFileId, :attachmentId, 1, :userId, SYSDATE, :userId, SYSDATE, :name
        )
        """, p);
    jdbc.update(
        """
        INSERT INTO the.recreation_attachment_content (
          forest_file_id, recreation_attachment_id, attachment_content
        ) VALUES (:forestFileId, :attachmentId, :content)
        """, p);
  }

  @Transactional
  public void deleteAttachment(String forestFileId, long attachmentId) {
    gateChild(forestFileId);
    MapSqlParameterSource p = new MapSqlParameterSource("forestFileId", forestFileId)
        .addValue("attachmentId", attachmentId);
    jdbc.update(
        """
        DELETE FROM the.recreation_attachment_content
         WHERE forest_file_id = :forestFileId AND recreation_attachment_id = :attachmentId
        """, p);
    int n = jdbc.update(
        """
        DELETE FROM the.recreation_attachment
         WHERE forest_file_id = :forestFileId AND recreation_attachment_id = :attachmentId
        """, p);
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "That establishment order is no longer on the project. Reload and try again.");
    }
  }

  /** GET_BLOB — the order's PDF; read by anyone who can open the tenure. */
  public AttachmentFile attachment(String forestFileId, long attachmentId) {
    return jdbc.query(
        """
        SELECT ra.attachment_file_name, rac.attachment_content
          FROM the.recreation_attachment ra
          JOIN the.recreation_attachment_content rac
            ON rac.forest_file_id = ra.forest_file_id
           AND rac.recreation_attachment_id = ra.recreation_attachment_id
         WHERE ra.forest_file_id = :forestFileId AND ra.recreation_attachment_id = :attachmentId
        """,
        new MapSqlParameterSource("forestFileId", forestFileId)
            .addValue("attachmentId", attachmentId),
        (rs, n) -> new AttachmentFile(
            rs.getString(1), Objects.requireNonNullElse(rs.getBytes(2), new byte[0])))
        .stream().findFirst().orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Establishment order not found."));
  }

  static String extension(String fileName) {
    int i = fileName.lastIndexOf('.');
    return i > 0 && i + 1 < fileName.length() ? fileName.substring(i + 1) : "";
  }
}
