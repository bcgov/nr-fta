package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkListDto;
import ca.bc.gov.nrs.fta.mark.service.MarkListService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private mark application/amendment list API — {@code GET /api/fta/marks}.
 * Parameters mirror the legacy {@code THE.FTA_500_MARK_LIST.mainline} list
 * ({@code p_action = 'GET'}) inputs.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkListController {

  private final MarkListService markListService;

  public MarkListController(MarkListService markListService) {
    this.markListService = markListService;
  }

  @GetMapping
  public ResponseEntity<List<MarkListDto>> list(
      @RequestParam(required = false) String hdrDistrict,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String markStatusSt,
      @RequestParam(required = false) String orgUnitCode,
      @RequestParam(required = false) String clientName) {
    return ResponseEntity.ok(
        markListService.list(hdrDistrict, timberMark, markStatusSt, orgUnitCode, clientName));
  }
}
