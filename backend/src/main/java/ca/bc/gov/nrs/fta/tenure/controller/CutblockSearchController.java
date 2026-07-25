package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockSearchDto;
import ca.bc.gov.nrs.fta.tenure.service.CutblockSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cut-block search API — {@code GET /api/fta/cut-blocks}. Parameters mirror the
 * legacy {@code THE.FTA_003_CUTBLK_SRCH.mainline} / {@code get} search inputs.
 */
@RestController
@RequestMapping("/api/fta/cut-blocks")
public class CutblockSearchController {

  private final CutblockSearchService cutblockSearchService;

  public CutblockSearchController(CutblockSearchService cutblockSearchService) {
    this.cutblockSearchService = cutblockSearchService;
  }

  @GetMapping
  public ResponseEntity<List<CutblockSearchDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String cutBlockId,
      @RequestParam(required = false) String blockStatusSt,
      @RequestParam(required = false) String orgUnitNo,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String managedByFile,
      @RequestParam(required = false) String managedByCp,
      @RequestParam(required = false) String harvestStartDateFrom,
      @RequestParam(required = false) String harvestStartDateTo,
      @RequestParam(required = false) String districtAdminZone) {
    return ResponseEntity.ok(
        cutblockSearchService.search(
            forestFileId,
            cuttingPermitId,
            timberMark,
            cutBlockId,
            blockStatusSt,
            orgUnitNo,
            clientNumber,
            clientLocnCode,
            clientName,
            managedByFile,
            managedByCp,
            harvestStartDateFrom,
            harvestStartDateTo,
            districtAdminZone));
  }
}
