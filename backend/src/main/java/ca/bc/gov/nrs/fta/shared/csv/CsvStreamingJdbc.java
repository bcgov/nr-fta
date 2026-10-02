package ca.bc.gov.nrs.fta.shared.csv;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The JDBC template the CSV exports query through.
 *
 * <p>Separate from the application's shared template for one reason: the fetch
 * size. Oracle's driver defaults to 10 rows per round trip, which is fine for a
 * 10-row page and ruinous for an export of tens of thousands — that would be
 * thousands of round trips. This template asks for 1,000 rows at a time, which
 * cuts the round trips by two orders of magnitude at the cost of a larger
 * driver-side buffer.
 *
 * <p>Setting the fetch size on the shared template instead would apply it to
 * every query in the application, including the single-row detail reads, so it
 * is scoped here.
 */
@Component
public class CsvStreamingJdbc {

  /** Rows per round trip when streaming an export. */
  private static final int FETCH_SIZE = 1000;

  private final NamedParameterJdbcTemplate jdbc;

  public CsvStreamingJdbc(DataSource dataSource) {
    JdbcTemplate template = new JdbcTemplate(dataSource);
    template.setFetchSize(FETCH_SIZE);
    this.jdbc = new NamedParameterJdbcTemplate(template);
  }

  /** The template to run an export's query on. */
  public NamedParameterJdbcTemplate jdbc() {
    return jdbc;
  }
}
