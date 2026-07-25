package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import ca.bc.gov.nrs.fta.tenure.service.TenureService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure search API — {@code GET /api/fta/tenures}. Parameters mirror the
 * legacy {@code THE.FTA_001_TENR_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TenureController {

  private final TenureService tenureService;

  public TenureController(TenureService tenureService) {
    this.tenureService = tenureService;
  }

  @GetMapping
  public ResponseEntity<List<TenureSummaryDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String orgUnitCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String fileStatus) {
    return ResponseEntity.ok(
        tenureService.search(forestFileId, fileTypeCode, orgUnitCode, clientName, clientNumber, fileStatus));
  }
}
