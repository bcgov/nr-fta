package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchDto;
import ca.bc.gov.nrs.fta.tenure.service.HarvestingSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Harvesting authority search API — {@code GET /api/fta/harvesting-authorities}.
 * Parameters mirror the searchable columns of the legacy
 * {@code THE.FTA_HVA_SEARCH} result record.
 */
@RestController
@RequestMapping("/api/fta/harvesting-authorities")
public class HarvestingSearchController {

  private final HarvestingSearchService harvestingSearchService;

  public HarvestingSearchController(HarvestingSearchService harvestingSearchService) {
    this.harvestingSearchService = harvestingSearchService;
  }

  @GetMapping
  public ResponseEntity<List<HarvestingSearchDto>> search(
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String orgUnitCode) {
    return ResponseEntity.ok(
        harvestingSearchService.search(
            cuttingPermitId, timberMark, forestFileId, clientName, orgUnitCode));
  }
}
