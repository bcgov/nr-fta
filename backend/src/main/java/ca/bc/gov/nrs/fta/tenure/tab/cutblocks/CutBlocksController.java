package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockCreateRequest;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockCreated;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockDeleteRequest;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksCodeOption;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksResponse;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenure detail's Cut block tab — legacy FTA903 Cut Block List.
 *
 * <ul>
 *   <li>{@code GET  /api/fta/tenures/{forestFileId}/cut-blocks} — blocks, suspensions, rules
 *       and the permits a block may be added to;
 *   <li>{@code POST /api/fta/tenures/{forestFileId}/cut-blocks} — add a block (FTA904 add);
 *   <li>{@code DELETE /api/fta/tenures/{forestFileId}/cut-blocks/{cbSkey}} — delete a block,
 *       with a comment;
 *   <li>{@code GET  /api/fta/tenure-lookups/fire-harvesting-reasons} — the add dialog's list.
 * </ul>
 *
 * <p>Writes are FTA_ADMIN by the generic write rule.
 */
@RestController
@RequestMapping("/api/fta")
public class CutBlocksController {

  private final CutBlocksService service;
  private final CutBlocksWriteService writes;

  public CutBlocksController(CutBlocksService service, CutBlocksWriteService writes) {
    this.service = service;
    this.writes = writes;
  }

  @GetMapping("/tenures/{forestFileId}/cut-blocks")
  public CutBlocksResponse list(@PathVariable String forestFileId) {
    return service.tab(forestFileId);
  }

  @PostMapping("/tenures/{forestFileId}/cut-blocks")
  public ResponseEntity<CutBlockCreated> add(
      @PathVariable String forestFileId,
      @RequestBody CutBlockCreateRequest request,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
        writes.add(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @DeleteMapping("/tenures/{forestFileId}/cut-blocks/{cbSkey}")
  public ResponseEntity<Void> delete(
      @PathVariable String forestFileId,
      @PathVariable long cbSkey,
      @RequestBody(required = false) CutBlockDeleteRequest request,
      JwtAuthenticationToken principal) {
    writes.delete(forestFileId, cbSkey, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/tenure-lookups/fire-harvesting-reasons")
  public List<CutBlocksCodeOption> fireHarvestingReasons() {
    return service.fireHarvestingReasons();
  }
}
