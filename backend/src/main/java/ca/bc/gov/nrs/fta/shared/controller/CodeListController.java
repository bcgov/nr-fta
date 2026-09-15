package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.service.CodeListService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Code lists for the search-screen dropdowns — {@code GET /api/fta/code-lists/*}.
 *
 * <p>One endpoint per list rather than a single parameterised one, so a caller
 * cannot name an arbitrary table and each list can diverge later (org units
 * already do) without changing the contract.
 */
@RestController
@RequestMapping("/api/fta/code-lists")
public class CodeListController {

  private final CodeListService codeListService;

  public CodeListController(CodeListService codeListService) {
    this.codeListService = codeListService;
  }

  @GetMapping("/org-units")
  public ResponseEntity<List<CodeOptionDto>> orgUnits() {
    return ResponseEntity.ok(codeListService.orgUnits());
  }

  @GetMapping("/file-types")
  public ResponseEntity<List<CodeOptionDto>> fileTypes() {
    return ResponseEntity.ok(codeListService.fileTypes());
  }

  @GetMapping("/file-statuses")
  public ResponseEntity<List<CodeOptionDto>> fileStatuses() {
    return ResponseEntity.ok(codeListService.fileStatuses());
  }

  @GetMapping("/file-client-types")
  public ResponseEntity<List<CodeOptionDto>> fileClientTypes() {
    return ResponseEntity.ok(codeListService.fileClientTypes());
  }

  @GetMapping("/file-sources")
  public ResponseEntity<List<CodeOptionDto>> fileSources() {
    return ResponseEntity.ok(codeListService.fileSources());
  }

  @GetMapping("/map-notation-types")
  public ResponseEntity<List<CodeOptionDto>> mapNotationTypes() {
    return ResponseEntity.ok(codeListService.mapNotationTypes());
  }

  @GetMapping("/range-unit-statuses")
  public ResponseEntity<List<CodeOptionDto>> rangeUnitStatuses() {
    return ResponseEntity.ok(codeListService.rangeUnitStatuses());
  }

  /**
   * Range zones, optionally narrowed to one district. The only code list that
   * takes a filter — the range tenure screen reloads it when the org unit
   * changes.
   */
  @GetMapping("/range-zones")
  public ResponseEntity<List<CodeOptionDto>> rangeZones(
      @RequestParam(required = false) String adminDistrictNo) {
    return ResponseEntity.ok(codeListService.rangeZones(adminDistrictNo));
  }

  @GetMapping("/land-districts")
  public ResponseEntity<List<CodeOptionDto>> landDistricts() {
    return ResponseEntity.ok(codeListService.landDistricts());
  }

  @GetMapping("/primary-ids")
  public ResponseEntity<List<CodeOptionDto>> primaryIds() {
    return ResponseEntity.ok(codeListService.primaryIds());
  }

  @GetMapping("/salvage-types")
  public ResponseEntity<List<CodeOptionDto>> salvageTypes() {
    return ResponseEntity.ok(codeListService.salvageTypes());
  }

  @GetMapping("/harvest-auth-statuses")
  public ResponseEntity<List<CodeOptionDto>> harvestAuthStatuses() {
    return ResponseEntity.ok(codeListService.harvestAuthStatuses());
  }

  @GetMapping("/block-statuses")
  public ResponseEntity<List<CodeOptionDto>> blockStatuses() {
    return ResponseEntity.ok(codeListService.blockStatuses());
  }

  @GetMapping("/private-mark-statuses")
  public ResponseEntity<List<CodeOptionDto>> privateMarkStatuses() {
    return ResponseEntity.ok(codeListService.privateMarkStatuses());
  }

  @GetMapping("/licence-to-cut-codes")
  public ResponseEntity<List<CodeOptionDto>> licenceToCutCodes() {
    return ResponseEntity.ok(codeListService.licenceToCutCodes());
  }

  @GetMapping("/harvest-auth-client-types")
  public ResponseEntity<List<CodeOptionDto>> harvestAuthClientTypes() {
    return ResponseEntity.ok(codeListService.harvestAuthClientTypes());
  }

  @GetMapping("/recreation-file-statuses")
  public ResponseEntity<List<CodeOptionDto>> recreationFileStatuses() {
    return ResponseEntity.ok(codeListService.recreationFileStatuses());
  }

  @GetMapping("/recreation-project-types")
  public ResponseEntity<List<CodeOptionDto>> recreationProjectTypes() {
    return ResponseEntity.ok(codeListService.recreationProjectTypes());
  }

  @GetMapping("/recreation-risk-ratings")
  public ResponseEntity<List<CodeOptionDto>> recreationRiskRatings() {
    return ResponseEntity.ok(codeListService.recreationRiskRatings());
  }

  @GetMapping("/recreation-control-access-types")
  public ResponseEntity<List<CodeOptionDto>> recreationControlAccessTypes() {
    return ResponseEntity.ok(codeListService.recreationControlAccessTypes());
  }

  @GetMapping("/recreation-maintain-standards")
  public ResponseEntity<List<CodeOptionDto>> recreationMaintainStandards() {
    return ResponseEntity.ok(codeListService.recreationMaintainStandards());
  }

  @GetMapping("/recreation-districts")
  public ResponseEntity<List<CodeOptionDto>> recreationDistricts() {
    return ResponseEntity.ok(codeListService.recreationDistricts());
  }
}
