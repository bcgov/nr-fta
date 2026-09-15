package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import ca.bc.gov.nrs.fta.tenure.service.TenureService;
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

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * The criteria are listed one per parameter rather than bound from an object,
   * so the query contract is visible here and a renamed record component cannot
   * silently change the API.
   */
  @GetMapping
  public ResponseEntity<PagedResponse<TenureSummaryDto>> search(
      @RequestParam(required = false) String adminOrgUnitNo,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String tenureType,
      @RequestParam(required = false) String fileStatus,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String fileClientType,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String fileSource,
      @RequestParam(required = false) String assocFileId,
      @RequestParam(required = false) String fileName,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String salvageInd,
      @RequestParam(required = false) String cashSaleInd,
      @RequestParam(required = false) String mapNotationTypeCode,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    TenureSearchCriteria criteria = new TenureSearchCriteria(
        adminOrgUnitNo, forestFileId, fileTypeCode, tenureType, fileStatus,
        clientNumber, clientLocnCode, clientName, fileClientType,
        mgmtUnitType, mgmtUnitId, fileSource, assocFileId, fileName,
        issueDateFrom, issueDateTo, expiryDateFrom, expiryDateTo,
        salvageInd, cashSaleInd, mapNotationTypeCode, sortBy);

    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(tenureService.search(criteria, safePage, safeSize));
  }
}
