package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.AuditReportDto;
import ca.bc.gov.nrs.fta.shared.service.AuditReportService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA402 Private Mark Certificate report API —
 * {@code GET /api/fta/admin/audit}. Parameters mirror the legacy
 * {@code THE.FTA_402_PKG} report inputs.
 */
@RestController
@RequestMapping("/api/fta/admin/audit")
public class AuditReportController {

  private final AuditReportService auditReportService;

  public AuditReportController(AuditReportService auditReportService) {
    this.auditReportService = auditReportService;
  }

  @GetMapping
  public ResponseEntity<List<AuditReportDto>> report(
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String mainLicensee) {
    return ResponseEntity.ok(auditReportService.report(timberMark, mainLicensee));
  }
}
