package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.MgmtUnitSearchDto;
import ca.bc.gov.nrs.fta.shared.service.MgmtUnitSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management-unit-type search API — {@code GET /api/fta/management-units}.
 * Parameters mirror the legacy
 * {@code THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE} code-list lookup (which
 * itself takes no filter inputs; the optional params narrow the returned list).
 */
@RestController
@RequestMapping("/api/fta/management-units")
public class MgmtUnitSearchController {

  private final MgmtUnitSearchService mgmtUnitSearchService;

  public MgmtUnitSearchController(MgmtUnitSearchService mgmtUnitSearchService) {
    this.mgmtUnitSearchService = mgmtUnitSearchService;
  }

  @GetMapping
  public ResponseEntity<List<MgmtUnitSearchDto>> search(
      @RequestParam(required = false) String mgmtUnitTypeCode,
      @RequestParam(required = false) String description) {
    return ResponseEntity.ok(mgmtUnitSearchService.search(mgmtUnitTypeCode, description));
  }
}
