package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkTransferRequest;
import ca.bc.gov.nrs.fta.mark.service.MarkTransferWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Timber-mark transfer write API — {@code POST /api/fta/marks/transfer}
 * (FTA240). Write access is enforced to {@code FTA_ADMIN} by the URL-level
 * authorization rules ({@code ApiAuthorizationCustomizer}); the audit user id
 * is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/marks/transfer")
public class MarkTransferWriteController {

  private final MarkTransferWriteService markTransferWriteService;

  public MarkTransferWriteController(MarkTransferWriteService markTransferWriteService) {
    this.markTransferWriteService = markTransferWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, String>> transfer(
      @RequestBody MarkTransferRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    String timberMark = markTransferWriteService.transfer(request, userId);
    return ResponseEntity.ok(Map.of("timberMark", timberMark));
  }
}
