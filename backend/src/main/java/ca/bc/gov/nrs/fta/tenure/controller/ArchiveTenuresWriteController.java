package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresSummaryDto;
import ca.bc.gov.nrs.fta.tenure.service.ArchiveTenuresWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA640 Archive Tenures — {@code /api/fta/admin/archive-tenures}. Ports
 * {@code THE.FTA_640_ARCHIVETEN}: GET counts the active grazing and hay tenures
 * that expired in a year, per district; POST archives exactly those. Access is
 * {@code FTA_ADMIN} by the URL-level authorization rules
 * ({@code ApiAuthorizationCustomizer}); the audit user id is taken from the JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/archive-tenures")
public class ArchiveTenuresWriteController {

  private final ArchiveTenuresWriteService archiveTenuresWriteService;

  public ArchiveTenuresWriteController(ArchiveTenuresWriteService archiveTenuresWriteService) {
    this.archiveTenuresWriteService = archiveTenuresWriteService;
  }

  @GetMapping
  public ResponseEntity<List<ArchiveTenuresSummaryDto>> summary(
      @RequestParam(required = false) Long regionNo,
      @RequestParam(required = false) Long districtNo,
      @RequestParam(required = false) Integer expiryYear) {
    return ResponseEntity.ok(archiveTenuresWriteService.summary(
        new ArchiveTenuresRequest(regionNo, districtNo, expiryYear)));
  }

  @PostMapping
  public ResponseEntity<Map<String, Integer>> archive(
      @RequestBody ArchiveTenuresRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    int updated = archiveTenuresWriteService.archive(request, userId);
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}
