package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptReportParams;
import ca.bc.gov.nrs.fta.apt.service.AptReportDefinition;
import ca.bc.gov.nrs.fta.apt.service.AptReportExtract;
import ca.bc.gov.nrs.fta.apt.service.AptReportService;
import ca.bc.gov.nrs.fta.shared.csv.CsvExport;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * APT reports — {@code GET /api/fta/apt/reports/...}. GETs, so every APT reader may run every
 * report, as in APT2.
 *
 * <ul>
 *   <li>{@code /{code}} — a PDF report ({@code APTR011} … {@code APTR043}), rendered in the
 *       backend; 501 until the report's template has been added.</li>
 *   <li>{@code /{code}/csv} — a CSV data extract ({@code APTR013}, {@code APTR041}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/fta/apt/reports")
public class AptReportController {

  private final AptReportService reports;

  public AptReportController(AptReportService reports) {
    this.reports = reports;
  }

  /**
   * One PDF report. Each report reads only the inputs it needs (see
   * {@link AptReportDefinition}); a missing one is a 400.
   */
  @GetMapping("/{code}")
  public ResponseEntity<byte[]> pdf(
      @PathVariable String code,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate reportDate,
      @RequestParam(required = false) Long managementUnitId,
      @RequestParam(required = false) Integer orgUnitNo,
      JwtAuthenticationToken principal) {
    AptReportDefinition report = AptReportDefinition.of(code)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "No APT report " + code + "."));
    byte[] pdf = reports.pdf(report,
        new AptReportParams(reportDate, managementUnitId, orgUnitNo),
        JwtPrincipalUtil.getAuditUserId(principal));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.inline().filename(report.name() + ".pdf").build());
    headers.setCacheControl("no-store");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /** One CSV data extract, as a {@code text/csv} attachment. */
  @GetMapping("/{code}/csv")
  public ResponseEntity<StreamingResponseBody> csv(@PathVariable String code) {
    AptReportExtract extract = AptReportExtract.of(code)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "No APT data extract " + code + "."));
    // Read before the response starts, so a database error is still an HTTP error.
    List<List<String>> rows = reports.extract(extract);
    return CsvExport.response(extract.fileName(),
        csv -> rows.forEach(row -> csv.writeRow(row.toArray())));
  }
}
