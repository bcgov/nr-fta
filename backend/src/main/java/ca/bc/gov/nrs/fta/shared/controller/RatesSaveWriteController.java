package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.service.RatesSaveWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Rates &amp; Fees Maintenance write API — {@code PUT /api/fta/admin/rates}
 * (save one calendar year of standard Range Billing rates, FTA699). Ports the {@code SAVE} action of
 * the legacy {@code THE.FTA_699_RATEFEE} package. The URL rules open it to {@code FTA_ADMIN}
 * and {@code FTA_RANGE_HEADQUARTERS_ADMIN}; rates apply province-wide, so a save is further limited to
 * an unscoped holder ({@link LoggedUserHelper#mayEditRangeRates}) and a district-scoped
 * administrator gets 403. The audit user id is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/rates")
public class RatesSaveWriteController {

  static final String SCOPED_NOT_ALLOWED =
      "Only a provincial administrator may save range rates.";

  private final RatesSaveWriteService ratesSaveWriteService;
  private final LoggedUserHelper auth;

  public RatesSaveWriteController(
      RatesSaveWriteService ratesSaveWriteService, LoggedUserHelper auth) {
    this.ratesSaveWriteService = ratesSaveWriteService;
    this.auth = auth;
  }

  @PutMapping
  public ResponseEntity<Map<String, Integer>> save(
      @RequestBody RatesSaveRequest request,
      JwtAuthenticationToken principal) {
    if (!auth.mayEditRangeRates()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, SCOPED_NOT_ALLOWED);
    }
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    int updated = ratesSaveWriteService.save(request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
