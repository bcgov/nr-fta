package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.RatesMaintenanceDto;
import ca.bc.gov.nrs.fta.shared.service.RatesMaintenanceService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standard Range Billing rates/fees maintenance API —
 * {@code GET /api/fta/admin/rates}. Parameters mirror the legacy
 * {@code THE.FTA_699_RATEFEE.mainline} GET inputs.
 */
@RestController
@RequestMapping("/api/fta/admin/rates")
public class RatesMaintenanceController {

  private final RatesMaintenanceService ratesMaintenanceService;

  public RatesMaintenanceController(RatesMaintenanceService ratesMaintenanceService) {
    this.ratesMaintenanceService = ratesMaintenanceService;
  }

  @GetMapping
  public ResponseEntity<List<RatesMaintenanceDto>> findRates(
      @RequestParam(required = false) Integer calendarYear) {
    return ResponseEntity.ok(ratesMaintenanceService.findRates(calendarYear));
  }
}
