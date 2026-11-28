package ca.bc.gov.nrs.fta.billing.service;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_690_TENURE_APPROVAL.MAINLINE} with action {@code PROCESS}, as
 * legacy batch {@code fta_batch_690_tenure_approval} did ({@code
 * DBProcTask.processFtaBatch690TenureApproval}, {@code Fta690TenureApproval}): ten {@code IN OUT}
 * parameters — action, calendar year, org unit no, user id, submit date, submit time, request id,
 * summary report name ({@code OUT}), error message, queue cursor — of which only the action and
 * the request id are set.
 *
 * <p>{@code PROCESS} bills one request: it creates the invoice transactions (audit id: the
 * submitter's user name without its {@code IDIR\}), and logs start, end, totals and outcome on the
 * request row through {@code FTA690_UPDATE_RBS_REQUEST}, an autonomous transaction — so the row is
 * marked processed (successful_ind Y or N, message_text) even when the rest is rolled back. It
 * returns the summary report name and the error message. Legacy ran it with auto-commit off
 * ({@code BasicStore.put}) and committed when the error message was blank, else rolled back; so
 * does {@link #process}.
 *
 * <p>Here {@link RangeBillingProcessor} calls it as soon as a request is submitted, rather than a
 * nightly batch draining the queue.
 */
@Component
public class RangeBillingPackage {

  /** The package, as log lines name it. */
  public static final String PACKAGE = "FTA_690_TENURE_APPROVAL";

  /** PROCESS's outcome: the summary report name and the error message (blank on success). */
  public record ProcessResult(String summaryReport, String errorMessage) {}

  static final String PROCESS_CALL =
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
          + " v_queue the.fta_690_tenure_approval.queue_cur_t;"
          + " BEGIN"
          + " the.fta_690_tenure_approval.mainline(v_action, v_year, v_org, v_user, v_sdate,"
          + " v_stime, v_id, v_summ, v_err, v_queue);"
          + " ? := v_summ;"
          + " ? := v_err;"
          + " END;";

  /** Whether the request is still waiting: PROCESS would otherwise overwrite its outcome. */
  static final String UNPROCESSED_SQL =
      "SELECT COUNT(*) FROM the.fta_range_bill_request"
          + " WHERE range_bill_request_id = ? AND NVL(processed_ind, 'N') = 'N'";

  /**
   * FTA_411_PKG.rec_FTA411 for one request, from the row PROCESS updated: the FTA411 report's
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

  /** True while the request has not been processed. */
  public boolean isUnprocessed(long rangeBillRequestId) {
    Long n = jdbc.queryForObject(UNPROCESSED_SQL, Long.class, rangeBillRequestId);
    return n != null && n > 0;
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

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }
}
