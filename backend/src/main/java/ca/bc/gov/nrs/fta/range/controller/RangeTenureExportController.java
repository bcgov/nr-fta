package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.service.RangeTenureExportCriteria;
import ca.bc.gov.nrs.fta.range.service.RangeTenureExportReport;
import ca.bc.gov.nrs.fta.range.service.RangeTenureExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The FTA001 Range Tenure Export (legacy's Export button), as an Excel workbook.
 * <ul>
 *   <li>{@code GET /api/fta/range-tenures/export} — the range tenure search's criteria;</li>
 *   <li>{@code GET /api/fta/range-tenures/{forestFileId}/export} — one file.</li>
 * </ul>
 * Read access follows the range tenure search and detail (ApiAuthorizationCustomizer): legacy
 * offered Export to whoever could open those screens.
 */
@RestController
@RequestMapping("/api/fta/range-tenures")
public class RangeTenureExportController {

  private final RangeTenureExportService exportService;

  public RangeTenureExportController(RangeTenureExportService exportService) {
    this.exportService = exportService;
  }

  @GetMapping("/export")
  public ResponseEntity<byte[]> exportSearch(
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
    return workbook(exportService.search(new RangeTenureExportCriteria(
        forestFileId, fileTypeCode, orgUnitNo, zone, clientName, clientNumber, clientLocnCode,
        fileClientType, fileStatus, mgmtUnitType, mgmtUnitId, issueDateFrom, issueDateTo,
        expiryDateFrom, expiryDateTo, provisionYear, authorizedUseFrom, authorizedUseTo,
        temporaryIncreaseFrom, temporaryIncreaseTo, billableNonUseFrom, billableNonUseTo,
        nonBillableNonUseFrom, nonBillableNonUseTo, totalAnnualUseFrom, totalAnnualUseTo)));
  }

  @GetMapping("/{forestFileId}/export")
  public ResponseEntity<byte[]> exportFile(@PathVariable String forestFileId) {
    return workbook(exportService.file(forestFileId));
  }

  private static ResponseEntity<byte[]> workbook(byte[] xlsx) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(RangeTenureExportReport.CONTENT_TYPE));
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(RangeTenureExportReport.FILENAME).build());
    return ResponseEntity.ok().headers(headers).body(xlsx);
  }
}
