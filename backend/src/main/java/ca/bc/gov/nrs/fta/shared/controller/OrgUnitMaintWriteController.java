package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.OrgUnitMaintRequest;
import ca.bc.gov.nrs.fta.shared.service.OrgUnitMaintWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Org Unit Maintenance write API (SIL99) — {@code POST
 * /api/fta/admin/org-unit-default} sets the authenticated user's default org
 * unit. The audit user id is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/org-unit-default")
public class OrgUnitMaintWriteController {

  private final OrgUnitMaintWriteService orgUnitMaintWriteService;

  public OrgUnitMaintWriteController(OrgUnitMaintWriteService orgUnitMaintWriteService) {
    this.orgUnitMaintWriteService = orgUnitMaintWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> setDefaultOrgUnit(
      @RequestBody OrgUnitMaintRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = orgUnitMaintWriteService.setDefaultOrgUnit(request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
