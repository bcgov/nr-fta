package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.RatesMaintenanceDto;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Fold;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.StoredRow;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA699 Rates &amp; Fees Maintenance, read side.
 *
 * <p>Ports {@code THE.FTA_699_RATEFEE.get}: for one calendar year, the rows sharing that year's
 * latest update_timestamp ({@code c_rate}), folded onto the 12 values the screen shows (see
 * {@link RatesRules#fold}). The year list is legacy's {@code fta.lookup.rangeBillCalendarYear}
 * ({@code FTA_CODE_LISTS.GET_RANGE_BILL_CALENDAR_YEAR}): every year with rates, and each such
 * year plus two, latest first.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class RatesMaintenanceService {

  private final NamedParameterJdbcTemplate jdbc;

  public RatesMaintenanceService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String YEARS_SQL =
      """
      SELECT DISTINCT calendar_year AS yr FROM the.range_bill_rate
       UNION
      SELECT DISTINCT calendar_year + 2 FROM the.range_bill_rate
       ORDER BY 1 DESC
      """;

  // c_rate. The timestamp is read to the second, as legacy's TO_CHAR, because it is the
  // optimistic lock the save matches on.
  private static final String RATES_SQL =
      """
      SELECT TO_CHAR(rbr.update_timestamp, 'YYYY-MM-DD HH24:MI:SS') AS update_timestamp,
             rbr.range_file_type_code AS range_file_type_code,
             rbr.range_rate_type_code AS range_rate_type_code,
             rbr.revenue_classn_code  AS revenue_classn_code,
             rbr.range_rate           AS range_rate,
             rbr.update_userid        AS update_userid
        FROM the.range_bill_rate rbr
       WHERE rbr.calendar_year = :calendarYear
         AND rbr.update_timestamp = (SELECT MAX(sub.update_timestamp)
                                       FROM the.range_bill_rate sub
                                      WHERE sub.calendar_year = :calendarYear)
       ORDER BY rbr.update_timestamp
      """;

  /** The Calendar Year dropdown: years with rates, and each plus two, latest first. */
  public List<Integer> years() {
    return jdbc.queryForList(YEARS_SQL, new MapSqlParameterSource(), Integer.class);
  }

  /** One year's 12 values — {@code FTA_699_RATEFEE.get}. */
  public RatesMaintenanceDto findYear(int calendarYear) {
    Fold fold = fold(calendarYear);
    return new RatesMaintenanceDto(
        calendarYear,
        fold.updateTimestamp(),
        fold.updateUserid(),
        fold.corrupt(),
        RatesRules.RATE_TYPES.stream()
            .map(t -> new RatesMaintenanceDto.Rate(t.code(), t.label(), fold.values().get(t.code())))
            .toList());
  }

  /** The year's rows as {@code get} reads them; the save checks its lock and corruption here. */
  Fold fold(int calendarYear) {
    List<StoredRow> rows = jdbc.query(
        RATES_SQL,
        new MapSqlParameterSource("calendarYear", calendarYear),
        (rs, n) -> new StoredRow(
            rs.getString("range_file_type_code"),
            rs.getString("range_rate_type_code"),
            rs.getString("revenue_classn_code"),
            rs.getBigDecimal("range_rate"),
            rs.getString("update_timestamp"),
            rs.getString("update_userid")));
    return RatesRules.fold(calendarYear, rows);
  }
}
