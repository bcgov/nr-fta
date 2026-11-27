package ca.bc.gov.nrs.fta.tenure.metrics;

import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Criteria;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA008 Application Metrics Export —
 * {@code GET /api/fta/application-metrics/export}: the Excel workbook legacy's Export button
 * produced.
 *
 * <p>Every criterion is optional: District is an ORG_UNIT_NO (blank for every district), the
 * dates {@code YYYY-MM-DD}. Access is the generic read rule (ApiAuthorizationCustomizer):
 * FTA_ADMIN, FTA_VIEWER and the harvest roles. Legacy put the screen on every user's Search menu
 * and granted the package to every FTA role.
 */
@RestController
@RequestMapping("/api/fta/application-metrics")
public class ApplicationMetricsController {

  private final ApplicationMetricsService service;

  public ApplicationMetricsController(ApplicationMetricsService service) {
    this.service = service;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> export(
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String submissionDateFrom,
      @RequestParam(required = false) String submissionDateTo,
      @RequestParam(required = false) String emailRecdDateFrom,
      @RequestParam(required = false) String emailRecdDateTo,
      @RequestParam(required = false) String acceptanceDateFrom,
      @RequestParam(required = false) String acceptanceDateTo,
      @RequestParam(required = false) String decisionDateFrom,
      @RequestParam(required = false) String decisionDateTo,
      @RequestParam(required = false) String issuanceDateFrom,
      @RequestParam(required = false) String issuanceDateTo) {
    ApplicationMetricsService.Workbook workbook = service.export(new Criteria(
        orgUnitNo, submissionDateFrom, submissionDateTo, emailRecdDateFrom, emailRecdDateTo,
        acceptanceDateFrom, acceptanceDateTo, decisionDateFrom, decisionDateTo,
        issuanceDateFrom, issuanceDateTo));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(ApplicationMetricsRules.CONTENT_TYPE));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(workbook.filename()).build());
    return ResponseEntity.ok().headers(headers).body(workbook.content());
  }
}
