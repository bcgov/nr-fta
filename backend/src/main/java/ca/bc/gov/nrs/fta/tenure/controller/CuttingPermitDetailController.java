package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.CuttingPermitDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cutting permit detail API — {@code GET /api/fta/cutting-permits/{cpId}}. The
 * path id and optional {@code forestFileId} filter mirror the legacy
 * {@code THE.FTA_902_CP_DETAIL.GET} inputs. Returns a single detail record, or
 * 404 when no matching cutting permit exists.
 */
@RestController
@RequestMapping("/api/fta/cutting-permits")
public class CuttingPermitDetailController {

  private final CuttingPermitDetailService cuttingPermitDetailService;

  public CuttingPermitDetailController(CuttingPermitDetailService cuttingPermitDetailService) {
    this.cuttingPermitDetailService = cuttingPermitDetailService;
  }

  @GetMapping("/{cpId}")
  public ResponseEntity<CuttingPermitDetailDto> get(
      @PathVariable String cpId,
      @RequestParam(required = false) String forestFileId) {
    return cuttingPermitDetailService.findByCpId(cpId, forestFileId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
