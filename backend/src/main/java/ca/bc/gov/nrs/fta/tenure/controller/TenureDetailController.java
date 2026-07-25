package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.TenureDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.TenureDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure detail API — {@code GET /api/fta/tenures/{forestFileId}}. Returns the
 * single richer detail record for one file, mirroring the GET action of the
 * legacy {@code THE.FTA_100_TENURE.mainline} procedure; 404 when the file does
 * not exist.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TenureDetailController {

  private final TenureDetailService tenureDetailService;

  public TenureDetailController(TenureDetailService tenureDetailService) {
    this.tenureDetailService = tenureDetailService;
  }

  @GetMapping("/{forestFileId}")
  public ResponseEntity<TenureDetailDto> getByForestFileId(
      @PathVariable String forestFileId) {
    TenureDetailDto detail = tenureDetailService.findByForestFileId(forestFileId);
    return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
  }
}
