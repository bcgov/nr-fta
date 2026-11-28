package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.BillingApprovalRequest;
import ca.bc.gov.nrs.fta.billing.dto.BillingRequestDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA690 Tenure Approval Submission.
 *
 * <ul>
 *   <li>{@link #submit} — legacy {@code FTA_690_TENURE_APPROVAL.SUBMIT}: a row in
 *       {@code FTA_RANGE_BILL_REQUEST} for a district and year. The caller then has
 *       {@link RangeBillingProcessor} bill it while the user waits (legacy waited for the nightly
 *       batch) and reads the outcome back with {@link #request}.
 *   <li>{@link #summaryPdf} — the FTA411 Approval Summary of a processed request, which legacy's
 *       batch e-mailed to the submitter.
 * </ul>
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class BillingApprovalService {

  static final String DISTRICT_REQUIRED = "District is required.";
  static final String YEAR_INVALID = "Billing Year must be a four-digit year.";
  static final String NOT_A_DISTRICT = "District is not a valid district.";
  static final String NOT_FOUND = "Billing request not found.";
  static final String NOT_PROCESSED = "The billing request has not been processed yet.";

  static final String SUMMARY_TEMPLATE = "reports/FTA411_ApprovalSummary.jrxml";

  private static final String DISTRICT_CODE_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :orgUnitNo"
          + " AND org_level_code = 'D'";

  /**
   * An unprocessed request for the district and year — one a stopped pod or legacy's queue left —
   * which Submit bills instead of adding another. Legacy's SUBMIT refused a second request for
   * the same day while the first waited for the batch; nothing waits now.
   */
  static final String UNPROCESSED_SQL =
      """
      SELECT range_bill_request_id
        FROM the.fta_range_bill_request
       WHERE org_unit_no = :orgUnitNo
         AND calendar_year = TO_NUMBER(:year)
         AND NVL(processed_ind, 'N') = 'N'
       ORDER BY range_bill_request_id
      """;

  private static final String NEXT_ID_SQL =
      "SELECT the.fta_range_bill_request_seq.NEXTVAL FROM dual";

  private static final String INSERT_SQL =
      """
      INSERT INTO the.fta_range_bill_request (
        range_bill_request_id, org_unit_no, submit_userid, calendar_year,
        processed_ind, submit_date, start_date, end_date, successful_ind,
        message_text
      ) VALUES (
        :id, :orgUnitNo, :userId, TO_NUMBER(:year),
        'N', TRUNC(SYSDATE), NULL, NULL, NULL, NULL
      )
      """;

  private static final String SELECT =
      """
      SELECT r.range_bill_request_id, r.org_unit_no, ou.org_unit_code, ou.org_unit_name,
             r.calendar_year, r.submit_userid, r.submit_date, r.start_date, r.end_date,
             r.processed_ind, r.successful_ind, r.message_text, r.total_invoices_loaded,
             r.first_invoice_number, r.last_invoice_number, r.total_invoice_value
        FROM the.fta_range_bill_request r
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = r.org_unit_no
      """;

  static final String REQUEST_SQL = SELECT + " WHERE r.range_bill_request_id = :id";

  private final NamedParameterJdbcTemplate jdbc;
  private final BillingDistrictScope scope;
  private final RangeBillingPackage pkg;

  public BillingApprovalService(
      NamedParameterJdbcTemplate jdbc, BillingDistrictScope scope, RangeBillingPackage pkg) {
    this.jdbc = jdbc;
    this.scope = scope;
    this.pkg = pkg;
  }

  /**
   * The Submit button's insert. Committed on return, so the processor can see the row.
   *
   * @return the request to bill: the district and year's unprocessed one if there is one, else a
   *     new one
   * @throws ResponseStatusException 400 if the district or year is invalid, 403 if the user
   *     does not administer the district
   */
  @Transactional
  public long submit(BillingApprovalRequest req, String userId) {
    List<String> e = validate(req);
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("orgUnitNo", req.orgUnitNo())
        .addValue("year", req.calendarYear())
        .addValue("userId", userId);
    requireDistrict(req.orgUnitNo());
    List<Long> waiting = jdbc.queryForList(UNPROCESSED_SQL, p, Long.class);
    if (!waiting.isEmpty()) {
      return waiting.get(0);
    }
    Long id = jdbc.queryForObject(NEXT_ID_SQL, new MapSqlParameterSource(), Long.class);
    if (id == null) {
      throw new IllegalStateException("FTA_RANGE_BILL_REQUEST_SEQ returned no value");
    }
    jdbc.update(INSERT_SQL, p.addValue("id", id));
    return id;
  }

  /** One request, if the user administers its district. */
  public BillingRequestDto request(long id) {
    List<BillingRequestDto> rows = jdbc.query(REQUEST_SQL, Map.of("id", id), this::row);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }
    BillingRequestDto r = rows.get(0);
    scope.requireDistrict(r.orgUnitCode());
    return r;
  }

  /**
   * The FTA411 Approval Summary PDF of a processed request.
   *
   * @throws ResponseStatusException 409 while the request is unprocessed
   */
  public byte[] summaryPdf(long id) {
    BillingRequestDto r = request(id);
    if (BillingRequestDto.QUEUED.equals(r.status())
        || BillingRequestDto.PROCESSING.equals(r.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, NOT_PROCESSED);
    }
    Map<String, Object> row = pkg.summary(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
    Map<String, Object> params = new HashMap<>();
    params.put("p_range_bill_request_id", Long.toString(id));
    return JasperTemplates.pdf(SUMMARY_TEMPLATE, params, List.of(row));
  }

  static List<String> validate(BillingApprovalRequest req) {
    List<String> e = new ArrayList<>();
    if (req == null || req.orgUnitNo() == null) {
      e.add(DISTRICT_REQUIRED);
    }
    if (req == null || req.calendarYear() == null || !req.calendarYear().matches("\\d{4}")) {
      e.add(YEAR_INVALID);
    }
    return e;
  }

  /**
   * A request's status from its row: legacy's {@code processed_ind}, {@code start_date},
   * {@code successful_ind} and {@code message_text}. The package's aborts ("No instructions
   * found …") mark the row successful with a message and no invoices, so a message means FAILED.
   */
  static String status(String processedInd, Timestamp startDate, String successfulInd,
      String messageText) {
    if (!"Y".equals(processedInd)) {
      return startDate == null ? BillingRequestDto.QUEUED : BillingRequestDto.PROCESSING;
    }
    return "Y".equals(successfulInd) && !StringUtils.hasText(messageText)
        ? BillingRequestDto.COMPLETE
        : BillingRequestDto.FAILED;
  }

  /** Legacy protected a district user's District: only the district they administer. */
  private void requireDistrict(Long orgUnitNo) {
    List<String> codes = jdbc.queryForList(
        DISTRICT_CODE_SQL, Map.of("orgUnitNo", orgUnitNo), String.class);
    if (codes.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOT_A_DISTRICT);
    }
    scope.requireDistrict(codes.get(0));
  }

  private BillingRequestDto row(ResultSet rs, int n) throws SQLException {
    long id = rs.getLong("range_bill_request_id");
    String processed = rs.getString("processed_ind");
    Timestamp start = rs.getTimestamp("start_date");
    Timestamp end = rs.getTimestamp("end_date");
    Timestamp submit = rs.getTimestamp("submit_date");
    String message = rs.getString("message_text");
    String status = status(processed, start, rs.getString("successful_ind"), message);
    return new BillingRequestDto(
        id,
        nullableLong(rs, "org_unit_no"),
        rs.getString("org_unit_code"),
        rs.getString("org_unit_name"),
        nullableInt(rs, "calendar_year"),
        rs.getString("submit_userid"),
        submit == null ? null : submit.toLocalDateTime().toLocalDate(),
        start == null ? null : start.toLocalDateTime(),
        end == null ? null : end.toLocalDateTime(),
        status,
        message,
        nullableInt(rs, "total_invoices_loaded"),
        rs.getString("first_invoice_number"),
        rs.getString("last_invoice_number"),
        rs.getBigDecimal("total_invoice_value"));
  }

  private static Long nullableLong(ResultSet rs, String col) throws SQLException {
    long v = rs.getLong(col);
    return rs.wasNull() ? null : v;
  }

  private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
    int v = rs.getInt(col);
    return rs.wasNull() ? null : v;
  }
}
