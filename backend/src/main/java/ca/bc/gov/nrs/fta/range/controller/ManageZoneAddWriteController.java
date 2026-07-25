package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import ca.bc.gov.nrs.fta.range.service.ManageZoneAddWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range-zone write API — {@code POST /api/fta/admin/range-zones} (save a range
 * zone: insert or update, porting the {@code SAVE} path of the legacy
 * {@code THE.FTA_631_RANGE_ZONE} package). Write access is enforced to
 * {@code FTA_ADMIN} by the URL-level authorization rules; the audit user id is
 * taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/range-zones")
public class ManageZoneAddWriteController {

  private final ManageZoneAddWriteService manageZoneAddWriteService;

  public ManageZoneAddWriteController(ManageZoneAddWriteService manageZoneAddWriteService) {
    this.manageZoneAddWriteService = manageZoneAddWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> save(
      @RequestBody ManageZoneAddRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = manageZoneAddWriteService.save(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of("rangeZoneCode", request.rangeZoneCode(), "updated", updated));
  }
}
