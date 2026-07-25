package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest.RateItem;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for standard Range Billing rates/fees (FTA699).
 *
 * <p>Ports the update path of the legacy PL/SQL package {@code
 * THE.FTA_699_RATEFEE} (procedures {@code save} / {@code update_range_bill_rate})
 * to a native UPDATE against {@code THE.RANGE_BILL_RATE} via {@link
 * NamedParameterJdbcTemplate}. The legacy {@code save} proc validates that every
 * rate is between 0.00 and 999.99 and stamps all affected rows with a single new
 * {@code update_timestamp}; that behaviour is reproduced here (SYSDATE for the
 * audit timestamp, mirrored SET clause). Runs against the shared {@code THE}
 * Oracle schema — there is no local database, so it is exercised only in a
 * deployed environment.
 */
@Service
public class RatesSaveWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public RatesSaveWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Mirrors update_range_bill_rate in the FTA_699_RATEFEE package body: same SET
  // clause (range_rate, update_userid, update_timestamp, rng_tenr_rate_desc). The
  // row is keyed by its primary key (range_bill_rate_id, returned by the read
  // endpoint) for this row-oriented API, scoped to the calendar year for safety.
  private static final String UPDATE_SQL =
      """
      UPDATE the.range_bill_rate
         SET range_rate         = :rangeRate,
             update_userid      = :userId,
             update_timestamp   = SYSDATE,
             rng_tenr_rate_desc = :rngTenrRateDesc
       WHERE range_bill_rate_id = :rangeBillRateId
         AND calendar_year      = :calendarYear
      """;

  /**
   * Save the edited Range Billing rates. Each supplied row is updated in place;
   * returns the number of rows updated.
   *
   * @param request the calendar year and the edited rate rows
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public int save(RatesSaveRequest request, String userId) {
    List<RateItem> rates = request.rates();
    if (rates == null || rates.isEmpty()) {
      return 0;
    }
    int updated = 0;
    for (RateItem rate : rates) {
      MapSqlParameterSource params = new MapSqlParameterSource()
          .addValue("rangeRate", rate.rangeRate())
          .addValue("rngTenrRateDesc", rate.rngTenrRateDesc())
          .addValue("rangeBillRateId", rate.rangeBillRateId())
          .addValue("calendarYear", request.calendarYear())
          .addValue("userId", userId);
      updated += jdbc.update(UPDATE_SQL, params);
    }
    return updated;
  }
}
