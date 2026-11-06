package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptOrgUnitDto;
import ca.bc.gov.nrs.fta.apt.service.AptLookupService;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The code lists shared by the APT screens — {@code GET /api/fta/apt/lookups/*}. */
@RestController
@RequestMapping("/api/fta/apt/lookups")
public class AptLookupController {

  private final AptLookupService lookups;

  public AptLookupController(AptLookupService lookups) {
    this.lookups = lookups;
  }

  /** Regions ({@code level=R}), districts ({@code level=D}) or both (no level). */
  @GetMapping("/org-units")
  public List<AptOrgUnitDto> orgUnits(@RequestParam(required = false) String level) {
    return lookups.orgUnits(level);
  }

  @GetMapping("/management-unit-types")
  public List<CodeOptionDto> managementUnitTypes() {
    return lookups.managementUnitTypes();
  }

  @GetMapping("/management-unit-statuses")
  public List<CodeOptionDto> managementUnitStatuses() {
    return lookups.managementUnitStatuses();
  }

  @GetMapping("/foa-types")
  public List<CodeOptionDto> foaTypes(
      @RequestParam(required = false) String managementUnitType) {
    return lookups.foaTypes(managementUnitType);
  }

  @GetMapping("/commitment-types")
  public List<CodeOptionDto> commitmentTypes() {
    return lookups.commitmentTypes();
  }

  /** All partition types, or with both MU params, those that MU's partitions use. */
  @GetMapping("/partition-types")
  public List<CodeOptionDto> partitionTypes(
      @RequestParam(required = false) String managementUnitType,
      @RequestParam(required = false) String managementUnitNumber) {
    return lookups.partitionTypes(managementUnitType, managementUnitNumber);
  }
}
