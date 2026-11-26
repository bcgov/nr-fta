package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.OrgUnitMaintDto;
import ca.bc.gov.nrs.fta.shared.dto.OrgUnitMaintRequest;
import ca.bc.gov.nrs.fta.shared.dto.UserOrgUnitDefaultDto;
import ca.bc.gov.nrs.fta.shared.service.OrgUnitMaintService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Org Unit Maintenance (legacy SIL99) and the default it sets. The user is always the one
 * signed in, taken from the token.
 *
 * <ul>
 *   <li>{@code GET /api/fta/admin/org-unit-default} — the screen: the user's org units and
 *       which is their default</li>
 *   <li>{@code POST /api/fta/admin/org-unit-default} — Save</li>
 *   <li>{@code GET /api/fta/org-unit-default} — the user's default, for the screens that
 *       default their district to it; open to every role that reads FTA</li>
 * </ul>
 */
@RestController
public class OrgUnitMaintController {

  private final OrgUnitMaintService service;

  public OrgUnitMaintController(OrgUnitMaintService service) {
    this.service = service;
  }

  @GetMapping("/api/fta/admin/org-unit-default")
  public ResponseEntity<OrgUnitMaintDto> view(JwtAuthenticationToken principal) {
    return ResponseEntity.ok(service.view(JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PostMapping("/api/fta/admin/org-unit-default")
  public ResponseEntity<OrgUnitMaintDto> save(
      @RequestBody OrgUnitMaintRequest request, JwtAuthenticationToken principal) {
    String orgUnitNo = request == null ? null : request.orgUnitNo();
    return ResponseEntity.ok(service.save(orgUnitNo, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @GetMapping("/api/fta/org-unit-default")
  public ResponseEntity<UserOrgUnitDefaultDto> effectiveDefault(
      JwtAuthenticationToken principal) {
    return ResponseEntity.ok(
        service.effectiveDefault(JwtPrincipalUtil.getAuditUserId(principal)));
  }
}
