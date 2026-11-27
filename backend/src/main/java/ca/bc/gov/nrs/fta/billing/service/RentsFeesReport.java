package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeListDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA670's Print: the FTA406 Rents and Fees Preparation Report, as a PDF.
 *
 * <p>Legacy's Print ({@code Fta670RentfeeAction.handlePrint}) ran the Jasper Server report
 * {@code FTA406R-RentsAndFees}, whose data source is {@code THE.FTAR406P}: the
 * {@code BILLABLE_TENURE} rows for the org unit (with rollup, every org unit rolling up to a
 * region, or the province for HQ), each with its units, rate and amount from the
 * {@code FTA_GET_BILLABLE_*} functions. The procedure is called here as it was there, and its
 * cursor's rows fill the same layout — it is the report's whole query, and the functions and
 * org-level branching it relies on stay in the database.
 *
 * <p>The parameters are legacy's {@code getReportParameters}: the user (as Prepared By and the
 * report user), the billing period the screen showed after Go, the org unit's code, name and
 * number, and the rollup indicator. Go is re-run first, so the org unit is checked and scoped
 * as the screen's GET does it ({@link RentsFeesService#search}) and the billing period is the
 * one Go showed.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Component
public class RentsFeesReport {

  static final String TEMPLATE = "reports/FTA406_RentsAndFees.jrxml";
  public static final String FILENAME = "FTA406-rents-and-fees.pdf";

  /** The IN OUT cursor, then FTAR406P's eight inputs. */
  static final String CALL = "{call the.ftar406p(?, ?, ?, ?, ?, ?, ?, ?, ?)}";

  /** FTAR406P's inputs, in order: the report's parameters of the same names. */
  static final List<String> INPUTS = List.of(
      "p_prepared_by", "p_billing_period_from", "p_billing_period_to", "p_org_unit_name",
      "p_org_unit_code", "p_org_unit_no", "p_rollup_ind", "p_userid");

  static final String ORG_UNIT_SQL =
      "SELECT org_unit_code, org_unit_name FROM the.org_unit WHERE org_unit_no = :orgUnitNo";

  /** The cursor's columns (Fta_406_Pkg.rec_FTA406): the report's fields. */
  static final List<String> FIELDS = List.of(
      "forest_file_id", "file_type_code", "file_status_st", "legal_effective_dt",
      "initial_expiry_dt", "current_expiry_dt", "invoice_prep_ind", "calendar_year",
      "anniversary_date", "org_unit_code", "billable_units", "billable_rates", "billing_amounts",
      "serverid", "client_number", "client_locn_code", "client_name");

  /** The fields the report declares as java.lang.Number; the rest are java.lang.String. */
  static final List<String> NUMBERS =
      List.of("calendar_year", "billable_units", "billable_rates", "billing_amounts");

  private final NamedParameterJdbcTemplate jdbc;
  private final RentsFeesService rentsFees;
  private final LoggedUserHelper auth;

  public RentsFeesReport(
      NamedParameterJdbcTemplate jdbc, RentsFeesService rentsFees, LoggedUserHelper auth) {
    this.jdbc = jdbc;
    this.rentsFees = rentsFees;
    this.auth = auth;
  }

  /**
   * The report for the org unit and rollup of the last Go.
   *
   * @param userId the requesting user's audit id (legacy's user name)
   * @throws org.springframework.web.server.ResponseStatusException as {@link
   *     RentsFeesService#search}: 400 with no org unit, 403 for one the user may not use
   */
  public byte[] render(Long orgUnitNo, Boolean rollup, String userId) {
    RentFeeListDto go = rentsFees.search(orgUnitNo, rollup);
    OrgUnit org = orgUnit(orgUnitNo);
    Map<String, Object> params = parameters(userId, go.billFrom(), go.billTo(),
        org.name(), org.code(), String.valueOf(orgUnitNo),
        RentsFeesRules.rollup(auth.isUnscopedAdmin(), rollup));
    return JasperTemplates.pdf(TEMPLATE, params, rows(params));
  }

  /** FTAR406P's inputs by name, which are also the report's parameters. */
  static Map<String, Object> parameters(String userId, String billFrom, String billTo,
      String orgUnitName, String orgUnitCode, String orgUnitNo, String rollupInd) {
    Map<String, Object> p = new HashMap<>();
    p.put("p_prepared_by", userId);
    p.put("p_billing_period_from", billFrom);
    p.put("p_billing_period_to", billTo);
    p.put("p_org_unit_name", orgUnitName);
    p.put("p_org_unit_code", orgUnitCode);
    p.put("p_org_unit_no", orgUnitNo);
    p.put("p_rollup_ind", rollupInd);
    p.put("p_userid", userId);
    return p;
  }

  /** Registers the cursor and binds the inputs, in FTAR406P's order. */
  static void bind(CallableStatement cs, Map<String, Object> params) throws SQLException {
    cs.registerOutParameter(1, Types.REF_CURSOR);
    for (int i = 0; i < INPUTS.size(); i++) {
      Object v = params.get(INPUTS.get(i));
      cs.setString(i + 2, v == null ? null : v.toString());
    }
  }

  /** One cursor row, keyed by the report's field names. */
  static Map<String, ?> row(ResultSet rs) throws SQLException {
    Map<String, Object> r = new HashMap<>();
    for (String f : FIELDS) {
      r.put(f, NUMBERS.contains(f) ? rs.getBigDecimal(f) : rs.getString(f));
    }
    return r;
  }

  private List<Map<String, ?>> rows(Map<String, Object> params) {
    return jdbc.getJdbcTemplate().execute(
        (CallableStatementCreator) con -> con.prepareCall(CALL),
        (CallableStatementCallback<List<Map<String, ?>>>) cs -> {
          bind(cs, params);
          cs.execute();
          List<Map<String, ?>> out = new ArrayList<>();
          try (ResultSet rs = cs.getObject(1, ResultSet.class)) {
            while (rs != null && rs.next()) {
              out.add(row(rs));
            }
          }
          return out;
        });
  }

  /**
   * The org unit's code and name. Legacy split them from the Org Unit list's "CODE - Name",
   * and used UNK / Unknown for an org unit not in it.
   */
  private OrgUnit orgUnit(Long orgUnitNo) {
    List<OrgUnit> found = jdbc.query(ORG_UNIT_SQL, Map.of("orgUnitNo", orgUnitNo),
        (rs, i) -> new OrgUnit(rs.getString("org_unit_code"), rs.getString("org_unit_name")));
    return found.isEmpty() ? new OrgUnit("UNK", "Unknown") : found.get(0);
  }

  record OrgUnit(String code, String name) {}
}
