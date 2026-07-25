package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.CutblockDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cut Block detail API — {@code GET /api/fta/cut-blocks/{blockId}}. Mirrors the
 * legacy {@code THE.FTA_904_CUTBLKDETAIL.GET} lookup: the block id is the path
 * key, with optional {@code forestFileId} / {@code cuttingPermitId} filters to
 * disambiguate when the same block id exists across files/permits.
 */
@RestController
@RequestMapping("/api/fta/cut-blocks")
public class CutblockDetailController {

  private final CutblockDetailService cutblockDetailService;

  public CutblockDetailController(CutblockDetailService cutblockDetailService) {
    this.cutblockDetailService = cutblockDetailService;
  }

  @GetMapping("/{blockId}")
  public ResponseEntity<CutblockDetailDto> getDetail(
      @PathVariable String blockId,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId) {
    return cutblockDetailService.find(blockId, forestFileId, cuttingPermitId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
