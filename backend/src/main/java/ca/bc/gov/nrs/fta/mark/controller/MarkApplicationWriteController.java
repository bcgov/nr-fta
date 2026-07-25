package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkApplicationRequest;
import ca.bc.gov.nrs.fta.mark.service.MarkApplicationWriteService;
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
 * Private mark write API — {@code POST /api/fta/marks} (submit a new private
 * mark application). Write access is enforced to {@code FTA_ADMIN} by the
 * URL-level authorization rules ({@code ApiAuthorizationCustomizer}); the audit
 * user id is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkApplicationWriteController {

  private final MarkApplicationWriteService markApplicationWriteService;

  public MarkApplicationWriteController(MarkApplicationWriteService markApplicationWriteService) {
    this.markApplicationWriteService = markApplicationWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, String>> create(
      @RequestBody MarkApplicationRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    String markNumber = markApplicationWriteService.create(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("markNumber", markNumber));
  }
}
