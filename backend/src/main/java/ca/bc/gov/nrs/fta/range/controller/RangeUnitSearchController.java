package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitSearchDto;
import ca.bc.gov.nrs.fta.range.service.RangeUnitSearchService;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range unit / pasture search API — {@code GET /api/fta/range-units}. Parameters
 * mirror the legacy {@code THE.FTA_006_RU_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/range-units")
public class RangeUnitSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final RangeUnitSearchService rangeUnitSearchService;

  public RangeUnitSearchController(RangeUnitSearchService rangeUnitSearchService) {
    this.rangeUnitSearchService = rangeUnitSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<RangeUnitSearchDto>> search(
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String rangeUnitName,
      @RequestParam(required = false) String pastureName,
      @RequestParam(required = false) String rangeStatus,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(rangeUnitSearchService.search(
        orgUnitNo, rangeUnitName, pastureName, rangeStatus, safePage, safeSize));
  }
}
