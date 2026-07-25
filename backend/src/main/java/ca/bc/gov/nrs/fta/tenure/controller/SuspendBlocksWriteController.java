package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.SuspendBlocksRequest;
import ca.bc.gov.nrs.fta.tenure.service.SuspendBlocksWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suspend-blocks write API — {@code POST
 * /api/fta/cutting-permits/{cpId}/suspend-blocks} (suspend cut blocks on a
 * cutting permit; ports the {@code SUSPEND} action of the legacy {@code
 * THE.FTA_912_SUSPEND_PERMIT} package). Write access is enforced to {@code
 * FTA_ADMIN} by the URL-level authorization rules; the audit user id is taken
 * from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/cutting-permits")
public class SuspendBlocksWriteController {

  private final SuspendBlocksWriteService suspendBlocksWriteService;

  public SuspendBlocksWriteController(SuspendBlocksWriteService suspendBlocksWriteService) {
    this.suspendBlocksWriteService = suspendBlocksWriteService;
  }

  @PostMapping("/{cpId}/suspend-blocks")
  public ResponseEntity<Map<String, Integer>> suspend(
      @PathVariable String cpId,
      @RequestBody SuspendBlocksRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int suspended = suspendBlocksWriteService.suspend(cpId, request, userId);
    return ResponseEntity.ok(Map.of("suspended", suspended));
  }
}
