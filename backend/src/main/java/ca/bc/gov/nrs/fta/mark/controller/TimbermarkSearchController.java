package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchCriteria;
import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchDto;
import ca.bc.gov.nrs.fta.mark.service.TimbermarkSearchService;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timber-mark search API — {@code GET /api/fta/timber-marks}. Parameters mirror
 * the legacy {@code THE.FTA_002_MARK_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/timber-marks")
public class TimbermarkSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final TimbermarkSearchService timbermarkSearchService;

  public TimbermarkSearchController(TimbermarkSearchService timbermarkSearchService) {
    this.timbermarkSearchService = timbermarkSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<TimbermarkSearchDto>> search(
      @RequestParam(required = false) String adminOrgUnitNo,
      @RequestParam(required = false) String districtAdminZone,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String markStatusSt,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String fileClientType,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String salvageTypeCode,
      @RequestParam(required = false) String certificate,
      @RequestParam(required = false) String landDistrict,
      @RequestParam(required = false) String primaryId,
      @RequestParam(required = false) String primaryDetail,
      @RequestParam(required = false) String privateMarkOnlyInd,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    TimbermarkSearchCriteria criteria = new TimbermarkSearchCriteria(
        adminOrgUnitNo, districtAdminZone, forestFileId, cuttingPermitId, timberMark,
        fileTypeCode, markStatusSt, clientNumber, clientLocnCode, clientName, fileClientType,
        mgmtUnitType, mgmtUnitId, issueDateFrom, issueDateTo, expiryDateFrom, expiryDateTo,
        salvageTypeCode, certificate, landDistrict, primaryId, primaryDetail,
        privateMarkOnlyInd, sortBy);

    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(timbermarkSearchService.search(criteria, safePage, safeSize));
  }
}
