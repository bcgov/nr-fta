package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.tenure.tab.details.ChangeMarkDesignateService.MarkDesignate;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA101 Change Mark Designate, from the tenure Details tab (FTA_ADMIN, by
 * {@code ApiAuthorizationCustomizer}):
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{id}/details/mark-designate?sourceFileId=} — Go: the source
 *       file's designate (this file's when blank) and the lock for Save;
 *   <li>{@code POST /api/fta/tenures/{id}/details/change-mark-designate} — Save Designate.
 * </ul>
 */
@RestController
public class ChangeMarkDesignateController {

  private final ChangeMarkDesignateService service;

  public ChangeMarkDesignateController(ChangeMarkDesignateService service) {
    this.service = service;
  }

  @GetMapping("/api/fta/tenures/{forestFileId}/details/mark-designate")
  public MarkDesignate get(
      @PathVariable String forestFileId,
      @RequestParam(required = false) String sourceFileId) {
    return service.get(forestFileId, sourceFileId);
  }

  /** Save Designate's body: the Source File and the revision count GET returned. */
  public record ChangeRequest(String sourceFileId, String revisionCount) {}

  @PostMapping("/api/fta/tenures/{forestFileId}/details/change-mark-designate")
  public ResponseEntity<Void> change(
      @PathVariable String forestFileId,
      @RequestBody ChangeRequest request,
      JwtAuthenticationToken principal) {
    service.save(forestFileId, request.sourceFileId(), request.revisionCount(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }
}
