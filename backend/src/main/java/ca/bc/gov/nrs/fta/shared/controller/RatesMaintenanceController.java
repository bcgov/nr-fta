package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.RatesMaintenanceDto;
import ca.bc.gov.nrs.fta.shared.service.RatesMaintenanceService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA699 Rates &amp; Fees Maintenance, read side — {@code GET /api/fta/admin/rates?calendarYear=}
 * (one year's 12 values, {@code THE.FTA_699_RATEFEE.get}) and {@code GET
 * /api/fta/admin/rates/years} (the Calendar Year dropdown). {@code FTA_ADMIN} only, by the
 * URL-level rules.
 */
@RestController
@RequestMapping("/api/fta/admin/rates")
public class RatesMaintenanceController {

  private final RatesMaintenanceService ratesMaintenanceService;

  public RatesMaintenanceController(RatesMaintenanceService ratesMaintenanceService) {
    this.ratesMaintenanceService = ratesMaintenanceService;
  }

  @GetMapping("/years")
  public ResponseEntity<List<Integer>> years() {
    return ResponseEntity.ok(ratesMaintenanceService.years());
  }

  /** Legacy's Go: the Calendar Year is mandatory. */
  @GetMapping
  public ResponseEntity<RatesMaintenanceDto> findYear(
      @RequestParam(required = false) Integer calendarYear) {
    if (calendarYear == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar Year is mandatory.");
    }
    return ResponseEntity.ok(ratesMaintenanceService.findYear(calendarYear));
  }
}
