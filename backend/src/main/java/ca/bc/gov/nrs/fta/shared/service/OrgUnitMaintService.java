package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.dto.OrgUnitMaintDto;
import ca.bc.gov.nrs.fta.shared.dto.UserOrgUnitDefaultDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Org Unit Maintenance (legacy SIL99, Admin &gt; Org Unit Maintenance): the signed-in user picks
 * which of their org units is their default, and the screens that legacy defaulted to it read it
 * back.
 *
 * <p>Reads and writes go through the legacy {@code THE.SIL_99_USER_ORG_DEFAULT} package
 * ({@link UserOrgDefaultPackage}), keyed by application {@code FTA} and the audit user id, so a
 * default a user set in legacy FTA carries over. The rules are {@link OrgUnitMaintRules}.
 *
 * <p>Legacy also let an administrator type another user's id and set theirs. That needed the
 * other user's WebADE org units; nothing here knows another user's grants, so a user maintains
 * only their own default.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class OrgUnitMaintService {

  private static final String ORG_UNIT_SQL =
      """
      SELECT org_unit_code, org_level_code, TO_CHAR(rollup_region_no) AS rollup_region_no
        FROM the.org_unit
       WHERE org_unit_no = TO_NUMBER(:orgUnitNo)
      """;

  private final UserOrgDefaultPackage pkg;
  private final CodeListService codeLists;
  private final LoggedUserHelper auth;
  private final NamedParameterJdbcTemplate jdbc;

  public OrgUnitMaintService(
      UserOrgDefaultPackage pkg,
      CodeListService codeLists,
      LoggedUserHelper auth,
      NamedParameterJdbcTemplate jdbc) {
    this.pkg = pkg;
    this.codeLists = codeLists;
    this.auth = auth;
    this.jdbc = jdbc;
  }

  /** The screen: the user's org units and which is checked. */
  @Transactional(readOnly = true)
  public OrgUnitMaintDto view(String userId) {
    List<CodeOptionDto> orgUnits = userOrgUnits();
    if (orgUnits.isEmpty()) {
      return new OrgUnitMaintDto(userId, orgUnits, null, OrgUnitMaintRules.NO_ORGS_FOR_USER);
    }
    return new OrgUnitMaintDto(
        userId, orgUnits, OrgUnitMaintRules.checked(orgUnits, saved(userId)), null);
  }

  /**
   * Save: make {@code orgUnitNo} the user's default ({@code SAVE}).
   *
   * @throws ResponseStatusException 400 when the org unit is missing or not one of the user's,
   *     or the package refuses it
   */
  @Transactional
  public OrgUnitMaintDto save(String orgUnitNo, String userId) {
    List<CodeOptionDto> orgUnits = userOrgUnits();
    List<String> errors = OrgUnitMaintRules.validate(orgUnits, orgUnitNo);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    List<String> refused =
        OrgUnitMaintRules.packageErrors(pkg.save(orgUnitNo.trim(), userId).errorMessage());
    if (!refused.isEmpty()) {
      // Throwing rolls back, as legacy's store.rollback().
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", refused));
    }
    return new OrgUnitMaintDto(userId, orgUnits, orgUnitNo.trim(), null);
  }

  /**
   * The user's default org unit as the other screens use it: the saved one, else the first of
   * the user's org units, else none.
   */
  @Transactional(readOnly = true)
  public UserOrgUnitDefaultDto effectiveDefault(String userId) {
    List<CodeOptionDto> orgUnits = auth.isAdmin() ? userOrgUnits() : List.of();
    String orgUnitNo = OrgUnitMaintRules.effective(orgUnits, saved(userId));
    if (orgUnitNo == null || !orgUnitNo.matches("\\d{1,10}")) {
      return UserOrgUnitDefaultDto.NONE;
    }
    List<UserOrgUnitDefaultDto> rows = jdbc.query(ORG_UNIT_SQL,
        new MapSqlParameterSource("orgUnitNo", orgUnitNo),
        (rs, n) -> new UserOrgUnitDefaultDto(orgUnitNo, rs.getString("org_unit_code"),
            rs.getString("org_level_code"), rs.getString("rollup_region_no")));
    return rows.isEmpty() ? UserOrgUnitDefaultDto.NONE : rows.get(0);
  }

  /** The saved default's ORG_UNIT_NO, or null when there is none (GET's NO_DATA_FOUND). */
  private String saved(String userId) {
    UserOrgDefaultPackage.Result r = pkg.get(userId);
    if (r.errorMessage() != null && !r.errorMessage().isBlank()) {
      return null;
    }
    return r.orgUnitNo();
  }

  private List<CodeOptionDto> userOrgUnits() {
    return OrgUnitMaintRules.userOrgUnits(
        codeLists.districts(), auth.isUnscopedAdmin(), auth.adminDistricts());
  }
}
