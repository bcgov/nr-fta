package ca.bc.gov.nrs.fta.tenure.suspension;

import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.BlockSuspension;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionCreateRequest;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionDeleteRequest;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionUpdateRequest;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A cut block's suspension, legacy FTA914. Writes are FTA_ADMIN or
 * FTA_HARVEST_AUTH_SENIOR_ADMIN by the URL rules ({@code ApiAuthorizationCustomizer}).
 *
 * <ul>
 *   <li>{@code GET /api/fta/cut-blocks/{cbSkey}/suspensions} — the block and its suspension;
 *   <li>{@code POST /api/fta/cut-blocks/{cbSkey}/suspensions} — Suspend Block;
 *   <li>{@code PUT .../suspensions/{id}} — Save Suspension or End Suspension, by {@code action};
 *   <li>{@code DELETE .../suspensions/{id}} — Delete Suspension.
 * </ul>
 */
@RestController
@RequestMapping("/api/fta/cut-blocks/{cbSkey}/suspensions")
public class BlockSuspensionController {

  private final BlockSuspensionService service;

  public BlockSuspensionController(BlockSuspensionService service) {
    this.service = service;
  }

  @GetMapping
  public BlockSuspension find(@PathVariable long cbSkey) {
    return service.find(cbSkey);
  }

  @PostMapping
  public ResponseEntity<Void> suspend(
      @PathVariable long cbSkey,
      @RequestBody SuspensionCreateRequest request,
      JwtAuthenticationToken principal) {
    service.suspend(cbSkey, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PutMapping("/{suspensionId}")
  public ResponseEntity<Void> update(
      @PathVariable long cbSkey,
      @PathVariable long suspensionId,
      @RequestBody SuspensionUpdateRequest request,
      JwtAuthenticationToken principal) {
    service.update(cbSkey, suspensionId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{suspensionId}")
  public ResponseEntity<Void> delete(
      @PathVariable long cbSkey,
      @PathVariable long suspensionId,
      @RequestBody(required = false) SuspensionDeleteRequest request,
      JwtAuthenticationToken principal) {
    service.delete(cbSkey, suspensionId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }
}
