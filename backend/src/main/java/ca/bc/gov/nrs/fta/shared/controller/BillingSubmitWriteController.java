package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.BillingSubmitRequest;
import ca.bc.gov.nrs.fta.shared.service.BillingSubmitWriteService;
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
 * Billing submission write API — {@code POST /api/fta/admin/billing} (queue a
 * Range Billing invoicing request, porting FTA690 {@code SUBMIT}). Write access
 * is enforced to {@code FTA_ADMIN} by the URL-level authorization rules; the
 * submit / audit user id is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/billing")
public class BillingSubmitWriteController {

  private final BillingSubmitWriteService billingSubmitWriteService;

  public BillingSubmitWriteController(BillingSubmitWriteService billingSubmitWriteService) {
    this.billingSubmitWriteService = billingSubmitWriteService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Integer>> submit(
      @RequestBody BillingSubmitRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int submitted = billingSubmitWriteService.submit(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("submitted", submitted));
  }
}
