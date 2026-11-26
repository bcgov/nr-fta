package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayProvisionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationsDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Hay cutting rotation tab's read — legacy FTA612 (Hay Cutting Rotations),
 * {@code FTA_612_HAYCUT_ROTAT}'s {@code GET}: the tenure's authorized forage tonnes
 * ({@code RANGE_TENURE}), the year's provision (non-use, billable) and its
 * {@code MEADOW_ROTATION} rows. The year defaults as legacy's ({@code setDefaultYear}): this
 * year when it is in the term, otherwise the term's first year. The tenure page no longer
 * shows rotations, so nothing here writes.
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
}
