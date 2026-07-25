package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.UploadExhibitARequest;
import ca.bc.gov.nrs.fta.tenure.service.UploadExhibitAWriteService;
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
 * Exhibit A write API — {@code POST /api/fta/exhibit-a/{esfId}/upload} (upload
 * the Exhibit A image for a tenure application). Ports {@code
 * FTA_307_UPLOAD_EXHIBIT_A}. Write access is enforced to {@code FTA_ADMIN} by
 * the URL-level authorization rules; the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/exhibit-a")
public class UploadExhibitAWriteController {

  private final UploadExhibitAWriteService uploadExhibitAWriteService;

  public UploadExhibitAWriteController(UploadExhibitAWriteService uploadExhibitAWriteService) {
    this.uploadExhibitAWriteService = uploadExhibitAWriteService;
  }

  @PostMapping("/{esfId}/upload")
  public ResponseEntity<Map<String, Integer>> upload(
      @PathVariable String esfId,
      @RequestBody UploadExhibitARequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = uploadExhibitAWriteService.upload(esfId, request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
