package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto;
import ca.bc.gov.nrs.fta.range.service.RangeTenureDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range tenure detail API — {@code GET /api/fta/range-tenures/{agreementId}}.
 * The path id is the range forest file id, mirroring the
 * {@code p_forest_file_id} input of the legacy
 * {@code THE.FTA_100RANGE_TENURE.mainline} (GET action). Returns the aggregated
 * detail, or {@code 404} when the file id is unknown.
 */
@RestController
@RequestMapping("/api/fta/range-tenures")
public class RangeTenureDetailController {

  private final RangeTenureDetailService rangeTenureDetailService;

  public RangeTenureDetailController(RangeTenureDetailService rangeTenureDetailService) {
    this.rangeTenureDetailService = rangeTenureDetailService;
  }

  @GetMapping("/{agreementId}")
  public ResponseEntity<RangeTenureDetailDto> getDetail(@PathVariable String agreementId) {
    RangeTenureDetailDto detail = rangeTenureDetailService.getDetail(agreementId);
    if (detail == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(detail);
  }
}
