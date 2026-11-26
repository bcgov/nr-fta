package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlockRevisionRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlocksResponse;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenure detail's TL blocks tab — legacy FTA980 (TL Block Summary): the list, Retire and
 * Un-Retire, the only actions legacy's screen offers. Reads are open to every role that can
 * open the tenure; writes are FTA_ADMIN by the generic write rule.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TlBlockController {

  private final TlBlockService service;

  public TlBlockController(TlBlockService service) {
    this.service = service;
  }

  /** {@code GET /api/fta/tenures/{forestFileId}/tl-blocks} — blocks, totals and rules. */
  @GetMapping("/{forestFileId}/tl-blocks")
  public TlBlocksResponse list(@PathVariable String forestFileId) {
    return service.get(forestFileId);
  }

  /** {@code POST …/tl-blocks/{tlBlockId}/retire} — {@code {revisionCount}}. */
  @PostMapping("/{forestFileId}/tl-blocks/{tlBlockId}/retire")
  public ResponseEntity<Void> retire(
      @PathVariable String forestFileId,
      @PathVariable String tlBlockId,
      @RequestBody TlBlockRevisionRequest request,
      JwtAuthenticationToken principal) {
    service.retire(
        forestFileId, tlBlockId, request.revisionCount(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** {@code POST …/tl-blocks/{tlBlockId}/unretire} — {@code {revisionCount}}. */
  @PostMapping("/{forestFileId}/tl-blocks/{tlBlockId}/unretire")
  public ResponseEntity<Void> unretire(
      @PathVariable String forestFileId,
      @PathVariable String tlBlockId,
      @RequestBody TlBlockRevisionRequest request,
      JwtAuthenticationToken principal) {
    service.unretire(
        forestFileId, tlBlockId, request.revisionCount(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }
}
