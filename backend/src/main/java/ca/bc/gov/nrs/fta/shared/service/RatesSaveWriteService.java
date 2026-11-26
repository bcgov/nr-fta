package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Fold;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Row;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA699 Rates &amp; Fees Maintenance, Save — {@code THE.FTA_699_RATEFEE.save}.
 *
 * <p>One calendar year at a time. The 12 values are checked as {@code Fta699RatefeeForm} did
 * (required, 0.00 to 999.99, at most two decimals), then each is written to every one of its
 * {@code RANGE_BILL_RATE} rows ({@link RatesRules#ROWS}), all 24 stamped with one new
 * update_timestamp:
 * <ul>
 *   <li>a year read with no timestamp (none entered yet) gets its 24 rows inserted;</li>
 *   <li>otherwise each row is updated where it still carries the timestamp it was read with. A
 *       row that no longer does was saved by someone else in between: the whole save is refused
 *       and rolled back (legacy's "record modified").</li>
 * </ul>
 * {@code rng_tenr_rate_desc} is written NULL, as legacy always did. A year whose stored rows are
 * not its 24 is corrupt; legacy kept Save off for it, and the server refuses it too.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RatesSaveWriteService {

  private static final String NOW_SQL =
      "SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM dual";

  // insert_range_bill_rate.
  private static final String INSERT_SQL =
      """
      INSERT INTO the.range_bill_rate (
        range_bill_rate_id, calendar_year, update_timestamp,
        range_file_type_code, range_rate_type_code, revenue_classn_code,
        range_rate, update_userid, rng_tenr_rate_desc)
      VALUES (
        the.range_bill_rate_seq.NEXTVAL, :calendarYear, TO_DATE(:newTimestamp, 'YYYY-MM-DD HH24:MI:SS'),
        :fileType, :rateType, :revenueClass,
        :rate, :userId, NULL)
      """;

  // update_range_bill_rate: keyed by the row's natural key and the timestamp it was read with.
  private static final String UPDATE_SQL =
      """
      UPDATE the.range_bill_rate
         SET update_timestamp   = TO_DATE(:newTimestamp, 'YYYY-MM-DD HH24:MI:SS'),
             range_rate         = :rate,
             update_userid      = :userId,
             rng_tenr_rate_desc = NULL
       WHERE update_timestamp     = TO_DATE(:updateTimestamp, 'YYYY-MM-DD HH24:MI:SS')
         AND calendar_year        = :calendarYear
         AND range_file_type_code = :fileType
         AND range_rate_type_code = :rateType
         AND revenue_classn_code  = :revenueClass
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RatesMaintenanceService rates;

  public RatesSaveWriteService(NamedParameterJdbcTemplate jdbc, RatesMaintenanceService rates) {
    this.jdbc = jdbc;
    this.rates = rates;
  }

  /**
   * Save one year's 12 values. Returns the number of rows written (24).
   *
   * @param request the year, the timestamp it was read with, and the 12 values
   * @param userId  the authenticated user id (audit column)
   * @throws ResponseStatusException 400 for a missing or unlisted year or an invalid value;
   *     409 if the year is corrupt or was saved by someone else since it was read
   */
  @Transactional
  public int save(RatesSaveRequest request, String userId) {
    Integer year = request == null ? null : request.calendarYear();
    if (year == null) {
      throw status(HttpStatus.BAD_REQUEST, RatesRules.YEAR_REQUIRED);
    }
    if (!rates.years().contains(year)) {
      throw status(HttpStatus.BAD_REQUEST, RatesRules.yearNotListed(year));
    }
    Map<String, String> errors = RatesRules.validate(request.rates());
    if (!errors.isEmpty()) {
      throw status(HttpStatus.BAD_REQUEST, String.join(" ", errors.values()));
    }
    Fold current = rates.fold(year);
    if (current.corrupt()) {
      throw status(HttpStatus.CONFLICT, RatesRules.CORRUPT);
    }

    Map<String, BigDecimal> values = new HashMap<>();
    RatesRules.RATE_TYPES.forEach(
        t -> values.put(t.code(), RatesRules.parse(request.rates().get(t.code()))));
    // One timestamp for every row of the save (legacy's l_timestamp).
    String newTimestamp =
        jdbc.queryForObject(NOW_SQL, new MapSqlParameterSource(), String.class);
    String readWith = StringUtils.hasText(request.updateTimestamp())
        ? request.updateTimestamp().trim()
        : null;

    int written = 0;
    for (Row row : RatesRules.ROWS) {
      MapSqlParameterSource p = new MapSqlParameterSource()
          .addValue("calendarYear", year)
          .addValue("newTimestamp", newTimestamp)
          .addValue("updateTimestamp", readWith)
          .addValue("fileType", row.fileType())
          .addValue("rateType", row.rateType())
          .addValue("revenueClass", row.revenueClass())
          .addValue("rate", values.get(row.value()))
          .addValue("userId", userId);
      if (readWith == null) {
        written += jdbc.update(INSERT_SQL, p);
      } else {
        int n = jdbc.update(UPDATE_SQL, p);
        if (n == 0) {
          // Thrown from inside the transaction, so the rows already updated roll back.
          throw status(HttpStatus.CONFLICT, RatesRules.MODIFIED);
        }
        written += n;
      }
    }
    return written;
  }

  private static ResponseStatusException status(HttpStatus status, String message) {
    return new ResponseStatusException(status, message);
  }
}
