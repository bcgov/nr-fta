package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.service.AuditReportService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA675 Audit Report — {@code /api/fta/admin/audit}:
 * <ul>
 *   <li>{@code GET /api/fta/admin/audit} — the FTA403 District Override Audit or FTA405 Delete
 *       EE Audit, as a PDF</li>
 *   <li>{@code GET /api/fta/admin/audit/district-regions} — each district's region, so the
 *       screen can check the district is in the region</li>
 * </ul>
 * Access is {@code FTA_ADMIN} by the URL-level authorization rules
 * ({@code ApiAuthorizationCustomizer}); which districts, the service decides.
 */
@RestController
@RequestMapping("/api/fta/admin/audit")
public class AuditReportController {

  private final AuditReportService auditReportService;

  public AuditReportController(AuditReportService auditReportService) {
    this.auditReportService = auditReportService;
  }

  /**
   * @param report   FTA403 or FTA405
   * @param region   a region's ORG_UNIT_NO, or "*"
   * @param district a district's ORG_UNIT_NO, or "*"
   * @param fileType up to five, in order; blanks allowed
   */
  @GetMapping
  public ResponseEntity<byte[]> report(
      @RequestParam(required = false) String report,
      @RequestParam(required = false) String region,
      @RequestParam(required = false) String district,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) List<String> fileType,
      JwtAuthenticationToken principal) {
    AuditReportService.Pdf pdf = auditReportService.render(report, region, district, fromDate,
        toDate, fileType, JwtPrincipalUtil.getAuditUserId(principal));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(pdf.filename()).build());
    return ResponseEntity.ok().headers(headers).body(pdf.content());
  }

  @GetMapping("/district-regions")
  public ResponseEntity<Map<String, String>> districtRegions() {
    return ResponseEntity.ok(auditReportService.districtRegions());
  }
}
