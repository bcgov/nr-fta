package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryDtos.HarvestHistoryDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure harvest history tab API (legacy FTA990, read-only):
 * {@code GET /api/fta/tenures/{forestFileId}/harvest-history} — the file's timber mark's
 * appraisal header and billing history; {@code GET /api/fta/timber-marks/{mark}/harvest-history}
 * — the same for one mark, as a cutting permit's tab reads it;
 * {@code GET /api/fta/timber-marks/{mark}/mark-report} — FTA990's "Print Mark Report", the
 * FTA404 Mark Report PDF for the mark.
 */
@RestController
@RequestMapping("/api/fta")
public class HarvestHistoryController {

  private final HarvestHistoryService harvestHistoryService;
  private final MarkReportService markReportService;

  public HarvestHistoryController(HarvestHistoryService harvestHistoryService,
      MarkReportService markReportService) {
    this.harvestHistoryService = harvestHistoryService;
    this.markReportService = markReportService;
  }

  @GetMapping("/tenures/{forestFileId}/harvest-history")
  public HarvestHistoryDto get(@PathVariable String forestFileId) {
    return harvestHistoryService.get(forestFileId);
  }

  @GetMapping("/timber-marks/{timberMark}/harvest-history")
  public HarvestHistoryDto forTimberMark(@PathVariable String timberMark) {
    return harvestHistoryService.forTimberMark(timberMark);
  }

  @GetMapping("/timber-marks/{timberMark}/mark-report")
  public ResponseEntity<byte[]> markReport(
      @PathVariable String timberMark, JwtAuthenticationToken principal) {
    MarkReportService.Pdf pdf =
        markReportService.render(timberMark, JwtPrincipalUtil.getAuditUserId(principal));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(pdf.filename()).build());
    return ResponseEntity.ok().headers(headers).body(pdf.content());
  }
}
