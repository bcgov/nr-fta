package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeChange;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeListDto;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareRequest;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareResult;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeRowDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA670 Annual Rents and Fees Preparation, on legacy {@code THE.FTA_670_RENTFEE}
 * ({@link RentsFeesPackage}).
 *
 * <p>{@link #search} is Go: the package's GET for the org unit (and rollup). {@link #prepare} is
 * Prepare: SAVE for every row whose Opt changed, in one transaction; if any row is refused,
 * nothing is kept and every refusal is reported, as legacy's handlePrepare rolled back.
 *
 * <p><b>Who sees what.</b> Legacy decided by the user's org unit: HQ could pick any org unit and
 * roll up the province; anyone else only their own region; a district user's org unit was fixed
 * and had no rollup. Here an unscoped {@code FTA_ADMIN} is the HQ user (any org unit, rollup) and
 * a district-scoped one ({@code FTA_ADMIN_DISTRICT-xxx}) the district user of each district
 * granted (no rollup) — checked by {@link BillingDistrictScope#adminOnly} before the package
 * runs. The package's own region check is then given an org unit that passes it: an HQ org unit
 * for the unscoped administrator, the district itself for a scoped one. Neither is a BCTS org
 * unit, so the package never takes its BCTS (A25-only) branches: no role here is a BCTS user.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RentsFeesService {

  static final String ORG_UNIT_CODE_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :orgUnitNo";

  /**
   * An HQ org unit that is not BCTS Headquarters (ORG_UNIT_CODE 'T'): the package treats a user
   * at any level-H org unit as HQ, and one at 'T' as BCTS Headquarters.
   */
  static final String HQ_ORG_UNIT_SQL =
      """
      SELECT TO_CHAR(MIN(org_unit_no))
        FROM the.org_unit
       WHERE org_level_code = 'H'
         AND org_unit_code <> 'T'
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RentsFeesPackage pkg;
  private final LoggedUserHelper auth;
  private final BillingDistrictScope scope;

  public RentsFeesService(
      NamedParameterJdbcTemplate jdbc, RentsFeesPackage pkg, LoggedUserHelper auth) {
    this.jdbc = jdbc;
    this.pkg = pkg;
    this.auth = auth;
    this.scope = BillingDistrictScope.adminOnly(auth);
  }

  /** The Go button. */
  public RentFeeListDto search(Long orgUnitNo, Boolean rollup) {
    RentsFeesPackage.Get g = get(orgUnitNo, rollup);
    return new RentFeeListDto(
        RentsFeesRules.billFrom(g.billFrom(),
            g.rows().stream().map(RentFeeRowDto::anniversaryDate).toList()),
        g.billTo(),
        g.rows());
  }

  /**
   * The Prepare button.
   *
   * @throws ResponseStatusException 400 with each row's error, or 409 when a row was changed by
   *     someone else since Go (the optimistic lock)
   */
  @Transactional
  public RentFeePrepareResult prepare(RentFeePrepareRequest req, String userId) {
    if (req == null || req.orgUnitNo() == null) {
      throw bad(RentsFeesRules.ORG_UNIT_REQUIRED);
    }
    if (req.rows() == null) {
      throw bad(RentsFeesRules.ROWS_REQUIRED);
    }
    // Legacy saved only rows its own GET had listed (held in the session). Re-run GET, which
    // also re-checks the org unit, and refuse any row it no longer lists.
    Set<String> listed = new HashSet<>();
    get(req.orgUnitNo(), req.rollup()).rows()
        .forEach(r -> listed.add(key(r.forestFileId(), r.calendarYear())));

    List<String> errors = new ArrayList<>();
    int saved = 0;
    for (int i = 0; i < req.rows().size(); i++) {
      int rowNo = i + 1;
      RentFeeChange c = req.rows().get(i);
      String invalid = RentsFeesRules.invalidIndicator(rowNo, c.invoicePrepIndNew());
      if (invalid != null) {
        errors.add(invalid);
        continue;
      }
      if (!RentsFeesRules.changed(c.invoicePrepInd(), c.invoicePrepIndNew())) {
        continue;
      }
      if (!listed.contains(key(c.forestFileId(), c.calendarYear()))) {
        errors.add(RentsFeesRules.modified(rowNo));
        continue;
      }
      List<String> rowErrors = RentsFeesRules.saveMessages(pkg.save(
          c.forestFileId(), c.calendarYear(), RentsFeesRules.normalize(c.invoicePrepInd()),
          RentsFeesRules.normalize(c.invoicePrepIndNew()), userId, c.updateTimestamp()), rowNo);
      if (rowErrors.isEmpty()) {
        saved++;
      } else {
        errors.addAll(rowErrors);
      }
    }
    if (!errors.isEmpty()) {
      // Thrown, so the transaction rolls back every row already saved.
      HttpStatus status = errors.stream().anyMatch(RentsFeesRules::isModified)
          ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, String.join("\n", errors));
    }
    return new RentFeePrepareResult(saved,
        saved > 0 ? RentsFeesRules.PREPARE_SUCCESS : RentsFeesRules.NO_CHANGES);
  }

  private RentsFeesPackage.Get get(Long orgUnitNo, Boolean rollup) {
    if (orgUnitNo == null) {
      throw bad(RentsFeesRules.ORG_UNIT_REQUIRED);
    }
    String orgUnitCode = jdbc.queryForList(ORG_UNIT_CODE_SQL, Map.of("orgUnitNo", orgUnitNo),
        String.class).stream().findFirst().orElse(null);
    scope.requireDistrict(orgUnitCode);
    boolean unscoped = auth.isUnscopedAdmin();
    String userOrgUnitNo = unscoped
        ? jdbc.queryForObject(HQ_ORG_UNIT_SQL, Map.of(), String.class)
        : String.valueOf(orgUnitNo);
    RentsFeesPackage.Get g = pkg.get(String.valueOf(orgUnitNo), userOrgUnitNo,
        RentsFeesRules.rollup(unscoped, rollup));
    List<String> errors = RentsFeesRules.getMessages(g.errorMessage());
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(
          RentsFeesRules.isAuthorityError(g.errorMessage())
              ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST,
          String.join(" ", errors));
    }
    return g;
  }

  private static String key(String forestFileId, String calendarYear) {
    return forestFileId + "|" + calendarYear;
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
