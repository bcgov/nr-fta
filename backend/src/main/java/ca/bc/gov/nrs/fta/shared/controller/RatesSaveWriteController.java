package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.service.RatesSaveWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rates &amp; Fees Maintenance write API — {@code PUT /api/fta/admin/rates}
 * (save standard Range Billing rates, FTA699). Ports the {@code SAVE} action of
 * the legacy {@code THE.FTA_699_RATEFEE} package. Write access is enforced to
 * {@code FTA_ADMIN} by the URL-level authorization rules; the audit user id is
 * taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/rates")
public class RatesSaveWriteController {

  private final RatesSaveWriteService ratesSaveWriteService;

  public RatesSaveWriteController(RatesSaveWriteService ratesSaveWriteService) {
    this.ratesSaveWriteService = ratesSaveWriteService;
  }

  @PutMapping
  public ResponseEntity<Map<String, Integer>> save(
      @RequestBody RatesSaveRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = ratesSaveWriteService.save(request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
