package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitSearchDto;
import ca.bc.gov.nrs.fta.range.service.RangeUnitSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range unit / pasture search API — {@code GET /api/fta/range-units}. Parameters
 * mirror the legacy {@code THE.FTA_006_RU_SRCH.mainline} search inputs.
 */
@RestController
@RequestMapping("/api/fta/range-units")
public class RangeUnitSearchController {

  private final RangeUnitSearchService rangeUnitSearchService;

  public RangeUnitSearchController(RangeUnitSearchService rangeUnitSearchService) {
    this.rangeUnitSearchService = rangeUnitSearchService;
  }

  @GetMapping
  public ResponseEntity<List<RangeUnitSearchDto>> search(
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String rangeUnitName,
      @RequestParam(required = false) String pastureName,
      @RequestParam(required = false) String rangeStatus) {
    return ResponseEntity.ok(
        rangeUnitSearchService.search(orgUnitNo, rangeUnitName, pastureName, rangeStatus));
  }
}
