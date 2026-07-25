package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CreateTenureRequest;
import ca.bc.gov.nrs.fta.tenure.service.TenureWriteService;
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
 * Tenure write API — {@code POST /api/fta/tenures} (create a new forest file).
 * Write access is enforced to {@code FTA_ADMIN} by the URL-level authorization
 * rules ({@code ApiAuthorizationCustomizer}); the audit user id is taken from
 * the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TenureWriteController {

  private final TenureWriteService tenureWriteService;

  public TenureWriteController(TenureWriteService tenureWriteService) {
    this.tenureWriteService = tenureWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, String>> create(
      @RequestBody CreateTenureRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    String forestFileId = tenureWriteService.create(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("forestFileId", forestFileId));
  }
}
