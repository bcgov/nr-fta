package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.MODIFIED;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.harvestOrZero;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.hayRowBlank;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.integer;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.trim;
import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.upper;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayProvisionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationsDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRowRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HaySaveRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HaySaveResult;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Hay cutting rotation tab — legacy FTA612 (Hay Cutting Rotations),
 * {@code FTA_612_HAYCUT_ROTAT}.
 *
 * <ul>
 *   <li>Read ({@code GET}): the tenure's authorized forage tonnes ({@code RANGE_TENURE}), the
 *       year's provision (non-use, billable) and its {@code MEADOW_ROTATION} rows. The year
 *       defaults as legacy's ({@code setDefaultYear}): this year when it is in the term,
 *       otherwise the term's first year.
 *   <li>Save: legacy saves the whole grid at once ({@code Fta612HaycutRotatAction.handleSave})
 *       — the provision ({@code SAVE_PROVISION}), then each changed row
 *       ({@code SAVE_ROTATION}) or row marked for deletion ({@code DELETE}), then the new
 *       rows — in one transaction, all or nothing. The form first requires the year's harvest
 *       plus non-use to equal the authorized tonnes, so rows cannot be saved one at a time.
 *       Each saved row passes {@code check_ru}, {@code check_tenure_term},
 *       {@code check_meadow_info} and {@code check_range_provision}. ({@code check_block}, the
 *       permit block against the spatial application, was switched off in legacy itself.)
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class HayCuttingRotationService {

  private static final String AUTH_TONNES_SQL =
      """
      SELECT NVL(MAX(authorized_harvest_tonnes), 0) FROM the.range_tenure
       WHERE forest_file_id = :forestFileId
      """;

  private static final String PROVISION_SQL =
      """
      SELECT nonuse_forage_tonnes, billable_non_use_ind, revision_count
        FROM the.range_provision
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String ROTATIONS_SQL =
      """
      SELECT meadow_rotation_skey, calendar_year, permit_block_id, range_unit_id, meadow_name,
             authorized_harvestable_forage, update_timestamp, update_userid, revision_count
        FROM the.meadow_rotation
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
       ORDER BY rotation_line_no, meadow_rotation_skey
      """;

  /** {@code get_first_year}, when the term gives no year: this year's rotations, else the first. */
  private static final String FIRST_YEAR_SQL =
      """
      SELECT NVL(MAX(CASE WHEN calendar_year = :thisYear THEN calendar_year END),
                 MIN(calendar_year))
        FROM the.meadow_rotation
       WHERE forest_file_id = :forestFileId
      """;

  private static final String INSERT_PROVISION_SQL =
      """
      INSERT INTO the.range_provision (
        forest_file_id, calendar_year, nonuse_forage_tonnes, billable_non_use_ind,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :forestFileId, :year, :nonUse, :billable,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  private static final String UPDATE_PROVISION_SQL =
      """
      UPDATE the.range_provision
         SET nonuse_forage_tonnes = :nonUse,
             billable_non_use_ind = :billable,
             revision_count       = revision_count + 1,
             update_userid        = :userId,
             update_timestamp     = SYSDATE
       WHERE forest_file_id = :forestFileId
         AND calendar_year = :year
         AND revision_count = :rev
      """;

  private static final String PROVISION_COUNT_SQL =
      """
      SELECT COUNT(*) FROM the.range_provision
       WHERE forest_file_id = :forestFileId AND calendar_year = :year
      """;

  private static final String RANGE_UNIT_SQL =
      "SELECT COUNT(*) FROM the.range_unit WHERE range_unit_id = :rangeUnitId";

  /** {@code check_meadow_info}: file, year, range unit and meadow name are unique. */
  private static final String MEADOW_TAKEN_SQL =
      """
      SELECT COUNT(*) FROM the.meadow_rotation
       WHERE forest_file_id = :forestFileId
         AND calendar_year = :year
         AND range_unit_id = :rangeUnitId
         AND meadow_name = :meadowName
         AND (:skey IS NULL OR meadow_rotation_skey <> :skey)
      """;

  /**
   * {@code save_rotation}'s insert. Legacy numbered a new row with the year's highest line
   * number (not one past it); here it gets the next one.
   */
  private static final String INSERT_ROTATION_SQL =
      """
      INSERT INTO the.meadow_rotation (
        meadow_rotation_skey, forest_file_id, calendar_year, range_unit_id, permit_block_id,
        meadow_name, authorized_harvestable_forage, rotation_line_no,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count,
        map_feature_id
      ) VALUES (
        the.meadow_rotation_seq.NEXTVAL, :forestFileId, :year, :rangeUnitId, :permitBlockId,
        :meadowName, :harvest,
        (SELECT NVL(MAX(rotation_line_no), 0) + 1 FROM the.meadow_rotation
          WHERE forest_file_id = :forestFileId AND calendar_year = :year),
        :userId, SYSDATE, :userId, SYSDATE, 1,
        NULL
      )
      """;

  private static final String UPDATE_ROTATION_SQL =
      """
      UPDATE the.meadow_rotation
         SET range_unit_id                 = :rangeUnitId,
             permit_block_id               = :permitBlockId,
             meadow_name                   = :meadowName,
             authorized_harvestable_forage = :harvest,
             update_userid                 = :userId,
             update_timestamp              = SYSDATE,
             revision_count                = revision_count + 1
       WHERE meadow_rotation_skey = :skey
         AND forest_file_id = :forestFileId
         AND revision_count = :rev
      """;

  private static final String DELETE_ROTATION_SQL =
      """
      DELETE FROM the.meadow_rotation
       WHERE meadow_rotation_skey = :skey
         AND forest_file_id = :forestFileId
         AND revision_count = :rev
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RotationsTenureService tenures;

  public HayCuttingRotationService(NamedParameterJdbcTemplate jdbc, RotationsTenureService tenures) {
    this.jdbc = jdbc;
    this.tenures = tenures;
  }

  /**
   * The tab for {@code year}, or legacy's default year when null.
   *
   * @throws ResponseStatusException 404 when the file does not exist
   */
  public HayRotationsDto get(String forestFileId, Integer year) {
    RotationsTenure t = tenures.load(forestFileId);
    RotationsRules rules = RotationsRules.hayCutting(t);
    if (!rules.applies()) {
      return new HayRotationsDto(rules, List.of(), null, null, List.of());
    }
    Integer y = year != null ? year : defaultYear(forestFileId, t);
    if (y == null) {
      return new HayRotationsDto(rules, t.termYears(), null, null, List.of());
    }
    Map<String, Object> key = Map.of("forestFileId", forestFileId, "year", y);
    int authTonnes = authorizedTonnes(forestFileId);
    List<HayRotationDto> rotations = rows(key);
    int harvest = rotations.stream()
        .map(HayRotationDto::authorizedHarvestableForage)
        .filter(Objects::nonNull)
        .mapToInt(Integer::intValue)
        .sum();
    List<Map<String, Object>> rp = jdbc.queryForList(PROVISION_SQL, key);
    Integer nonUse = rp.isEmpty() ? null : toInteger(rp.get(0).get("nonuse_forage_tonnes"));
    String billable = rp.isEmpty() ? null : (String) rp.get(0).get("billable_non_use_ind");
    Long rev = rp.isEmpty() ? null : toLong(rp.get(0).get("revision_count"));
    HayProvisionDto provision = new HayProvisionDto(
        authTonnes,
        // Fta612HaycutRotatForm.setDefaults: a blank non-use shows as 0.
        nonUse == null ? 0 : nonUse,
        billable == null ? "N" : billable,
        harvest,
        authTonnes,
        rev);
    return new HayRotationsDto(rules, t.termYears(), y, provision, rotations);
  }

  /**
   * Save — the provision and the grid, all or nothing.
   *
   * @throws ResponseStatusException 404 / 409 by the tab's rules; 400 with every failed check;
   *     409 when the provision or a row changed since it was read
   */
  @Transactional
  public HaySaveResult save(String forestFileId, int year, HaySaveRequest q, String userId) {
    RotationsTenure t = tenures.loadWritable(forestFileId, RotationsRules::hayCutting);
    List<HayRowRequest> rows = q.rows() == null ? List.of() : q.rows();
    Map<String, Object> key = Map.of("forestFileId", forestFileId, "year", year);
    Map<Long, HayRotationDto> current = new HashMap<>();
    for (HayRotationDto r : rows(key)) {
      current.put(r.meadowRotationSkey(), r);
    }

    // Which rows go to the database: legacy skips blank new rows and unchanged existing ones.
    boolean[] changed = new boolean[rows.size()];
    List<String> e = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      HayRowRequest r = rows.get(i);
      if (r.meadowRotationSkey() == null) {
        // A new row removed again before saving is simply not sent on.
        changed[i] = !r.delete() && !hayRowBlank(r);
      } else {
        HayRotationDto was = current.get(r.meadowRotationSkey());
        if (was == null) {
          // Deleted by someone else since the grid was read.
          throw modified();
        }
        changed[i] = r.delete() || !same(r, was);
      }
    }

    // Fta612HaycutRotatForm, "Save".
    Integer nonUse = integer(q.nonUse(), "Non-Use", "Non-Use", 0, 99999, e);
    String billable = GrazingRotationService.billable(q.billableInd(), e);
    int authTonnes = authorizedTonnes(forestFileId);
    if (nonUse != null) {
      // totalHarvest: the rows kept (as edited), the rows the grid did not send (as stored),
      // and the new ones, plus non-use.
      long total = nonUse;
      for (HayRowRequest r : rows) {
        if (!r.delete()) {
          total += harvestOrZero(r.authorizedHarvest());
        }
      }
      for (HayRotationDto was : current.values()) {
        boolean sent = rows.stream()
            .anyMatch(r -> Objects.equals(r.meadowRotationSkey(), was.meadowRotationSkey()));
        if (!sent && was.authorizedHarvestableForage() != null) {
          total += was.authorizedHarvestableForage();
        }
      }
      String totalError = RotationsFieldChecks.hayTotalError(total, authTonnes);
      if (totalError != null) {
        e.add(totalError);
      }
    }
    e.addAll(RotationsFieldChecks.hayRowErrors(rows, i -> changed[i]));
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    // SAVE_PROVISION.
    MapSqlParameterSource rp = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("year", year, Types.INTEGER)
        .addValue("nonUse", nonUse, Types.INTEGER)
        .addValue("billable", billable)
        .addValue("rev", q.provisionRevisionCount(), Types.NUMERIC)
        .addValue("userId", userId);
    if (q.provisionRevisionCount() == null) {
      Long n = jdbc.queryForObject(PROVISION_COUNT_SQL, rp, Long.class);
      if (n != null && n > 0) {
        throw modified();
      }
      jdbc.update(INSERT_PROVISION_SQL, rp);
    } else if (jdbc.update(UPDATE_PROVISION_SQL, rp) == 0) {
      throw modified();
    }

    // The rows, existing ones first as legacy's grid had them, then the new ones; each
    // row's checks see the rows saved before it.
    List<String> rowErrors = new ArrayList<>();
    int saved = 0;
    int deleted = 0;
    for (int pass = 0; pass < 2; pass++) {
      for (int i = 0; i < rows.size(); i++) {
        HayRowRequest r = rows.get(i);
        boolean existing = r.meadowRotationSkey() != null;
        if (!changed[i] || existing != (pass == 0)) {
          continue;
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("forestFileId", forestFileId)
            .addValue("year", year, Types.INTEGER)
            .addValue("skey", r.meadowRotationSkey(), Types.NUMERIC)
            .addValue("rev", r.revisionCount(), Types.NUMERIC)
            .addValue("permitBlockId", trim(r.permitBlockId()))
            .addValue("rangeUnitId", upper(r.rangeUnitId()))
            .addValue("meadowName", upper(r.meadowName()))
            .addValue("harvest", toInteger(trim(r.authorizedHarvest())), Types.INTEGER)
            .addValue("userId", userId);
        if (r.delete()) {
          if (r.revisionCount() == null || jdbc.update(DELETE_ROTATION_SQL, p) == 0) {
            throw modified();
          }
          deleted++;
          continue;
        }
        List<String> problems = rowChecks(t, year, p);
        if (!problems.isEmpty()) {
          String name = (String) p.getValue("meadowName");
          for (String problem : problems) {
            rowErrors.add("Row " + (i + 1) + (name == null ? "" : " (" + name + ")") + ": "
                + problem);
          }
          continue;
        }
        if (existing) {
          if (r.revisionCount() == null || jdbc.update(UPDATE_ROTATION_SQL, p) == 0) {
            throw modified();
          }
        } else {
          jdbc.update(INSERT_ROTATION_SQL, p);
        }
        saved++;
      }
    }
    if (!rowErrors.isEmpty()) {
      // Rolls the whole save back, as legacy's store.rollback() did.
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", rowErrors));
    }
    String message = saved + deleted > 0
        ? "Save successful. " + saved + " rows saved " + deleted + " rows deleted."
        : "Save successful.";
    return new HaySaveResult(saved, deleted, message);
  }

  /**
   * {@code SAVE_ROTATION}'s checks on one row: {@code check_ru}, {@code check_tenure_term},
   * {@code check_meadow_info} ({@code check_range_provision} always passes here: the
   * provision was saved first).
   */
  private List<String> rowChecks(RotationsTenure t, int year, MapSqlParameterSource p) {
    List<String> e = new ArrayList<>();
    Long ru = jdbc.queryForObject(RANGE_UNIT_SQL, p, Long.class);
    if (ru == null || ru != 1) {
      e.add("Range Unit " + p.getValue("rangeUnitId") + " is not in the system.");
    }
    if (!t.inTerm(year)) {
      e.add("The Rotation calendar year does not fall within the tenure term of the File.");
    }
    Long taken = jdbc.queryForObject(MEADOW_TAKEN_SQL, p, Long.class);
    if (taken != null && taken > 0) {
      e.add("The combination of File Id, Calendar year, Range Unit and Meadow Name is not"
          + " unique.");
    }
    return e;
  }

  private List<HayRotationDto> rows(Map<String, Object> key) {
    return jdbc.query(ROTATIONS_SQL, key, (rs, n) -> new HayRotationDto(
        rs.getLong("meadow_rotation_skey"),
        rs.getInt("calendar_year"),
        rs.getString("permit_block_id"),
        rs.getString("range_unit_id"),
        rs.getString("meadow_name"),
        rs.getObject("authorized_harvestable_forage", Integer.class),
        rs.getObject("update_timestamp", LocalDate.class),
        rs.getString("update_userid"),
        rs.getLong("revision_count")));
  }

  private int authorizedTonnes(String forestFileId) {
    Integer n = jdbc.queryForObject(
        AUTH_TONNES_SQL, Map.of("forestFileId", forestFileId), Integer.class);
    return n == null ? 0 : n;
  }

  private Integer defaultYear(String forestFileId, RotationsTenure t) {
    int thisYear = LocalDate.now().getYear();
    if (t.hasTerm()) {
      return t.inTerm(thisYear) ? Integer.valueOf(thisYear) : t.termStartYear();
    }
    return jdbc.queryForObject(
        FIRST_YEAR_SQL, Map.of("forestFileId", forestFileId, "thisYear", thisYear),
        Integer.class);
  }

  /** {@code noChange}: the four inputs as they were read (meadow names compare uppercased). */
  private static boolean same(HayRowRequest r, HayRotationDto was) {
    return Objects.equals(trim(r.permitBlockId()), trim(was.permitBlockId()))
        && Objects.equals(upper(r.rangeUnitId()), upper(was.rangeUnitId()))
        && Objects.equals(upper(r.meadowName()), upper(was.meadowName()))
        && Objects.equals(toInteger(trim(r.authorizedHarvest())),
            was.authorizedHarvestableForage());
  }

  private static Integer toInteger(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.valueOf(v.toString().trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long toLong(Object v) {
    return v instanceof Number n ? n.longValue() : null;
  }

  private static ResponseStatusException modified() {
    return new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
  }
}
