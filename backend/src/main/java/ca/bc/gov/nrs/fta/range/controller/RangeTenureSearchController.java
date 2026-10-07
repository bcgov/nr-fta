package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureSearchDto;
import ca.bc.gov.nrs.fta.range.service.RangeTenureSearchService;
import ca.bc.gov.nrs.fta.shared.csv.CsvExport;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Range tenure search API — {@code GET /api/fta/range-tenures}. Parameters
 * mirror the legacy {@code THE.FTA_001R_TENR_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/range-tenures")
public class RangeTenureSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final RangeTenureSearchService rangeTenureSearchService;

  public RangeTenureSearchController(RangeTenureSearchService rangeTenureSearchService) {
    this.rangeTenureSearchService = rangeTenureSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<RangeTenureSearchDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String fileClientType,
      @RequestParam(required = false) String fileStatus,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String provisionYear,
      @RequestParam(required = false) String authorizedUseFrom,
      @RequestParam(required = false) String authorizedUseTo,
      @RequestParam(required = false) String temporaryIncreaseFrom,
      @RequestParam(required = false) String temporaryIncreaseTo,
      @RequestParam(required = false) String billableNonUseFrom,
      @RequestParam(required = false) String billableNonUseTo,
      @RequestParam(required = false) String nonBillableNonUseFrom,
      @RequestParam(required = false) String nonBillableNonUseTo,
      @RequestParam(required = false) String totalAnnualUseFrom,
      @RequestParam(required = false) String totalAnnualUseTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(
        rangeTenureSearchService.search(
            forestFileId,
            fileTypeCode,
            orgUnitNo,
            zone,
            clientName,
            clientNumber,
            clientLocnCode,
            fileClientType,
            fileStatus,
            mgmtUnitType,
            mgmtUnitId,
            issueDateFrom,
            issueDateTo,
            expiryDateFrom,
            expiryDateTo,
            provisionYear,
            authorizedUseFrom,
            authorizedUseTo,
            temporaryIncreaseFrom,
            temporaryIncreaseTo,
            billableNonUseFrom,
            billableNonUseTo,
            nonBillableNonUseFrom,
            nonBillableNonUseTo,
            totalAnnualUseFrom,
            totalAnnualUseTo,
            safePage,
            safeSize));
  }

  /**
   * Every matching range tenure as a CSV download — the same criteria as the
   * search, with no paging, streamed as the rows arrive.
   */
  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> exportCsv(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String fileClientType,
      @RequestParam(required = false) String fileStatus,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String provisionYear,
      @RequestParam(required = false) String authorizedUseFrom,
      @RequestParam(required = false) String authorizedUseTo,
      @RequestParam(required = false) String temporaryIncreaseFrom,
      @RequestParam(required = false) String temporaryIncreaseTo,
      @RequestParam(required = false) String billableNonUseFrom,
      @RequestParam(required = false) String billableNonUseTo,
      @RequestParam(required = false) String nonBillableNonUseFrom,
      @RequestParam(required = false) String nonBillableNonUseTo,
      @RequestParam(required = false) String totalAnnualUseFrom,
      @RequestParam(required = false) String totalAnnualUseTo) {
    return CsvExport.response(
        "range-tenures",
        csv -> rangeTenureSearchService.exportCsv(
            forestFileId,
            fileTypeCode,
            orgUnitNo,
            zone,
            clientName,
            clientNumber,
            clientLocnCode,
            fileClientType,
            fileStatus,
            mgmtUnitType,
            mgmtUnitId,
            issueDateFrom,
            issueDateTo,
            expiryDateFrom,
            expiryDateTo,
            provisionYear,
            authorizedUseFrom,
            authorizedUseTo,
            temporaryIncreaseFrom,
            temporaryIncreaseTo,
            billableNonUseFrom,
            billableNonUseTo,
            nonBillableNonUseFrom,
            nonBillableNonUseTo,
            totalAnnualUseFrom,
            totalAnnualUseTo,
            csv));
  }
}
