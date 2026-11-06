package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.service.AptForestFileIdService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The licences a partition apportionment's commitment can name —
 * {@code GET /api/fta/apt/forest-file-ids?managementUnitId=&foaType=}
 * ({@code APT_FOREST_FILE_ID_GET}).
 */
@RestController
@RequestMapping("/api/fta/apt/forest-file-ids")
public class AptForestFileIdController {

  private final AptForestFileIdService forestFileIds;

  public AptForestFileIdController(AptForestFileIdService forestFileIds) {
    this.forestFileIds = forestFileIds;
  }

  @GetMapping
  public List<String> forestFileIds(@RequestParam long managementUnitId,
      @RequestParam String foaType) {
    return forestFileIds.forestFileIds(managementUnitId, foaType);
  }
}
