package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitPastureRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitSaveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitWriteResultDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.service.CodeListService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA630 Maintain Range Unit / Pasture's writes: Add New and Save Range, Delete Range, and a
 * pasture's Save (add or update) and Delete. Each runs legacy's {@code FTA_630_MN_RG_UN_PST}
 * ({@link RangeUnitPackage}) after the form's checks ({@link RangeUnitRules}).
 *
 * <p>Who may write: legacy's package checked {@code Sil_Check_User_Org_Authority(user org,
 * district)} on Save Range only, and the screen disabled the other buttons by the same check on
 * the unit's district ({@code p_ind}). Here every write requires
 * {@link LoggedUserHelper#mayEditRangeDistrict} on the unit's district — and on a Save Range that
 * moves the unit, on the new district as well — refused with legacy's districtNotInRegion
 * message. The package's own check is then satisfied by passing the district as the user's org
 * unit.
 *
 * <p>Runs in a transaction that is rolled back whenever the package reports an error, as legacy's
 * action did. Against the shared {@code THE} Oracle schema only — there is no local database.
 */
@Service
public class RangeUnitWriteService {

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  private static final String UNIT_SQL =
      """
      SELECT TO_CHAR(ru.admin_forest_district_no)  AS district,
             ou.org_unit_code                      AS district_code,
             ru.range_unit_status_code             AS status,
             TO_CHAR(ru.status_date, 'YYYY-MM-DD') AS as_of,
             ru.revision_count                     AS revision_count
        FROM the.range_unit ru
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = ru.admin_forest_district_no
       WHERE ru.range_unit_id = :rangeUnitId
      """;

  private static final String PASTURE_SQL =
      """
      SELECT revision_count
        FROM the.range_unit_pasture
       WHERE range_unit_id = :rangeUnitId
         AND pasture_id = :pastureId
      """;

  private static final String DISTRICT_CODE_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :districtNo";

  /** The range unit as stored: what the checks compare a save against. */
  record Unit(String district, String districtCode, String status, String asOf,
      Integer revisionCount) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final RangeUnitPackage pkg;
  private final LoggedUserHelper auth;
  private final CodeListService codeLists;
  private final Clock clock;

  @Autowired
  public RangeUnitWriteService(NamedParameterJdbcTemplate jdbc, RangeUnitPackage pkg,
      LoggedUserHelper auth, CodeListService codeLists) {
    this(jdbc, pkg, auth, codeLists, Clock.system(PACIFIC));
  }

  RangeUnitWriteService(NamedParameterJdbcTemplate jdbc, RangeUnitPackage pkg,
      LoggedUserHelper auth, CodeListService codeLists, Clock clock) {
    this.jdbc = jdbc;
    this.pkg = pkg;
    this.auth = auth;
    this.codeLists = codeLists;
    this.clock = clock;
  }

  // ── Range unit ────────────────────────────────────────────────────

  /**
   * Add New, then Save Range: the package generates the id ("RU" + the next number of
   * RANGE_UNIT_FILE_ID_CTL), saves the unit at status P and creates pasture '00' named after it.
   *
   * @throws ResponseStatusException 400 for a failed check or a package error; 403 when the user
   *     may not edit range files of the district
   */
  @Transactional
  public RangeUnitWriteResultDto add(RangeUnitSaveRequest request, String userId) {
    List<String> errors = new ArrayList<>(RangeUnitRules.validateSave(request, true));
    if (request != null) {
      addIfPresent(errors, RangeUnitRules.expiredCode(
          "Status", RangeUnitRules.NEW_STATUS, null, codes(codeLists.rangeUnitStatuses())));
      addIfPresent(errors, RangeUnitRules.expiredCode(
          "District", request.district(), null, codes(codeLists.districtOrgUnits())));
    }
    badRequestIf(errors);
    String district = request.district().trim();
    requireMayEdit(districtCode(district));

    RangeUnitRules.AsOf asOf = RangeUnitRules.asOf(
        RangeUnitRules.NEW_STATUS, null, request.asOf(), LocalDate.now(clock));
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE");
    in.put("p_user_org_unit", district);
    in.put("p_district", district);
    in.put("p_range_unit_name", request.rangeUnitName().trim());
    in.put("p_status", RangeUnitRules.NEW_STATUS);
    in.put("p_as_of", asOf.value());
    in.put("p_district_admin_zone", RangeUnitRules.zone(request.districtAdminZone()));
    in.put("p_update_userid", userId);
    Map<String, String> out = pkg.call(in);
    RangeUnitRules.Parsed result = check(out);
    return new RangeUnitWriteResultDto(out.get("p_range_unit_id"),
        warnings(asOf.warning(), result));
  }

  /**
   * Save Range on an existing unit: name (which renames pasture '00', refused when another pasture
   * has that name), status and As of, district and zone.
   *
   * @throws ResponseStatusException 400 for a failed check or a package error; 403 when the user
   *     may not edit range files of the unit's district or the new one; 404 when there is no such
   *     unit; 409 when it changed since it was read
   */
  @Transactional
  public RangeUnitWriteResultDto save(String rangeUnitId, RangeUnitSaveRequest request,
      String userId) {
    Unit unit = unit(rangeUnitId);
    List<String> errors = new ArrayList<>(RangeUnitRules.validateSave(request, false));
    if (request != null) {
      addIfPresent(errors, RangeUnitRules.expiredCode(
          "Status", request.status(), unit.status(), codes(codeLists.rangeUnitStatuses())));
      addIfPresent(errors, RangeUnitRules.expiredCode(
          "District", request.district(), unit.district(),
          codes(codeLists.districtOrgUnits())));
    }
    badRequestIf(errors);
    requireMayEdit(unit.districtCode());
    String district = request.district().trim();
    if (!district.equals(unit.district())) {
      requireMayEdit(districtCode(district));
    }
    requireRevision(request.revisionCount(), unit.revisionCount());

    String status = request.status().trim();
    RangeUnitRules.AsOf asOf =
        RangeUnitRules.asOf(status, unit.status(), request.asOf(), LocalDate.now(clock));
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE");
    in.put("p_user_org_unit", district);
    in.put("p_range_unit_id", rangeUnitId);
    in.put("p_district", district);
    in.put("p_range_unit_name", request.rangeUnitName().trim());
    in.put("p_status", status);
    in.put("p_prev_status", unit.status());
    in.put("p_as_of", asOf.value());
    in.put("p_prev_as_of", unit.asOf());
    in.put("p_district_admin_zone", RangeUnitRules.zone(request.districtAdminZone()));
    in.put("p_update_userid", userId);
    in.put("p_revision_count", String.valueOf(unit.revisionCount()));
    RangeUnitRules.Parsed result = check(pkg.call(in));
    return new RangeUnitWriteResultDto(rangeUnitId, warnings(asOf.warning(), result));
  }

  /**
   * Delete Range: refused by the package while a CIMS range risk site or inspection, a meadow
   * rotation or a livestock rotation refers to the unit; otherwise deletes its pastures and it.
   *
   * @throws ResponseStatusException 403 when the user may not edit range files of the unit's
   *     district; 404 when there is no such unit; 409 when it changed since it was read or a
   *     reference stops the delete; 400 for another package error
   */
  @Transactional
  public void delete(String rangeUnitId, Integer revisionCount) {
    Unit unit = unit(rangeUnitId);
    requireMayEdit(unit.districtCode());
    requireRevision(revisionCount, unit.revisionCount());
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "REMOVE");
    in.put("p_range_unit_id", rangeUnitId);
    in.put("p_revision_count", String.valueOf(unit.revisionCount()));
    check(pkg.call(in));
  }

  // ── Pastures ──────────────────────────────────────────────────────

  /**
   * A pasture's Save with no pasture selected: adds it, refused when the unit already has a
   * pasture of that id or name.
   *
   * @throws ResponseStatusException 400 for a failed check, a duplicate or another package error;
   *     403 when the user may not edit range files of the unit's district; 404 when there is no
   *     such unit
   */
  @Transactional
  public RangeUnitWriteResultDto addPasture(String rangeUnitId, RangeUnitPastureRequest request,
      String userId) {
    Unit unit = unit(rangeUnitId);
    requireMayEdit(unit.districtCode());
    badRequestIf(request == null
        ? List.of(RangeUnitRules.PASTURE_ID_REQUIRED, RangeUnitRules.PASTURE_NAME_REQUIRED)
        : RangeUnitRules.validatePasture(request.pastureId(), request.pastureName(), true));
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE_ITEM");
    in.put("p_range_unit_id", rangeUnitId);
    in.put("p_pasture_id", request.pastureId().trim());
    in.put("p_pasture_name", request.pastureName().trim());
    in.put("p_update_userid", userId);
    in.put("p_revision_count", String.valueOf(unit.revisionCount()));
    check(pkg.call(in));
    return new RangeUnitWriteResultDto(rangeUnitId, List.of());
  }

  /**
   * A pasture's Save after Update: its name. Updating pasture '00' renames the unit too.
   *
   * @throws ResponseStatusException 400 for a failed check or a package error; 403 when the user
   *     may not edit range files of the unit's district; 404 when there is no such unit or
   *     pasture; 409 when either changed since it was read
   */
  @Transactional
  public RangeUnitWriteResultDto updatePasture(String rangeUnitId, String pastureId,
      RangeUnitPastureRequest request, String userId) {
    Unit unit = unit(rangeUnitId);
    requireMayEdit(unit.districtCode());
    List<String> errors = new ArrayList<>(request == null
        ? List.of(RangeUnitRules.PASTURE_NAME_REQUIRED)
        : RangeUnitRules.validatePasture(pastureId, request.pastureName(), false));
    if (request != null && request.pastureRevisionCount() == null) {
      errors.add(RangeUnitRules.PASTURE_REVISION_REQUIRED);
    }
    badRequestIf(errors);
    Integer pastureRevision = pastureRevision(rangeUnitId, pastureId);
    requireRevision(request.pastureRevisionCount(), pastureRevision);
    if (RangeUnitRules.FIRST_PASTURE.equals(pastureId)) {
      requireRevision(request.revisionCount(), unit.revisionCount());
    }
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE_ITEM");
    in.put("p_range_unit_id", rangeUnitId);
    in.put("p_pasture_id", pastureId);
    in.put("p_pasture_name", request.pastureName().trim());
    in.put("p_update_userid", userId);
    in.put("p_pasture_revision_count", String.valueOf(pastureRevision));
    in.put("p_revision_count", String.valueOf(unit.revisionCount()));
    check(pkg.call(in));
    return new RangeUnitWriteResultDto(rangeUnitId, List.of());
  }

  /**
   * A pasture's Delete: refused for pasture '00', and by the package while a CIMS range risk site
   * or inspection or a livestock rotation refers to it.
   *
   * @throws ResponseStatusException 403 when the user may not edit range files of the unit's
   *     district; 404 when there is no such unit or pasture; 409 when it changed since it was read
   *     or something stops the delete; 400 for another package error
   */
  @Transactional
  public void deletePasture(String rangeUnitId, String pastureId, Integer pastureRevisionCount) {
    Unit unit = unit(rangeUnitId);
    requireMayEdit(unit.districtCode());
    Integer pastureRevision = pastureRevision(rangeUnitId, pastureId);
    String block = RangeUnitRules.pastureDeleteBlock(pastureId);
    if (block != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, block);
    }
    requireRevision(pastureRevisionCount, pastureRevision);
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "REMOVE_ITEM");
    in.put("p_range_unit_id", rangeUnitId);
    in.put("p_pasture_id", pastureId);
    in.put("p_revision_count", String.valueOf(unit.revisionCount()));
    in.put("p_pasture_revision_count", String.valueOf(pastureRevision));
    check(pkg.call(in));
  }

  // ── Helpers ───────────────────────────────────────────────────────

  private Unit unit(String rangeUnitId) {
    List<Unit> rows = jdbc.query(UNIT_SQL, new MapSqlParameterSource("rangeUnitId", rangeUnitId),
        (rs, n) -> new Unit(rs.getString("district"), rs.getString("district_code"),
            rs.getString("status"), rs.getString("as_of"),
            rs.getObject("revision_count", Integer.class)));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "Range unit " + rangeUnitId + " was not found.");
    }
    return rows.get(0);
  }

  private Integer pastureRevision(String rangeUnitId, String pastureId) {
    List<Integer> rows = jdbc.queryForList(PASTURE_SQL, new MapSqlParameterSource()
        .addValue("rangeUnitId", rangeUnitId)
        .addValue("pastureId", pastureId), Integer.class);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "Pasture " + pastureId + " of range unit " + rangeUnitId + " was not found.");
    }
    return rows.get(0);
  }

  /** The district's ORG_UNIT_CODE. */
  private String districtCode(String districtNo) {
    List<String> rows = jdbc.queryForList(DISTRICT_CODE_SQL,
        new MapSqlParameterSource("districtNo", Long.parseLong(districtNo)), String.class);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RangeUnitRules.DISTRICT_INVALID);
    }
    return rows.get(0);
  }

  private void requireMayEdit(String districtCode) {
    if (districtCode == null || !auth.mayEditRangeDistrict(districtCode)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          RangeUnitRules.DISTRICT_NOT_IN_REGION);
    }
  }

  private static void requireRevision(Integer sent, Integer stored) {
    if (!Objects.equals(sent, stored)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, RangeUnitRules.MODIFIED);
    }
  }

  private static void badRequestIf(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
  }

  private static void addIfPresent(List<String> errors, String error) {
    if (error != null) {
      errors.add(error);
    }
  }

  private static Set<String> codes(List<CodeOptionDto> options) {
    return options.stream().map(CodeOptionDto::code).collect(Collectors.toSet());
  }

  /**
   * The package's verdict. An error throws, which rolls back whatever the package wrote first: 403
   * for its authority check, 409 for a revision mismatch or a reference that stops a delete, 400
   * for anything else.
   */
  private static RangeUnitRules.Parsed check(Map<String, String> out) {
    RangeUnitRules.Parsed result = RangeUnitRules.parse(out.get("p_error_message"));
    if (!result.failed()) {
      return result;
    }
    List<String> blocks = result.deleteBlocks();
    if (!blocks.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, String.join(" ", blocks));
    }
    HttpStatus status = result.notAuthorized() ? HttpStatus.FORBIDDEN
        : result.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    throw new ResponseStatusException(status, String.join(" ", result.errors()));
  }

  private static List<String> warnings(String own, RangeUnitRules.Parsed result) {
    List<String> all = new ArrayList<>();
    if (own != null) {
      all.add(own);
    }
    all.addAll(result.warnings());
    return all;
  }
}
