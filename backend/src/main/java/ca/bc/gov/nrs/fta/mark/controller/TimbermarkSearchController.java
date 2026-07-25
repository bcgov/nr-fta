package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchDto;
import ca.bc.gov.nrs.fta.mark.service.TimbermarkSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timber-mark search API — {@code GET /api/fta/timber-marks}. Parameters mirror
 * the legacy {@code THE.FTA_002_MARK_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/timber-marks")
public class TimbermarkSearchController {

  private final TimbermarkSearchService timbermarkSearchService;

  public TimbermarkSearchController(TimbermarkSearchService timbermarkSearchService) {
    this.timbermarkSearchService = timbermarkSearchService;
  }

  @GetMapping
  public ResponseEntity<List<TimbermarkSearchDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String markStatusSt,
      @RequestParam(required = false) String certificate,
      @RequestParam(required = false) String salvageInd,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientName) {
    return ResponseEntity.ok(
        timbermarkSearchService.search(
            forestFileId,
            timberMark,
            cuttingPermitId,
            fileTypeCode,
            markStatusSt,
            certificate,
            salvageInd,
            clientNumber,
            clientName));
  }
}
