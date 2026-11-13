package ca.bc.gov.nrs.fta.billing.service;

import ca.bc.gov.nrs.fta.billing.dto.BillingApprovalRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA690 Tenure Approval Submission, ported from
 * {@code FTA_690_TENURE_APPROVAL.SUBMIT}: queue a range billing run for a
 * district and year as a row in {@code FTA_RANGE_BILL_REQUEST}. The legacy
 * batch job (690) drains that queue through the package's PROCESS procedure and
 * creates the invoices; nothing here bills anyone.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class BillingApprovalService {

  static final String DISTRICT_REQUIRED = "District is required.";
  static final String YEAR_INVALID = "Billing Year must be a four-digit year.";
  static final String NOT_A_DISTRICT = "District is not a valid district.";
  static final String ALREADY_QUEUED = "A request has already been queued for this district today.";

  private static final String IS_DISTRICT_SQL =
      "SELECT COUNT(*) FROM the.org_unit WHERE org_unit_no = :orgUnitNo AND org_level_code = 'D'";

  /** SUBMIT's guard: one unprocessed request per district and year per day. */
  private static final String QUEUED_TODAY_SQL =
      """
      SELECT COUNT(*)
        FROM the.fta_range_bill_request
       WHERE org_unit_no = :orgUnitNo
         AND calendar_year = TO_NUMBER(:year)
         AND processed_ind = 'N'
         AND TRUNC(submit_date) = TRUNC(SYSDATE)
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.fta_range_bill_request (
        range_bill_request_id, org_unit_no, submit_userid, calendar_year,
        processed_ind, submit_date, start_date, end_date, successful_ind,
        message_text
      ) VALUES (
        the.fta_range_bill_request_seq.NEXTVAL, :orgUnitNo, :userId, TO_NUMBER(:year),
        'N', TRUNC(SYSDATE), NULL, NULL, NULL, NULL
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public BillingApprovalService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The Submit button.
   *
   * @throws ResponseStatusException 400 if the district or year is invalid, 409
   *     if a request for them is already queued today
   */
  @Transactional
  public void submit(BillingApprovalRequest req, String userId) {
    List<String> e = validate(req);
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("orgUnitNo", req.orgUnitNo())
        .addValue("year", req.calendarYear())
        .addValue("userId", userId);
    if (count(IS_DISTRICT_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOT_A_DISTRICT);
    }
    if (count(QUEUED_TODAY_SQL, p) > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_QUEUED);
    }
    jdbc.update(INSERT_SQL, p);
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

  private long count(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }
}
