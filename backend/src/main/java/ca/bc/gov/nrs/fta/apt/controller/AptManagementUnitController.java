package ca.bc.gov.nrs.fta.apt.controller;

import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitRules.requirePositiveId;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitSearchCriteria;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitUnassignedDto;
import ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * APT2's management unit API (APTM1100 search, APTM1200 create/edit, APTM1000 history) under
 * {@code /api/fta/apt}. Reads are open to every APT reader; writes need an APT editor, which
 * {@code ApiAuthorizationCustomizer} enforces by path and method. The audit user is always the
 * token's. The unit id in a path must be a positive integer (400 otherwise), as APT2 checks.
 */
@RestController
@RequestMapping("/api/fta/apt")
public class AptManagementUnitController {

  private final AptManagementUnitService units;

  public AptManagementUnitController(AptManagementUnitService units) {
    this.units = units;
  }

  /** {@code APT_MANAGEMENT_UNITS_GET}; every filter optional. */
  @GetMapping("/management-units")
  public List<AptManagementUnitDto> search(
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) String managementUnitType,
      @RequestParam(required = false) String managementUnitNumber,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate effectiveDate,
      @RequestParam(required = false) Long orgUnitNumber,
      @RequestParam(required = false) String orgUnitName,
      @RequestParam(required = false) String orcsFileNumber,
      @RequestParam(required = false) Long sourceManagementUnitId) {
    return units.search(new AptManagementUnitSearchCriteria(id, managementUnitType,
        managementUnitNumber, status, effectiveDate, orgUnitNumber, orgUnitName, orcsFileNumber,
        sourceManagementUnitId));
  }

  @GetMapping("/management-units/{id}")
  public AptManagementUnitDto get(@PathVariable Long id) {
    requirePositiveId(id);
    return units.get(id);
  }

  @PostMapping("/management-units")
  public ResponseEntity<AptManagementUnitDto> create(@RequestBody AptManagementUnitRequest body,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(units.create(body, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/management-units/{id}")
  public AptManagementUnitDto update(@PathVariable Long id,
      @RequestBody AptManagementUnitRequest body, JwtAuthenticationToken principal) {
    requirePositiveId(id);
    return units.update(id, body, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** The unit's AAC determinations, newest first; {@code historyId} narrows to one. */
  @GetMapping("/management-units/{id}/history")
  public List<AptManagementUnitHistoryDto> history(@PathVariable Long id,
      @RequestParam(required = false) Long historyId) {
    requirePositiveId(id);
    return units.history(id, historyId);
  }

  @PostMapping("/management-units/{id}/history")
  public ResponseEntity<AptManagementUnitHistoryDto> createHistory(@PathVariable Long id,
      @RequestBody AptManagementUnitHistoryRequest body, JwtAuthenticationToken principal) {
    requirePositiveId(id);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(units.createHistory(id, body, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/management-units/{id}/history/{historyId}")
  public AptManagementUnitHistoryDto updateHistory(@PathVariable Long id,
      @PathVariable Long historyId, @RequestBody AptManagementUnitHistoryRequest body,
      JwtAuthenticationToken principal) {
    requirePositiveId(id);
    return units.updateHistory(id, historyId, body, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** Code-table management units not yet in APT: what a new one is picked from. */
  @GetMapping("/unassigned-management-units")
  public List<AptManagementUnitUnassignedDto> unassigned() {
    return units.unassigned();
  }
}
