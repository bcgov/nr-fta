package ca.bc.gov.nrs.fta.tenure.metrics;

import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Column;
import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Criteria;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA008 Application Metrics Export (Search &gt; Application Metrics Export): legacy's Export
 * button, which wrote an Excel workbook of cutting permit application metrics, with no on-screen
 * list.
 *
 * <ul>
 *   <li>sheet {@code Metrics} — {@code FTA_008_METRICS_EXTRACT.GET_METRICS}: each CP application
 *       (pending, approved or issued, not an A11 file) with its submission, email, First Nations
 *       consultation, decision and issuance dates, hold status and day counts;</li>
 *   <li>sheet {@code Holds} — {@code GET_HOLDS}: each hold on those applications.</li>
 * </ul>
 *
 * <p>The package does the selecting and computing; this checks the criteria as the form's
 * validators did, runs it, and lays out the sheets as {@code Fta008MetricsSrchAction} did. A
 * search with no matches still gives the workbook, headings only, as legacy's did.
 */
@Service
public class ApplicationMetricsService {

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  /** The workbook and the name legacy gave it. */
  public record Workbook(String filename, byte[] content) {}

  private final ApplicationMetricsPackage pkg;
  private final Clock clock;

  @Autowired
  public ApplicationMetricsService(ApplicationMetricsPackage pkg) {
    this(pkg, Clock.system(PACIFIC));
  }

  ApplicationMetricsService(ApplicationMetricsPackage pkg, Clock clock) {
    this.pkg = pkg;
    this.clock = clock;
  }

  /**
   * The export for these criteria.
   *
   * @throws ResponseStatusException 400 with the form's errors; 500 with the package's, when it
   *                                 reports one
   */
  public Workbook export(Criteria criteria) {
    List<String> errors = ApplicationMetricsRules.validate(criteria);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    ApplicationMetricsPackage.Extract extract =
        pkg.extract(ApplicationMetricsRules.procedureArguments(criteria));
    String message = ApplicationMetricsRules.message(extract.errorMessage());
    if (!message.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
    byte[] xlsx = MetricsWorkbook.xlsx(List.of(
        sheet(ApplicationMetricsRules.METRICS_SHEET, ApplicationMetricsRules.METRICS_COLUMNS,
            extract.metrics()),
        sheet(ApplicationMetricsRules.HOLDS_SHEET, ApplicationMetricsRules.HOLDS_COLUMNS,
            extract.holds())));
    return new Workbook(ApplicationMetricsRules.filename(LocalDateTime.now(clock)), xlsx);
  }

  private static MetricsWorkbook.Sheet sheet(String name, List<Column> columns,
      List<List<String>> rows) {
    return new MetricsWorkbook.Sheet(name, columns.stream().map(Column::heading).toList(), rows);
  }
}
