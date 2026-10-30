package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryDtos.HarvestHistoryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure harvest history tab API (legacy FTA990, read-only):
 * {@code GET /api/fta/tenures/{forestFileId}/harvest-history} — the file's timber mark's
 * appraisal header and billing history; {@code GET /api/fta/timber-marks/{mark}/harvest-history}
 * — the same for one mark, as a cutting permit's tab reads it.
 */
@RestController
@RequestMapping("/api/fta")
public class HarvestHistoryController {

  private final HarvestHistoryService harvestHistoryService;

  public HarvestHistoryController(HarvestHistoryService harvestHistoryService) {
    this.harvestHistoryService = harvestHistoryService;
  }

  @GetMapping("/tenures/{forestFileId}/harvest-history")
  public HarvestHistoryDto get(@PathVariable String forestFileId) {
    return harvestHistoryService.get(forestFileId);
  }

  @GetMapping("/timber-marks/{timberMark}/harvest-history")
  public HarvestHistoryDto forTimberMark(@PathVariable String timberMark) {
    return harvestHistoryService.forTimberMark(timberMark);
  }
}
