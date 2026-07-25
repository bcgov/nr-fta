package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneDto;
import ca.bc.gov.nrs.fta.range.service.ManageZoneService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage Range Zone API — {@code GET /api/fta/admin/range-zones}. Parameters
 * mirror the legacy {@code THE.FTA_631_RANGE_ZONE.mainline} GET inputs.
 */
@RestController
@RequestMapping("/api/fta/admin/range-zones")
public class ManageZoneController {

  private final ManageZoneService manageZoneService;

  public ManageZoneController(ManageZoneService manageZoneService) {
    this.manageZoneService = manageZoneService;
  }

  @GetMapping
  public ResponseEntity<List<ManageZoneDto>> search(
      @RequestParam(required = false) String adminForestDistrictNo,
      @RequestParam(required = false) String rangeZoneCode) {
    return ResponseEntity.ok(manageZoneService.search(adminForestDistrictNo, rangeZoneCode));
  }
}
