package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockSearchDto;
import ca.bc.gov.nrs.fta.tenure.service.CutblockSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cut-block search API — {@code GET /api/fta/cut-blocks}. Parameters mirror the
 * legacy {@code THE.FTA_003_CUTBLK_SRCH.mainline} / {@code get} search inputs.
 */
@RestController
@RequestMapping("/api/fta/cut-blocks")
public class CutblockSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final CutblockSearchService cutblockSearchService;

  public CutblockSearchController(CutblockSearchService cutblockSearchService) {
    this.cutblockSearchService = cutblockSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<CutblockSearchDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String cutBlockId,
      @RequestParam(required = false) String blockStatusSt,
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String managedByFile,
      @RequestParam(required = false) String managedByCp,
      @RequestParam(required = false) String harvestStartDateFrom,
      @RequestParam(required = false) String harvestStartDateTo,
      @RequestParam(required = false) String districtAdminZone,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(cutblockSearchService.search(
        forestFileId, cuttingPermitId, timberMark, cutBlockId, blockStatusSt, orgUnitNo,
        clientNumber, clientLocnCode, clientName, managedByFile, managedByCp,
        harvestStartDateFrom, harvestStartDateTo, districtAdminZone,
        sortBy, safePage, safeSize));
  }
}
