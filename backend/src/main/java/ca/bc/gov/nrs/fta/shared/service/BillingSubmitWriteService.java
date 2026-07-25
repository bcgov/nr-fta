package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.BillingSubmitRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for the Range Billing submission flow (FTA690).
 *
 * <p>Ports the {@code SUBMIT} path of the legacy {@code Fta_690_Tenure_Approval}
 * package to an INSERT against {@code THE.FTA_RANGE_BILL_REQUEST} via {@link
 * NamedParameterJdbcTemplate}. The row acts as the queue a nightly batch process
 * drains (via the package's {@code GET}/{@code PROCESS} procedures) to create the
 * invoice transactions. The legacy proc guards against a duplicate un-processed
 * request for the same org-unit / calendar-year on the same day; that guard
 * should be reproduced here as the module is hardened. Runs against the shared
 * {@code THE} Oracle schema — there is no local database, so it is exercised only
 * in a deployed environment.
 */
@Service
public class BillingSubmitWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public BillingSubmitWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Mirrors Fta_690_Tenure_Approval.SUBMIT: queue a Range Billing request row.
  private static final String INSERT_SQL =
      """
      INSERT INTO the.fta_range_bill_request (
        range_bill_request_id, org_unit_no, submit_userid, calendar_year,
        processed_ind, submit_date, start_date, end_date, successful_ind,
        message_text
      ) VALUES (
        the.fta_range_bill_request_seq.NEXTVAL,
        TO_NUMBER(:orgUnitNo), :userId, TO_NUMBER(:calendarYear),
        'N', TRUNC(SYSDATE), NULL, NULL, NULL, NULL
      )
      """;

  /**
   * Queue a Range Billing invoicing request. Returns the number of rows written.
   *
   * @param request the calendar year + org unit to submit
   * @param userId  the authenticated user id (submit / audit column)
   */
  @Transactional
  public int submit(BillingSubmitRequest request, String userId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("orgUnitNo", request.orgUnitNo())
        .addValue("calendarYear", request.calendarYear())
        .addValue("userId", userId);
    return jdbc.update(INSERT_SQL, params);
  }
}
