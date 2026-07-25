package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import ca.bc.gov.nrs.fta.tenure.service.ArchiveTenuresWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Archive-tenures write API — {@code POST /api/fta/admin/archive-tenures}
 * (FTA640). Ports the {@code SAVE} action of the legacy {@code
 * THE.FTA_640_ArchiveTen} package: archives eligible expired grazing / hay
 * tenures for the given district/region and expiry year. Write access is
 * enforced to {@code FTA_ADMIN} by the URL-level authorization rules ({@code
 * ApiAuthorizationCustomizer}); the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/archive-tenures")
public class ArchiveTenuresWriteController {

  private final ArchiveTenuresWriteService archiveTenuresWriteService;

  public ArchiveTenuresWriteController(ArchiveTenuresWriteService archiveTenuresWriteService) {
    this.archiveTenuresWriteService = archiveTenuresWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Integer>> archive(
      @RequestBody ArchiveTenuresRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = archiveTenuresWriteService.archive(request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
