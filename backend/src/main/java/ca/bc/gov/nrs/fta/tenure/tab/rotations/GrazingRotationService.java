package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.MODIFIED;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.NOT_IN_TERM;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.integer;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.trim;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.upper;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingProvisionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingProvisionRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationsDto;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Grazing rotation tab — legacy FTA611 (Grazing Rotations), {@code FTA_611_GRAZE_ROTATN}.
 *
 * <ul>
 *   <li>Read ({@code GET}): the year's {@code RANGE_PROVISION} (non-use, billable, totals, the
 *       maximum head of each livestock) and its {@code LIVESTOCK_ROTATION} rows. The year
 *       defaults as legacy's: this year when it has rotations, otherwise the term's first year.
 *   <li>Save Provision ({@code SAVE_PROV}): the year's header, inserted when the year has no
 *       provision yet — only for a year within the term.
 *   <li>Save ({@code SAVE}): add or change a rotation, behind the form's checks and the
 *       package's range unit/pasture check; TTL AUMs are calculated when left blank
 *       ({@code setTTLAUM}). The year's provision is created first when missing
 *       ({@code save_provision}) and its AUM/PLD totals are re-summed after
 *       ({@code update_rng_prov}).
 *   <li>Delete ({@code REMOVE}): the rotation, re-summing the totals, and the year's provision
 *       too once it has no rotations and no maximum livestock numbers.
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class GrazingRotationService {

  private static final String DEFAULT_YEAR_SQL =
      """
      SELECT COUNT(*) FROM the.livestock_rotation
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String PROVISION_SQL =
      """
      SELECT nonuse_forage_tonnes, billable_non_use_ind,
             total_authorized_grazbl_forage, total_private_land_graz_forage,
             max_no_of_cattle, max_no_of_horses, max_no_of_sheep, max_no_of_other_livestock,
             revision_count
        FROM the.range_provision
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String ROTATIONS_SQL =
      """
      SELECT lr.livestock_rotation_skey,
             lr.calendar_year,
             lr.livestock_code,
             CASE WHEN lc.description IS NOT NULL
                  THEN lr.livestock_code || ' - ' || lc.description
                  ELSE lr.livestock_code END                     AS livestock_desc,
             lr.livestock_count,
             TO_CHAR(lr.begin_rotation_date, 'MM-DD')            AS begin_rotation_date,
             TO_CHAR(lr.end_rotation_date, 'MM-DD')              AS end_rotation_date,
             lr.range_unit_id,
             lr.pasture_id,
             lr.authorized_grazable_forage,
             lr.private_land_grazable_forage,
             lr.rotation_line_no,
             lr.revision_count
        FROM the.livestock_rotation lr
        LEFT JOIN the.livestock_code lc ON lc.livestock_code = lr.livestock_code
       WHERE lr.forest_file_id = :forestFileId
         AND lr.calendar_year = :year
       ORDER BY lr.rotation_line_no, lr.livestock_rotation_skey
      """;

  private static final String LIVESTOCK_CODES_SQL =
      """
      SELECT livestock_code AS code, livestock_code || ' - ' || description AS description
        FROM the.livestock_code
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY livestock_code
      """;

  private static final String LIVESTOCK_CODE_SQL =
      """
      SELECT CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 'Y' ELSE 'N' END
        FROM the.livestock_code WHERE livestock_code = :code
      """;

  /** {@code SAVE}'s {@code curRangeUnit}: the pasture must be one of the range unit's. */
  private static final String PASTURE_SQL =
      """
      SELECT COUNT(*)
        FROM the.range_unit ru
        JOIN the.range_unit_pasture rup ON rup.range_unit_id = ru.range_unit_id
       WHERE ru.range_unit_id = :rangeUnitId
         AND rup.pasture_id = :pastureId
      """;

  private static final String PROVISION_EXISTS_SQL =
      """
      SELECT COUNT(*) FROM the.range_provision
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String INSERT_PROVISION_SQL =
      """
      INSERT INTO the.range_provision (
        forest_file_id, calendar_year, nonuse_forage_tonnes, billable_non_use_ind,
        max_no_of_cattle, max_no_of_horses, max_no_of_sheep, max_no_of_other_livestock,
        revision_count, entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :forestFileId, :year, :nonUse, :billable,
        :cattle, :horses, :sheep, :other,
        0, :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  private static final String UPDATE_PROVISION_SQL =
      """
      UPDATE the.range_provision
         SET nonuse_forage_tonnes      = :nonUse,
             billable_non_use_ind      = :billable,
             max_no_of_cattle          = :cattle,
             max_no_of_horses          = :horses,
             max_no_of_sheep           = :sheep,
             max_no_of_other_livestock = :other,
             revision_count            = revision_count + 1,
             update_userid             = :userId,
             update_timestamp          = SYSDATE
       WHERE forest_file_id = :forestFileId
         AND calendar_year = :year
         AND revision_count = :rev
      """;

  /** {@code update_rng_prov} / {@code REMOVE}: the year's totals from its rotations. */
  private static final String UPDATE_TOTALS_SQL =
      """
      UPDATE the.range_provision rp
         SET total_authorized_grazbl_forage = (
               SELECT NVL(SUM(lr.authorized_grazable_forage), 0) FROM the.livestock_rotation lr
                WHERE lr.forest_file_id = rp.forest_file_id
                  AND lr.calendar_year = rp.calendar_year),
             total_private_land_graz_forage = (
               SELECT NVL(SUM(lr.private_land_grazable_forage), 0) FROM the.livestock_rotation lr
                WHERE lr.forest_file_id = rp.forest_file_id
                  AND lr.calendar_year = rp.calendar_year),
             revision_count   = revision_count + 1,
             update_userid    = :userId,
             update_timestamp = SYSDATE
       WHERE rp.forest_file_id = :forestFileId
         AND rp.calendar_year = :year
      """;

  /** {@code ADD} numbers a new rotation after the year's last. */
  private static final String INSERT_ROTATION_SQL =
      """
      INSERT INTO the.livestock_rotation (
        livestock_rotation_skey, forest_file_id, calendar_year, livestock_code,
        begin_rotation_date, end_rotation_date, range_unit_id, pasture_id, rotation_line_no,
        livestock_count, authorized_grazable_forage, private_land_grazable_forage,
        revision_count, entry_timestamp, entry_userid, update_userid, update_timestamp
      ) VALUES (
        the.livestock_rotation_seq.NEXTVAL, :forestFileId, :year, :livestockCode,
        :begin, :end, :rangeUnitId, :pastureId,
        (SELECT NVL(MAX(rotation_line_no), 0) + 1 FROM the.livestock_rotation
          WHERE forest_file_id = :forestFileId AND calendar_year = :year),
        :count, :aums, :pld,
        1, SYSDATE, :userId, :userId, SYSDATE
      )
      """;

  private static final String UPDATE_ROTATION_SQL =
      """
      UPDATE the.livestock_rotation
         SET livestock_code               = :livestockCode,
             begin_rotation_date          = :begin,
             end_rotation_date            = :end,
             range_unit_id                = :rangeUnitId,
             pasture_id                   = :pastureId,
             livestock_count              = :count,
             authorized_grazable_forage   = :aums,
             private_land_grazable_forage = :pld,
             revision_count               = revision_count + 1,
             update_userid                = :userId,
             update_timestamp             = SYSDATE
       WHERE livestock_rotation_skey = :skey
         AND forest_file_id = :forestFileId
         AND calendar_year = :year
         AND revision_count = :rev
      """;

  private static final String CURRENT_CODE_SQL =
      """
      SELECT livestock_code FROM the.livestock_rotation
       WHERE livestock_rotation_skey = :skey
         AND forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String DELETE_ROTATION_SQL =
      """
      DELETE FROM the.livestock_rotation
       WHERE livestock_rotation_skey = :skey
         AND forest_file_id = :forestFileId
         AND calendar_year = :year
         AND revision_count = :rev
      """;

  /**
   * {@code REMOVE}: once the year has no rotations, its provision goes too when every maximum
   * livestock number is 0 or unset. (Legacy's condition mixes AND and OR without brackets, so
   * it fires on more cases than its comment says; this ports the comment's intent.)
   */
  private static final String DELETE_EMPTY_PROVISION_SQL =
      """
      DELETE FROM the.range_provision rp
       WHERE rp.forest_file_id = :forestFileId
         AND rp.calendar_year = :year
         AND NVL(rp.max_no_of_cattle, 0) = 0
         AND NVL(rp.max_no_of_horses, 0) = 0
         AND NVL(rp.max_no_of_sheep, 0) = 0
         AND NVL(rp.max_no_of_other_livestock, 0) = 0
         AND NOT EXISTS (SELECT 1 FROM the.livestock_rotation lr
                          WHERE lr.forest_file_id = rp.forest_file_id
                            AND lr.calendar_year = rp.calendar_year)
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RotationsTenureService tenures;

  public GrazingRotationService(NamedParameterJdbcTemplate jdbc, RotationsTenureService tenures) {
    this.jdbc = jdbc;
    this.tenures = tenures;
  }

  /**
   * The tab for {@code year}, or legacy's default year when null.
   *
   * @throws ResponseStatusException 404 when the file does not exist
   */
  public GrazingRotationsDto get(String forestFileId, Integer year) {
    RotationsTenure t = tenures.load(forestFileId);
    RotationsRules rules = RotationsRules.grazing(t);
    if (!rules.applies()) {
      return new GrazingRotationsDto(rules, List.of(), null, null, List.of());
    }
    Integer y = year != null ? year : defaultYear(forestFileId, t);
    if (y == null) {
      return new GrazingRotationsDto(rules, t.termYears(), null, null, List.of());
    }
    Map<String, Object> key = Map.of("forestFileId", forestFileId, "year", y);
    List<GrazingProvisionDto> provision = jdbc.query(PROVISION_SQL, key, (rs, n) -> {
      Integer nonUse = rs.getObject("nonuse_forage_tonnes", Integer.class);
      Integer auth = rs.getObject("total_authorized_grazbl_forage", Integer.class);
      Integer pld = rs.getObject("total_private_land_graz_forage", Integer.class);
      return new GrazingProvisionDto(
          true,
          nonUse,
          rs.getString("billable_non_use_ind"),
          auth,
          pld,
          // Legacy's "= Net Authorized": Non-Use + TTL AUMs − PLD, blanks as 0.
          nz(nonUse) + nz(auth) - nz(pld),
          rs.getObject("max_no_of_cattle", Integer.class),
          rs.getObject("max_no_of_horses", Integer.class),
          rs.getObject("max_no_of_sheep", Integer.class),
          rs.getObject("max_no_of_other_livestock", Integer.class),
          rs.getObject("revision_count", Long.class));
    });
    List<GrazingRotationDto> rotations = jdbc.query(ROTATIONS_SQL, key, (rs, n) ->
        new GrazingRotationDto(
            rs.getLong("livestock_rotation_skey"),
            rs.getInt("calendar_year"),
            rs.getString("livestock_code"),
            rs.getString("livestock_desc"),
            rs.getObject("livestock_count", Integer.class),
            rs.getString("begin_rotation_date"),
            rs.getString("end_rotation_date"),
            rs.getString("range_unit_id"),
            rs.getString("pasture_id"),
            rs.getObject("authorized_grazable_forage", Integer.class),
            rs.getObject("private_land_grazable_forage", Integer.class),
            rs.getObject("rotation_line_no", Integer.class),
            rs.getLong("revision_count")));
    GrazingProvisionDto p = provision.isEmpty()
        ? new GrazingProvisionDto(false, null, null, null, null, 0, null, null, null, null, null)
        : provision.get(0);
    return new GrazingRotationsDto(rules, t.termYears(), y, p, rotations);
  }

  /** The current livestock codes, "CODE - description" — the Animal dropdown. */
  public List<CodeOptionDto> livestockCodes() {
    return jdbc.query(LIVESTOCK_CODES_SQL, Map.of(), (rs, n) ->
        new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  /**
   * Save Provision — {@code save_provision}.
   *
   * @throws ResponseStatusException 404 / 409 by the tab's rules; 400 when a field is invalid
   *     or the year is outside the term; 409 when the provision changed since it was read
   */
  @Transactional
  public void saveProvision(
      String forestFileId, int year, GrazingProvisionRequest q, String userId) {
    RotationsTenure t = tenures.loadWritable(forestFileId, RotationsRules::grazing);
    List<String> e = new ArrayList<>();
    if (!t.inTerm(year)) {
      e.add(NOT_IN_TERM);
    }
    // Fta611GrazeRotatnForm, "Save Provision".
    Integer nonUse = integer(q.nonUseForageTonnes(), null, "Non-Use", 0, 99999, e);
    Integer cattle = integer(q.maxCattle(), null, "Cattle", 0, 99999, e);
    Integer horses = integer(q.maxHorses(), null, "Horse", 0, 9999, e);
    Integer sheep = integer(q.maxSheep(), null, "Sheep", 0, 9999, e);
    Integer other = integer(q.maxOtherLivestock(), null, "Other", 0, 9999, e);
    String billable = billable(q.billableNonUseInd(), e);
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("year", year, Types.INTEGER)
        .addValue("nonUse", nonUse, Types.INTEGER)
        .addValue("billable", billable)
        .addValue("cattle", cattle, Types.INTEGER)
        .addValue("horses", horses, Types.INTEGER)
        .addValue("sheep", sheep, Types.INTEGER)
        .addValue("other", other, Types.INTEGER)
        .addValue("rev", q.revisionCount(), Types.NUMERIC)
        .addValue("userId", userId);
    if (provisionExists(p)) {
      if (q.revisionCount() == null || jdbc.update(UPDATE_PROVISION_SQL, p) == 0) {
        throw modified();
      }
    } else {
      try {
        jdbc.update(INSERT_PROVISION_SQL, p);
      } catch (DuplicateKeyException ex) {
        throw modified();
      }
    }
  }

  /**
   * Save, for a new rotation — {@code SAVE} without a key, i.e. {@code ADD}.
   *
   * @throws ResponseStatusException as {@link #saveProvision}; 409 when the same rotation
   *     already exists
   */
  @Transactional
  public void add(String forestFileId, int year, GrazingRotationRequest q, String userId) {
    RotationsTenure t = tenures.loadWritable(forestFileId, RotationsRules::grazing);
    MapSqlParameterSource p = checked(forestFileId, t, year, q, null, userId);
    ensureProvision(p);
    try {
      jdbc.update(INSERT_ROTATION_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw duplicate(p);
    }
    jdbc.update(UPDATE_TOTALS_SQL, p);
  }

  /**
   * Save, for an existing rotation — {@code SAVE} with a key, i.e. {@code CHANGE}.
   *
   * @throws ResponseStatusException as {@link #add}; 409 when the rotation changed or went
   *     since it was read
   */
  @Transactional
  public void update(
      String forestFileId, int year, long skey, GrazingRotationRequest q, String userId) {
    RotationsTenure t = tenures.loadWritable(forestFileId, RotationsRules::grazing);
    List<String> current = jdbc.queryForList(
        CURRENT_CODE_SQL,
        Map.of("skey", skey, "forestFileId", forestFileId, "year", year),
        String.class);
    if (current.isEmpty()) {
      throw modified();
    }
    MapSqlParameterSource p = checked(forestFileId, t, year, q, current.get(0), userId)
        .addValue("skey", skey)
        .addValue("rev", q.revisionCount(), Types.NUMERIC);
    ensureProvision(p);
    int n;
    try {
      n = q.revisionCount() == null ? 0 : jdbc.update(UPDATE_ROTATION_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw duplicate(p);
    }
    if (n == 0) {
      throw modified();
    }
    jdbc.update(UPDATE_TOTALS_SQL, p);
  }

  /**
   * Delete — {@code REMOVE}.
   *
   * @throws ResponseStatusException 404 / 409 by the tab's rules; 409 when the rotation
   *     changed or went since it was read
   */
  @Transactional
  public void delete(String forestFileId, int year, long skey, long revisionCount, String userId) {
    tenures.loadWritable(forestFileId, RotationsRules::grazing);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("year", year, Types.INTEGER)
        .addValue("skey", skey)
        .addValue("rev", revisionCount)
        .addValue("userId", userId);
    if (jdbc.update(DELETE_ROTATION_SQL, p) == 0) {
      throw modified();
    }
    jdbc.update(UPDATE_TOTALS_SQL, p);
    jdbc.update(DELETE_EMPTY_PROVISION_SQL, p);
  }

  /**
   * The form's and the package's checks on a rotation ({@code Fta611GrazeRotatnForm} "Save",
   * {@code SAVE}'s range unit/pasture check, {@code save_provision}'s term check), and its
   * bind values with TTL AUMs worked out.
   *
   * @param currentCode the row's livestock code on a change — an expired code it already has
   *                    may stay
   */
  private MapSqlParameterSource checked(
      String forestFileId,
      RotationsTenure t,
      int year,
      GrazingRotationRequest q,
      String currentCode,
      String userId) {
    List<String> e = new ArrayList<>();
    if (!t.inTerm(year)) {
      e.add(NOT_IN_TERM);
    }
    String code = upper(q.livestockCode());
    if (code == null) {
      e.add("Animal is mandatory.");
    } else if (!code.equals(currentCode)) {
      List<String> active = jdbc.queryForList(LIVESTOCK_CODE_SQL, Map.of("code", code), String.class);
      if (active.isEmpty()) {
        e.add("Invalid Livestock.");
      } else if (!"Y".equals(active.get(0))) {
        e.add("Livestock (" + code + ") is an expired code.");
      }
    }
    Integer count = integer(q.livestockCount(), "No.", "Livestock Count", 0, 9999, e);
    LocalDate begin = day(q.beginRotationDate(), "Start", year, e);
    LocalDate end = day(q.endRotationDate(), "End", year, e);
    if (begin != null && end != null && begin.isAfter(end)) {
      e.add("Start must be less than or equal to End.");
    }
    String ru = upper(q.rangeUnitId());
    String pasture = upper(q.pastureId());
    if (ru == null) {
      e.add("Range Unit is mandatory.");
    }
    if (pasture == null) {
      e.add("Range Pasture is mandatory.");
    }
    Integer pld = integer(q.privateLandGrazableForage(), "PLD", "PLD", 0, 99999, e);
    Integer aums = integer(q.authorizedGrazableForage(), null, "TTL AUMs", 0, 99999, e);
    if (ru != null && pasture != null) {
      Long n = jdbc.queryForObject(
          PASTURE_SQL, Map.of("rangeUnitId", ru, "pastureId", pasture), Long.class);
      if (n == null || n == 0) {
        e.add("Invalid Range Unit/Pasture.");
      }
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
    if (trim(q.authorizedGrazableForage()) == null) {
      aums = RotationsFieldChecks.aums(code, count, begin, end);
    }
    return new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("year", year, Types.INTEGER)
        .addValue("livestockCode", code)
        .addValue("begin", begin, Types.DATE)
        .addValue("end", end, Types.DATE)
        .addValue("rangeUnitId", ru)
        .addValue("pastureId", pasture)
        .addValue("count", count, Types.INTEGER)
        .addValue("aums", aums, Types.INTEGER)
        .addValue("pld", pld, Types.INTEGER)
        .addValue("userId", userId);
  }

  /**
   * {@code save_provision} as {@code SAVE} calls it: the year gets a provision when it has
   * none (billable N, the rest blank — the page saves the header with Save Provision).
   */
  private void ensureProvision(MapSqlParameterSource p) {
    if (provisionExists(p)) {
      return;
    }
    MapSqlParameterSource blank = new MapSqlParameterSource()
        .addValue("forestFileId", p.getValue("forestFileId"))
        .addValue("year", p.getValue("year"), Types.INTEGER)
        .addValue("nonUse", null, Types.INTEGER)
        .addValue("billable", "N")
        .addValue("cattle", null, Types.INTEGER)
        .addValue("horses", null, Types.INTEGER)
        .addValue("sheep", null, Types.INTEGER)
        .addValue("other", null, Types.INTEGER)
        .addValue("userId", p.getValue("userId"));
    try {
      jdbc.update(INSERT_PROVISION_SQL, blank);
    } catch (DuplicateKeyException ex) {
      // Another save made it in between; it is there now, which is all this needs.
    }
  }

  private boolean provisionExists(MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(PROVISION_EXISTS_SQL, p, Long.class);
    return n != null && n > 0;
  }

  private Integer defaultYear(String forestFileId, RotationsTenure t) {
    int thisYear = LocalDate.now().getYear();
    Long n = jdbc.queryForObject(
        DEFAULT_YEAR_SQL, Map.of("forestFileId", forestFileId, "year", thisYear), Long.class);
    return n != null && n > 0 ? Integer.valueOf(thisYear) : t.termStartYear();
  }

  private static LocalDate day(String raw, String label, int year, List<String> e) {
    if (trim(raw) == null) {
      e.add(label + " is mandatory.");
      return null;
    }
    LocalDate d = RotationsFieldChecks.monthDay(raw, year);
    if (d == null) {
      e.add(label + " must be a valid day in the format MM-DD.");
    }
    return d;
  }

  /** Legacy's dropdown: blank (saved as N), N - Non-Billable, Y - Billable. */
  static String billable(String raw, List<String> e) {
    String v = upper(raw);
    if (v == null) {
      return "N";
    }
    if (!"Y".equals(v) && !"N".equals(v)) {
      e.add("Billable must be Y or N.");
    }
    return v;
  }

  private static int nz(Integer n) {
    return n == null ? 0 : n;
  }

  private static ResponseStatusException modified() {
    return new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
  }

  /** {@code ADD}/{@code CHANGE}'s DUP_VAL_ON_INDEX message. */
  private static ResponseStatusException duplicate(MapSqlParameterSource p) {
    return new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Rotation already exists for " + p.getValue("livestockCode")
            + " for the specified date(s) and range unit pasture.");
  }
}
