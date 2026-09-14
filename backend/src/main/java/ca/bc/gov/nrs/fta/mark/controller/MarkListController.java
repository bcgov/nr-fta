package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkListDto;
import ca.bc.gov.nrs.fta.mark.service.MarkListService;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
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

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final MarkListService markListService;

  public MarkListController(MarkListService markListService) {
    this.markListService = markListService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<MarkListDto>> list(
      @RequestParam(required = false) String hdrDistrict,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String markStatusSt,
      @RequestParam(required = false) String orgUnitCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(markListService.list(
        hdrDistrict, timberMark, markStatusSt, orgUnitCode, clientName, safePage, safeSize));
  }
}
