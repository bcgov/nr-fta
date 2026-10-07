package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import static ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.MODIFIED;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationResult;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsFieldChecks.CopyInput;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsRules.RotationKind;
import java.sql.Types;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Copy rotation tab — legacy FTA613 (Copy Grazing/Hay Cutting Rotations),
 * {@code FTA_613_COPY_GH_ROTA}.
 *
 * <p>Copies a source tenure's year — its {@code RANGE_PROVISION} and its rotations, livestock
 * for a grazing tenure (optionally only one range unit's) or meadow for a hay cutting one —
 * onto this tenure's target years: up to nine listed years, or every other year from a start
 * year to the end of the term. Each target year's rotations and provision are replaced. As
 * in legacy, a first attempt that would overwrite years with rotations writes nothing and
 * asks for confirmation ({@code p_warn_ind}); the copy guards the file's
 * {@code PROV_FOREST_USE.REVISION_COUNT} and bumps it.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class CopyRotationService {

  /** {@code ADD}: the source must be a range tenure (its NO_DATA_FOUND otherwise). */
  private static final String SOURCE_SQL =
      """
      SELECT pfu.file_type_code
        FROM the.prov_forest_use pfu
        JOIN the.range_tenure rt ON rt.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :src
      """;

  private static final String SOURCE_PROVISION_SQL =
      """
      SELECT COUNT(*) FROM the.range_provision
       WHERE forest_file_id = :src AND calendar_year = :srcYear
      """;

  private static final String RANGE_UNIT_SQL =
      "SELECT COUNT(*) FROM the.range_unit WHERE range_unit_id = :ru";

  /** {@code ADD}'s range unit check reads livestock rotations, whatever the file type. */
  private static final String SOURCE_RU_ROTATIONS_SQL =
      """
      SELECT COUNT(*) FROM the.livestock_rotation
       WHERE forest_file_id = :src AND calendar_year = :srcYear AND range_unit_id = :ru
      """;

  private static final String HAS_LIVESTOCK_SQL =
      """
      SELECT COUNT(*) FROM the.livestock_rotation
       WHERE forest_file_id = :target AND calendar_year = :year
      """;

  private static final String HAS_MEADOW_SQL =
      """
      SELECT COUNT(*) FROM the.meadow_rotation
       WHERE forest_file_id = :target AND calendar_year = :year
      """;

  /** A 29 February rotation day has no date in a target year that is not a leap year. */
  private static final String LEAP_DAY_SQL =
      """
      SELECT COUNT(*) FROM the.livestock_rotation
       WHERE forest_file_id = :src AND calendar_year = :srcYear
         AND range_unit_id LIKE NVL(:ru, '%')
         AND (TO_CHAR(begin_rotation_date, 'MM-DD') = '02-29'
              OR TO_CHAR(end_rotation_date, 'MM-DD') = '02-29')
      """;

  private static final String BUMP_FILE_SQL =
      """
      UPDATE the.prov_forest_use
         SET revision_count = revision_count + 1,
             update_userid  = :userId
       WHERE forest_file_id = :target
         AND revision_count = :rev
      """;

  private static final String DELETE_LIVESTOCK_SQL =
      "DELETE FROM the.livestock_rotation WHERE forest_file_id = :target AND calendar_year = :year";

  private static final String DELETE_MEADOW_SQL =
      "DELETE FROM the.meadow_rotation WHERE forest_file_id = :target AND calendar_year = :year";

  private static final String DELETE_PROVISION_SQL =
      "DELETE FROM the.range_provision WHERE forest_file_id = :target AND calendar_year = :year";

  /** {@code INSERT_RANGE_PROVISION}: the totals are worked out after the rotations. */
  private static final String COPY_PROVISION_SQL =
      """
      INSERT INTO the.range_provision (
        forest_file_id, calendar_year, total_authorized_grazbl_forage,
        total_private_land_graz_forage, nonuse_forage_tonnes, billable_non_use_ind,
        max_no_of_cattle, max_no_of_horses, max_no_of_sheep, max_no_of_other_livestock,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      )
      SELECT :target, :year, NULL,
             NULL, nonuse_forage_tonnes, billable_non_use_ind,
             max_no_of_cattle, max_no_of_horses, max_no_of_sheep, max_no_of_other_livestock,
             :userId, SYSDATE, :userId, SYSDATE, 1
        FROM the.range_provision
       WHERE forest_file_id = :src AND calendar_year = :srcYear
      """;

  /** {@code INSERT_ROTATION} for livestock: the same days, moved to the target year. */
  private static final String COPY_LIVESTOCK_SQL =
      """
      INSERT INTO the.livestock_rotation (
        livestock_rotation_skey, forest_file_id, calendar_year, livestock_code,
        begin_rotation_date, end_rotation_date, range_unit_id, pasture_id, rotation_line_no,
        rotation_area_desc, livestock_count, authorized_grazable_forage,
        private_land_grazable_forage, entry_timestamp, entry_userid, update_userid,
        update_timestamp, revision_count
      )
      SELECT the.livestock_rotation_seq.NEXTVAL, :target, :year, livestock_code,
             TO_DATE(TO_CHAR(:year) || TO_CHAR(begin_rotation_date, 'MMDD'), 'YYYYMMDD'),
             TO_DATE(TO_CHAR(:year) || TO_CHAR(end_rotation_date, 'MMDD'), 'YYYYMMDD'),
             range_unit_id, pasture_id, rotation_line_no,
             rotation_area_desc, livestock_count, authorized_grazable_forage,
             private_land_grazable_forage, SYSDATE, :userId, :userId,
             SYSDATE, 1
        FROM the.livestock_rotation
       WHERE forest_file_id = :src AND calendar_year = :srcYear
         AND range_unit_id LIKE NVL(:ru, '%')
      """;

  /** {@code INSERT_ROTATION} for meadows. */
  private static final String COPY_MEADOW_SQL =
      """
      INSERT INTO the.meadow_rotation (
        meadow_rotation_skey, forest_file_id, calendar_year, range_unit_id, permit_block_id,
        meadow_name, authorized_harvestable_forage, rotation_line_no, map_feature_id,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      )
      SELECT the.meadow_rotation_seq.NEXTVAL, :target, :year, range_unit_id, permit_block_id,
             meadow_name, authorized_harvestable_forage, rotation_line_no, map_feature_id,
             :userId, SYSDATE, :userId, SYSDATE, 1
        FROM the.meadow_rotation
       WHERE forest_file_id = :src AND calendar_year = :srcYear
      """;

  /** {@code UPDATE_RANGE_PROVISION_TOTALS}. */
  private static final String TOTALS_SQL =
      """
      UPDATE the.range_provision rp
         SET (total_authorized_grazbl_forage, total_private_land_graz_forage) = (
               SELECT SUM(lr.authorized_grazable_forage), SUM(lr.private_land_grazable_forage)
                 FROM the.livestock_rotation lr
                WHERE lr.forest_file_id = rp.forest_file_id
                  AND lr.calendar_year = rp.calendar_year)
       WHERE rp.forest_file_id = :target AND rp.calendar_year = :year
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RotationsTenureService tenures;

  public CopyRotationService(NamedParameterJdbcTemplate jdbc, RotationsTenureService tenures) {
    this.jdbc = jdbc;
    this.tenures = tenures;
  }

  /**
   * The tab.
   *
   * @throws ResponseStatusException 404 when the file does not exist
   */
  public CopyRotationDto get(String forestFileId) {
    RotationsTenure t = tenures.load(forestFileId);
    RotationsRules rules = RotationsRules.copy(t);
    RotationKind kind = rules.applies() ? RotationsRules.kind(t.fileTypeCode()) : null;
    return new CopyRotationDto(
        rules,
        kind == null ? null : kind.name(),
        t.termStartYear(),
        t.termEndYear(),
        t.pfuRevisionCount());
  }

  /**
   * The copy — {@code SAVE}.
   *
   * @throws ResponseStatusException 404 / 409 by the tab's rules; 400 with every failed check;
   *     409 when the file changed since the tab was read
   */
  @Transactional
  public CopyRotationResult copy(String forestFileId, CopyRotationRequest q, String userId) {
    RotationsTenure t = tenures.loadWritable(forestFileId, RotationsRules::copy);
    RotationKind kind = RotationsRules.kind(t.fileTypeCode());

    List<String> e = new ArrayList<>();
    CopyInput in = RotationsFieldChecks.copyInput(q, e);
    if (in == null) {
      throw badRequest(e);
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("target", forestFileId)
        .addValue("src", in.sourceForestFileId())
        .addValue("srcYear", in.sourceYear(), Types.INTEGER)
        .addValue("ru", in.sourceRangeUnitId())
        .addValue("rev", q.pfuRevisionCount(), Types.NUMERIC)
        .addValue("userId", userId);

    // ADD's checks, in its order.
    List<String> sourceType = jdbc.queryForList(SOURCE_SQL, p, String.class);
    if (sourceType.isEmpty()) {
      throw badRequest(List.of("The Source Tenure selected does not exist or is not a valid"
          + " Range Tenure. Please retry."));
    }
    String srcType = sourceType.get(0);
    if (srcType == null || srcType.isEmpty() || srcType.charAt(0) != t.fileTypeCode().charAt(0)) {
      e.add(kind == RotationKind.GRAZING
          ? "Source Tenure type must be a Grazing Licence/Permit."
          : "Source Tenure type must be a Hay Cutting Licence/Permit.");
    }
    if (count(SOURCE_PROVISION_SQL, p) <= 0) {
      e.add("Source year does not exist for given source file.");
    }
    if (in.sourceRangeUnitId() != null) {
      if (count(RANGE_UNIT_SQL, p) <= 0) {
        e.add("Invalid Range Unit.");
      } else if (count(SOURCE_RU_ROTATIONS_SQL, p) <= 0) {
        e.add("Rotations do not exist for given Range unit.");
      }
    }
    boolean sameFile = forestFileId.equalsIgnoreCase(in.sourceForestFileId());
    String sameYear = "Source file and year cannot be the same as target file and year.";
    if (in.everyOtherYearFrom() != null) {
      if (sameFile && in.sourceYear() == in.everyOtherYearFrom()) {
        e.add(sameYear);
      }
      if (!t.inTerm(in.everyOtherYearFrom())) {
        e.add(outOfTerm(in.everyOtherYearFrom(), t));
      }
    } else {
      for (int y : in.targetYears()) {
        if (!t.inTerm(y)) {
          e.add(outOfTerm(y, t));
        }
        if (sameFile && in.sourceYear() == y) {
          e.add(sameYear);
        }
      }
    }
    if (!e.isEmpty()) {
      throw badRequest(e);
    }

    List<Integer> years = RotationsFieldChecks.copyYears(in, t.termStartYear(), t.termEndYear());
    if (!q.overwrite()) {
      List<Integer> taken = new ArrayList<>();
      for (int y : years) {
        p.addValue("year", y, Types.INTEGER);
        if (count(kind == RotationKind.GRAZING ? HAS_LIVESTOCK_SQL : HAS_MEADOW_SQL, p) > 0) {
          taken.add(y);
        }
      }
      if (!taken.isEmpty()) {
        return new CopyRotationResult(false, RotationsFieldChecks.overwriteWarning(taken), taken);
      }
    }
    if (kind == RotationKind.GRAZING && count(LEAP_DAY_SQL, p) > 0) {
      List<Integer> notLeap = years.stream().filter(y -> !Year.isLeap(y)).toList();
      if (!notLeap.isEmpty()) {
        throw badRequest(List.of("A source rotation starts or ends on 02-29, which "
            + join(notLeap) + (notLeap.size() == 1 ? " does" : " do") + " not have."));
      }
    }

    if (q.pfuRevisionCount() == null || jdbc.update(BUMP_FILE_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    for (int y : years) {
      p.addValue("year", y, Types.INTEGER);
      jdbc.update(kind == RotationKind.GRAZING ? DELETE_LIVESTOCK_SQL : DELETE_MEADOW_SQL, p);
      jdbc.update(DELETE_PROVISION_SQL, p);
      jdbc.update(COPY_PROVISION_SQL, p);
      if (kind == RotationKind.GRAZING) {
        jdbc.update(COPY_LIVESTOCK_SQL, p);
        jdbc.update(TOTALS_SQL, p);
      } else {
        jdbc.update(COPY_MEADOW_SQL, p);
      }
    }
    return new CopyRotationResult(
        true,
        "Save successful. " + in.sourceForestFileId() + " " + in.sourceYear() + " copied to "
            + join(years) + ".",
        years);
  }

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  private static String outOfTerm(int year, RotationsTenure t) {
    return "The Target Year " + year + " is Invalid. It must fall within the tenure term year "
        + t.termStartYear() + " and " + t.termEndYear() + ".";
  }

  private static String join(List<Integer> years) {
    return years.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  private static ResponseStatusException badRequest(List<String> e) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
  }
}
