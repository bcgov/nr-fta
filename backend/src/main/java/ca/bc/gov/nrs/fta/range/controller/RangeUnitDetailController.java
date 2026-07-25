package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitDetailDto;
import ca.bc.gov.nrs.fta.range.service.RangeUnitDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range unit detail API — {@code GET /api/fta/range-units/{unitId}}. Mirrors the
 * {@code GET} action of the legacy {@code THE.FTA_630_MN_RG_UN_PST.mainline},
 * returning the range-unit tombstone plus its pastures. Responds 404 when no
 * range unit matches the id.
 */
@RestController
@RequestMapping("/api/fta/range-units")
public class RangeUnitDetailController {

  private final RangeUnitDetailService rangeUnitDetailService;

  public RangeUnitDetailController(RangeUnitDetailService rangeUnitDetailService) {
    this.rangeUnitDetailService = rangeUnitDetailService;
  }

  @GetMapping("/{unitId}")
  public ResponseEntity<RangeUnitDetailDto> getRangeUnit(@PathVariable String unitId) {
    RangeUnitDetailDto detail = rangeUnitDetailService.getRangeUnitDetail(unitId);
    return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
  }
}
