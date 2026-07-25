package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.ApplicationAdjudicateRequest;
import ca.bc.gov.nrs.fta.tenure.service.ApplicationAdjudicateWriteService;
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
 * Tenure-application adjudication write API — {@code POST
 * /api/fta/applications/{esfId}/actions}. Ports the write path of the legacy
 * {@code THE.FTA_302_ADJUDCOMMENT} package. Write access is enforced to {@code
 * FTA_ADMIN} by the URL-level authorization rules ({@code
 * ApiAuthorizationCustomizer}); the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/applications/{esfId}/actions")
public class ApplicationAdjudicateWriteController {

  private final ApplicationAdjudicateWriteService adjudicateWriteService;

  public ApplicationAdjudicateWriteController(
      ApplicationAdjudicateWriteService adjudicateWriteService) {
    this.adjudicateWriteService = adjudicateWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Integer>> adjudicate(
      @PathVariable String esfId,
      @RequestBody ApplicationAdjudicateRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = adjudicateWriteService.adjudicate(esfId, request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
