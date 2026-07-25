package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockActionRequest;
import ca.bc.gov.nrs.fta.tenure.service.CutblockActionWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cut-block action write API — {@code POST
 * /api/fta/cut-blocks/{blockId}/actions} (amend / suspend / re-label a cut
 * block, selected by the request {@code action}). Write access is enforced to
 * {@code FTA_ADMIN} by the URL-level authorization rules ({@code
 * ApiAuthorizationCustomizer}); the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/cut-blocks")
public class CutblockActionWriteController {

  private final CutblockActionWriteService cutblockActionWriteService;

  public CutblockActionWriteController(CutblockActionWriteService cutblockActionWriteService) {
    this.cutblockActionWriteService = cutblockActionWriteService;
  }

  @PostMapping("/{blockId}/actions")
  public ResponseEntity<Map<String, Object>> perform(
      @PathVariable String blockId,
      @RequestBody CutblockActionRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = cutblockActionWriteService.perform(blockId, request, userId);
    return ResponseEntity.status(HttpStatus.OK)
        .body(Map.of("blockId", blockId, "action", request.action(), "updated", updated));
  }
}
