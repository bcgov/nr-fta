package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  /** The current codes of each dropdown, keyed as {@link TenureDetailsRules} names them. */
  @GetMapping("/api/fta/tenure-lookups/tenure-details")
  public Map<String, List<CodeOptionDto>> options() {
    return lookups.options();
  }
}
