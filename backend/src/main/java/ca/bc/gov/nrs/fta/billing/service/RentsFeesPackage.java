package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeRowDto;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_670_RENTFEE.mainline}, which FTA670's Go and Prepare ran
 * ({@code Fta670RentfeeBean}):
 *
 * <ul>
 *   <li>{@code GET} — sets the billing period (next month) and opens a cursor of the
 *       {@code BILLABLE_TENURE} rows for the org unit (or, with rollup, its region or the
 *       province), with each file's units, rate and amount. A user whose org unit is not HQ is
 *       refused an org unit outside their region. A BCTS user gets only A25 files, everyone else
 *       everything but A25.
 *   <li>{@code SAVE} — for one row, deletes it when the new indicator is {@code D}, else sets
 *       {@code INVOICE_PREP_IND}; both only while {@code UPDATE_TIMESTAMP} still matches the
 *       one GET returned, else "record modified" (the optimistic lock).
 * </ul>
 *
 * <p>Every parameter is {@code IN OUT VARCHAR2}, bound by name. The package neither commits nor
 * rolls back, so SAVE runs in the caller's transaction, as legacy's BasicStore did.
 */
@Component
public class RentsFeesPackage {

  /** GET's billing period, rows and messages. */
  public record Get(String billFrom, String billTo, List<RentFeeRowDto> rows, String errorMessage) {}

  private static final String DECLARE =
      "DECLARE"
          + " v_action VARCHAR2(10) := ?;"
          + " v_file VARCHAR2(4000) := ?;"
          + " v_org VARCHAR2(4000) := ?;"
          + " v_user_org VARCHAR2(4000) := ?;"
          + " v_rollup VARCHAR2(4000) := ?;"
          + " v_year VARCHAR2(4000) := ?;"
          + " v_from VARCHAR2(4000);"
          + " v_to VARCHAR2(4000);"
          + " v_ind VARCHAR2(4000) := ?;"
          + " v_ind_new VARCHAR2(4000) := ?;"
          + " v_user VARCHAR2(4000) := ?;"
          + " v_rev VARCHAR2(4000);"
          + " v_ts VARCHAR2(4000) := ?;"
          + " v_err VARCHAR2(32767);"
          + " v_results the.fta_670_rentfee.ref_rentfee;"
          + " v_out SYS_REFCURSOR;"
          + " BEGIN"
          + " the.fta_670_rentfee.mainline("
          + "p_action => v_action, p_forest_file_id => v_file, p_org_unit => v_org,"
          + " p_user_org_unit => v_user_org, p_rollup => v_rollup, p_calender_year => v_year,"
          + " p_bill_from => v_from, p_bill_to => v_to, p_invoice_prep_ind => v_ind,"
          + " p_invoice_prep_ind_new => v_ind_new, p_update_userid => v_user,"
          + " p_revision_count => v_rev, p_update_timestamp => v_ts,"
          + " p_error_message => v_err, p_rentfee_results => v_results);";

  /** The number of inputs DECLARE binds, before the outputs. */
  private static final int INPUTS = 10;

  /**
   * GET opens its cursor only when there is no error; an empty one stands in otherwise, so the
   * driver never sees an unopened cursor.
   */
  static final String GET_CALL = DECLARE
      + " ? := v_from; ? := v_to; ? := v_err;"
      + " IF v_results%ISOPEN THEN v_out := v_results;"
      + " ELSE OPEN v_out FOR SELECT NULL FROM dual WHERE 1 = 0; END IF;"
      + " ? := v_out;"
      + " END;";

  static final String SAVE_CALL = DECLARE + " ? := v_err; END;";

  private final JdbcTemplate jdbc;

  // Its JdbcTemplate shares the transaction-bound connection, so SAVE joins the caller's
  // transaction.
  public RentsFeesPackage(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * GET.
   *
   * @param orgUnitNo     the org unit picked (ORG_UNIT_NO)
   * @param userOrgUnitNo the org unit the package checks authority and BCTS-ness against
   * @param rollup        Y or N
   */
  public Get get(String orgUnitNo, String userOrgUnitNo, String rollup) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(GET_CALL),
        (CallableStatement cs) -> {
          bind(cs, "GET", null, orgUnitNo, userOrgUnitNo, rollup, null, null, null, null, null);
          cs.registerOutParameter(INPUTS + 1, Types.VARCHAR);
          cs.registerOutParameter(INPUTS + 2, Types.VARCHAR);
          cs.registerOutParameter(INPUTS + 3, Types.VARCHAR);
          cs.registerOutParameter(INPUTS + 4, Types.REF_CURSOR);
          cs.execute();
          String error = cs.getString(INPUTS + 3);
          List<RentFeeRowDto> rows = new ArrayList<>();
          if (error == null || error.isBlank()) {
            try (ResultSet rs = cs.getObject(INPUTS + 4, ResultSet.class)) {
              while (rs != null && rs.next()) {
                rows.add(row(rs));
              }
            }
          }
          return new Get(cs.getString(INPUTS + 1), cs.getString(INPUTS + 2), rows,
              error == null ? "" : error);
        });
  }

  /**
   * SAVE for one row, as legacy's handlePrepare put it.
   *
   * @param updateTimestamp the row's {@code updateTimestamp} from GET
   * @return the package's messages, blank when it saved
   */
  public String save(String forestFileId, String calendarYear, String invoicePrepInd,
      String invoicePrepIndNew, String userId, String updateTimestamp) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(SAVE_CALL),
        (CallableStatement cs) -> {
          bind(cs, "SAVE", forestFileId, null, null, null, calendarYear, invoicePrepInd,
              invoicePrepIndNew, userId, updateTimestamp);
          cs.registerOutParameter(INPUTS + 1, Types.VARCHAR);
          cs.execute();
          String error = cs.getString(INPUTS + 1);
          return error == null ? "" : error;
        });
  }

  /** rec_RentFee, by name. */
  static RentFeeRowDto row(ResultSet rs) throws java.sql.SQLException {
    return new RentFeeRowDto(
        rs.getString("FOREST_FILE_ID"),
        rs.getString("CALENDAR_YEAR"),
        RentsFeesRules.normalize(rs.getString("INVOICE_PREP_IND")),
        rs.getString("FILE_TYPE_CODE"),
        rs.getString("ORG_CODE"),
        rs.getString("CLIENT_NUMBER"),
        rs.getString("CLIENT_LOCN_CODE"),
        rs.getString("CLIENT_NAME"),
        rs.getString("ANNV_DATE"),
        rs.getString("UNIT_BILL"),
        rs.getString("UNIT_TYPE"),
        rs.getString("UNIT_RATE"),
        rs.getString("UNIT_AMOUNT"),
        rs.getString("UPDATE_TIMESTAMP"));
  }

  private static void bind(CallableStatement cs, String... values) throws java.sql.SQLException {
    for (int i = 0; i < values.length; i++) {
      String v = values[i];
      cs.setString(i + 1, v == null || v.isEmpty() ? null : v);
    }
  }
}
