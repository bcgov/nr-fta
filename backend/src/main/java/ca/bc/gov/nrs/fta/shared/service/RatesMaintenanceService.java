package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.RatesMaintenanceDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Standard Range Billing rates/fees maintenance business logic.
 *
 * <p>Ports the {@code GET} path of the legacy Oracle package
 * {@code THE.FTA_699_RATEFEE} (Rates &amp; Fees Maintenance) to a native query
 * against the shared {@code THE} schema. The query mirrors the package's
 * {@code c_rate} cursor: for a given calendar year it returns the most recent
 * generation of {@code RANGE_BILL_RATE} rows (the set sharing the MAX
 * update_timestamp for that year). The {@code p_calendar_year} mainline input
 * is applied only when supplied (NVL-style); when omitted, the latest set for
 * every calendar year is returned.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RatesMaintenanceService {

  private final NamedParameterJdbcTemplate jdbc;

  public RatesMaintenanceService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String RATES_SQL =
      """
      SELECT rbr.range_bill_rate_id   AS range_bill_rate_id,
             rbr.calendar_year        AS calendar_year,
             rbr.update_timestamp     AS update_timestamp,
             rbr.range_file_type_code AS range_file_type_code,
             rbr.range_rate_type_code AS range_rate_type_code,
             rbr.revenue_classn_code  AS revenue_classn_code,
             rbr.range_rate           AS range_rate,
             rbr.update_userid        AS update_userid,
             rbr.rng_tenr_rate_desc   AS rng_tenr_rate_desc
        FROM the.range_bill_rate rbr
       WHERE (:calendarYear IS NULL OR rbr.calendar_year = :calendarYear)
         AND rbr.update_timestamp = (SELECT MAX(sub.update_timestamp)
                                       FROM the.range_bill_rate sub
                                      WHERE sub.calendar_year = rbr.calendar_year)
       ORDER BY rbr.calendar_year, rbr.range_rate_type_code, rbr.range_file_type_code
      """;

  /**
   * Retrieve the current set of standard range rates/fees — mirrors
   * {@code FTA_699_RATEFEE.get}.
   *
   * @param calendarYear the calendar year to retrieve, or null for all years
   */
  public List<RatesMaintenanceDto> findRates(Integer calendarYear) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("calendarYear", calendarYear);

    return jdbc.query(RATES_SQL, params, (rs, rowNum) -> new RatesMaintenanceDto(
        rs.getObject("range_bill_rate_id", Long.class),
        rs.getObject("calendar_year", Integer.class),
        rs.getObject("update_timestamp", java.time.LocalDate.class),
        rs.getString("range_file_type_code"),
        rs.getString("range_rate_type_code"),
        rs.getString("revenue_classn_code"),
        rs.getBigDecimal("range_rate"),
        rs.getString("update_userid"),
        rs.getString("rng_tenr_rate_desc")));
  }
}
