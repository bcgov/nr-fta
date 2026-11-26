package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenure page's Details tab — legacy FTA100 (Tenure).
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{id}/details} — everything the screen shows, with the edit
 *       rules;
 *   <li>{@code PUT /api/fta/tenures/{id}/details} — Save (FTA_ADMIN, by the generic write
 *       rule);
 *   <li>{@code POST /api/fta/tenures/{id}/details/delete} — Delete the file;
 *   <li>{@code POST /api/fta/tenures/{id}/details/retire} — Retire the file;
 *   <li>{@code POST /api/fta/tenures/{id}/details/assign-mark-designate} — Assign Mark
 *       Designate (these three FTA_ADMIN, sent the GET's {@code revisions});
 *   <li>{@code GET /api/fta/tenure-lookups/tenure-details} — the edit form's dropdown lists.
 * </ul>
 */
@RestController
public class TenureDetailsController {

  private final TenureDetailsService service;
  private final TenureDetailsLookups lookups;

  public TenureDetailsController(TenureDetailsService service, TenureDetailsLookups lookups) {
    this.service = service;
    this.lookups = lookups;
  }

  @GetMapping("/api/fta/tenures/{forestFileId}/details")
  public TenureDetailsDto get(@PathVariable String forestFileId) {
    return service.get(forestFileId);
  }

  @PutMapping("/api/fta/tenures/{forestFileId}/details")
  public TenureDetailsSaveResult update(
      @PathVariable String forestFileId,
      @RequestBody TenureDetailsUpdateRequest request,
      JwtAuthenticationToken principal) {
    return service.update(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** Body of Delete and Retire: the GET's {@code revisions}, unchanged (the lock). */
  public record ActionRequest(Map<String, String> revisions) {}

  @PostMapping("/api/fta/tenures/{forestFileId}/details/delete")
  public ResponseEntity<Void> delete(
      @PathVariable String forestFileId,
      @RequestBody ActionRequest request,
      JwtAuthenticationToken principal) {
    service.delete(forestFileId, request.revisions(), JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/fta/tenures/{forestFileId}/details/retire")
  public ResponseEntity<Void> retire(
      @PathVariable String forestFileId,
      @RequestBody ActionRequest request,
      JwtAuthenticationToken principal) {
    service.retire(forestFileId, request.revisions(), JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/fta/tenures/{forestFileId}/details/assign-mark-designate")
  public ResponseEntity<Void> assignMarkDesignate(
      @PathVariable String forestFileId,
      @RequestBody ActionRequest request,
      JwtAuthenticationToken principal) {
    service.assignMarkDesignate(forestFileId, request.revisions(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** The current codes of each dropdown, keyed as {@link TenureDetailsRules} names them. */
  @GetMapping("/api/fta/tenure-lookups/tenure-details")
  public Map<String, List<CodeOptionDto>> options() {
    return lookups.options();
  }
}
