package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CutBlockEditRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockRelabelRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockSavedDto;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.CutBlockWriteService;
import ca.bc.gov.nrs.fta.tenure.service.CutblockDetailService;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksCodeOption;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cut Block detail API (legacy FTA904, FTA231).
 *
 * <ul>
 *   <li>{@code GET /api/fta/cut-blocks/{blockId}?forestFileId=&cuttingPermitId=} — the block with
 *       that label on the file; a label repeats across files, so the file is required, and the
 *       permit is needed where it repeats within one;
 *   <li>{@code PUT /api/fta/cut-blocks/{cbSkey}} — FTA904's Save;
 *   <li>{@code POST /api/fta/cut-blocks/{cbSkey}/relabel} — FTA231's Save;
 *   <li>{@code GET /api/fta/tenure-lookups/cut-regulation-codes} and
 *       {@code /reforest-declare-types} — the edit dialog's lists.
 * </ul>
 *
 * <p>Who may call which is {@code ApiAuthorizationCustomizer}; the block's own rules are
 * {@code CutBlockRules}. The audit user id is taken from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta")
public class CutblockDetailController {

  private final CutblockDetailService cutblockDetailService;
  private final CutBlockWriteService cutBlockWriteService;

  public CutblockDetailController(
      CutblockDetailService cutblockDetailService, CutBlockWriteService cutBlockWriteService) {
    this.cutblockDetailService = cutblockDetailService;
    this.cutBlockWriteService = cutBlockWriteService;
  }

  @GetMapping("/cut-blocks/{blockId}")
  public ResponseEntity<CutblockDetailDto> getDetail(
      @PathVariable String blockId,
      @RequestParam String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      JwtAuthenticationToken principal) {
    return cutblockDetailService.find(blockId, forestFileId, cuttingPermitId, principal)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/cut-blocks/{cbSkey}")
  public CutBlockSavedDto save(
      @PathVariable long cbSkey,
      @RequestBody CutBlockEditRequest request,
      JwtAuthenticationToken principal) {
    return cutBlockWriteService.save(
        cbSkey, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/cut-blocks/{cbSkey}/relabel")
  public CutBlockSavedDto relabel(
      @PathVariable long cbSkey,
      @RequestBody CutBlockRelabelRequest request,
      JwtAuthenticationToken principal) {
    return cutBlockWriteService.relabel(
        cbSkey, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @GetMapping("/tenure-lookups/cut-regulation-codes")
  public List<CutBlocksCodeOption> cutRegulationCodes() {
    return cutblockDetailService.cutRegulationCodes();
  }

  @GetMapping("/tenure-lookups/reforest-declare-types")
  public List<CutBlocksCodeOption> reforestDeclareTypes() {
    return cutblockDetailService.reforestDeclareTypes();
  }
}
