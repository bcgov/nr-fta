package ca.bc.gov.nrs.fta.batch.rangebilling;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_690_TENURE_APPROVAL.MAINLINE} as legacy batch
 * {@code fta_batch_690_tenure_approval} did ({@code DBProcTask.processFtaBatch690TenureApproval},
 * {@code Fta690TenureApproval}): ten {@code IN OUT} parameters — action, calendar year, org unit
 * no, user id, submit date, submit time, request id, summary report name ({@code OUT}), error
 * message, queue cursor — of which the batch sets only the action and, for PROCESS, the request
 * id.
 *
 * <ul>
 *   <li>{@code GET} — the queue: every {@code FTA_RANGE_BILL_REQUEST} row with
 *       {@code processed_ind} N (or null). Read on an auto-commit connection (legacy's
 *       {@code BeanList}).
 *   <li>{@code PROCESS} — one request: creates the invoice transactions (audit id: the
 *       submitter's user name without its {@code IDIR\}), and logs start, end, totals and outcome
 *       on the request row through {@code FTA690_UPDATE_RBS_REQUEST}, an autonomous transaction —
 *       so the row is marked processed (successful_ind Y or N, message_text) even when the rest
 *       is rolled back. Returns the summary report name ({@code fta411-ApprovalSummary.rpt}) and
 *       the error message. Legacy ran it with auto-commit off ({@code BasicStore.put}) and
 *       committed when the error message was blank, else rolled back; so does {@link #process}.
 * </ul>
 */
@Component
public class RangeBillingPackage {

  /** The package, as legacy's error e-mails name it. */
  public static final String PACKAGE = "FTA_690_TENURE_APPROVAL";

  /** One queued request ({@code queue_rec_t}, the columns the batch uses). */
  public record QueuedRequest(
      long rangeBillRequestId, Long orgUnitNo, String submitUserid, Integer calendarYear) {}

  /** GET's outcome: its error message (blank when it succeeded) and the queue. */
  public record GetResult(String errorMessage, List<QueuedRequest> requests) {}

  /** PROCESS's outcome: the summary report name and the error message (blank on success). */
  public record ProcessResult(String summaryReport, String errorMessage) {}

  private static final String DECLARE =
      "DECLARE"
          + " v_action VARCHAR2(30) := ?;"
          + " v_year VARCHAR2(30);"
          + " v_org VARCHAR2(30);"
          + " v_user VARCHAR2(100);"
          + " v_sdate VARCHAR2(30);"
          + " v_stime VARCHAR2(30);"
          + " v_id VARCHAR2(30) := ?;"
          + " v_summ VARCHAR2(100);"
          + " v_err VARCHAR2(32767);"
          + " v_queue the.fta_690_tenure_approval.queue_cur_t;";

  private static final String MAINLINE =
      " the.fta_690_tenure_approval.mainline(v_action, v_year, v_org, v_user, v_sdate, v_stime,"
          + " v_id, v_summ, v_err, v_queue);";

  static final String GET_CALL =
      DECLARE
          + " v_out SYS_REFCURSOR;"
          + " BEGIN"
          + MAINLINE
          + " ? := v_err;"
          + " IF v_queue%ISOPEN THEN v_out := v_queue;"
          + " ELSE OPEN v_out FOR SELECT NULL FROM dual WHERE 1 = 0; END IF;"
          + " ? := v_out;"
          + " END;";

  static final String PROCESS_CALL =
      DECLARE
          + " BEGIN"
          + MAINLINE
          + " ? := v_summ;"
          + " ? := v_err;"
          + " END;";

  /**
   * FTA_411_PKG.rec_FTA411 for one request, from the row PROCESS just updated: the FTA411 report's
   * data.
   */
  static final String SUMMARY_SQL =
      """
      SELECT ou.org_unit_code,
             r.submit_userid,
             r.calendar_year,
             TO_CHAR(SYSDATE, 'HH24:MI:SS') AS curr_time,
             SYSDATE AS curr_date,
             r.total_invoices_loaded AS total_invoices,
             r.first_invoice_number AS first_invoice,
             r.last_invoice_number AS last_invoice,
             r.total_invoice_value AS invoice_total,
             r.message_text,
             SYS_CONTEXT('USERENV', 'DB_NAME') AS current_server
        FROM the.fta_range_bill_request r
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = r.org_unit_no
       WHERE r.range_bill_request_id = ?
      """;

  private final JdbcTemplate jdbc;

  public RangeBillingPackage(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** {@code GET}: the unprocessed requests. */
  public GetResult get() {
    return jdbc.execute(
        (Connection con) -> con.prepareCall(GET_CALL),
        (CallableStatement cs) -> {
          cs.setString(1, "GET");
          cs.setNull(2, Types.VARCHAR);
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.registerOutParameter(4, Types.REF_CURSOR);
          cs.execute();
          String error = trim(cs.getString(3));
          List<QueuedRequest> rows = new ArrayList<>();
          if (error.isEmpty()) {
            try (ResultSet rs = cs.getObject(4, ResultSet.class)) {
              while (rs != null && rs.next()) {
                rows.add(new QueuedRequest(
                    rs.getLong("RANGE_BILL_REQUEST_ID"),
                    nullableLong(rs, "ORG_UNIT_NO"),
                    rs.getString("SUBMIT_USERID"),
                    nullableInt(rs, "CALENDAR_YEAR")));
              }
            }
          }
          return new GetResult(error, rows);
        });
  }

  /**
   * {@code PROCESS} for one request, in its own transaction: committed when the package reports
   * no error, rolled back otherwise.
   */
  public ProcessResult process(long rangeBillRequestId) {
    return jdbc.execute((ConnectionCallback<ProcessResult>) con -> {
      boolean autoCommit = con.getAutoCommit();
      con.setAutoCommit(false);
      try {
        ProcessResult result;
        try (CallableStatement cs = con.prepareCall(PROCESS_CALL)) {
          cs.setString(1, "PROCESS");
          cs.setString(2, Long.toString(rangeBillRequestId));
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.registerOutParameter(4, Types.VARCHAR);
          cs.execute();
          result = new ProcessResult(trim(cs.getString(3)), trim(cs.getString(4)));
        } catch (SQLException | RuntimeException e) {
          con.rollback();
          throw e;
        }
        if (result.errorMessage().isEmpty()) {
          con.commit();
        } else {
          con.rollback();
        }
        return result;
      } finally {
        con.setAutoCommit(autoCommit);
      }
    });
  }

  /** The FTA411 report's row for the request, if the request exists. */
  public Optional<Map<String, Object>> summary(long rangeBillRequestId) {
    List<Map<String, Object>> rows = jdbc.queryForList(SUMMARY_SQL, rangeBillRequestId);
    return rows.stream().findFirst().map(RangeBillingPackage::lowerKeys);
  }

  /** Jasper fields are lower case; Oracle's column labels are upper case. */
  private static Map<String, Object> lowerKeys(Map<String, Object> row) {
    Map<String, Object> out = new HashMap<>();
    row.forEach((k, v) -> out.put(k.toLowerCase(Locale.ROOT), v));
    return out;
  }

  private static Long nullableLong(ResultSet rs, String col) throws SQLException {
    long v = rs.getLong(col);
    return rs.wasNull() ? null : v;
  }

  private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
