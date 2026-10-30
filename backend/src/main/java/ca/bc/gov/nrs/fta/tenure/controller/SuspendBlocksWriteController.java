package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.SuspendBlocksRequest;
import ca.bc.gov.nrs.fta.tenure.service.SuspendBlocksWriteService;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.PermitSuspension;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suspending a permit's cut blocks, legacy FTA912: {@code GET} and {@code POST
 * /api/fta/cutting-permits/{cpId}/suspend-blocks}. The POST is FTA_ADMIN or
 * FTA_HARVEST_AUTH_SENIOR_ADMIN by the URL rules; the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/cutting-permits")
public class SuspendBlocksWriteController {

  private final SuspendBlocksWriteService suspendBlocksWriteService;

  public SuspendBlocksWriteController(SuspendBlocksWriteService suspendBlocksWriteService) {
    this.suspendBlocksWriteService = suspendBlocksWriteService;
  }

  @GetMapping("/{cpId}/suspend-blocks")
  public PermitSuspension find(@PathVariable String cpId, @RequestParam String forestFileId) {
    return suspendBlocksWriteService.find(cpId, forestFileId);
  }

  @PostMapping("/{cpId}/suspend-blocks")
  public ResponseEntity<Map<String, Integer>> suspend(
      @PathVariable String cpId,
      @RequestBody SuspendBlocksRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    int suspended = suspendBlocksWriteService.suspend(cpId, request, userId);
    return ResponseEntity.ok(Map.of("suspended", suspended));
  }
}
