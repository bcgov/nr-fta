package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingProvisionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationsDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Grazing rotation tab's read — legacy FTA611 (Grazing Rotations),
 * {@code FTA_611_GRAZE_ROTATN}'s {@code GET}: the year's {@code RANGE_PROVISION} (non-use,
 * billable, totals, the maximum head of each livestock) and its {@code LIVESTOCK_ROTATION}
 * rows. The year defaults as legacy's: this year when it has rotations, otherwise the term's
 * first year. The tenure page no longer shows rotations, so nothing here writes.
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

  private Integer defaultYear(String forestFileId, RotationsTenure t) {
    int thisYear = LocalDate.now().getYear();
    Long n = jdbc.queryForObject(
        DEFAULT_YEAR_SQL, Map.of("forestFileId", forestFileId, "year", thisYear), Long.class);
    return n != null && n > 0 ? Integer.valueOf(thisYear) : t.termStartYear();
  }

  private static int nz(Integer n) {
    return n == null ? 0 : n;
  }
}
