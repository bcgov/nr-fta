package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitCreateRequest;
import ca.bc.gov.nrs.fta.tenure.dto.TenureCuttingPermitDto;
import ca.bc.gov.nrs.fta.tenure.dto.TenureDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.CuttingPermitCreateService;
import ca.bc.gov.nrs.fta.tenure.service.TenureCuttingPermitService;
import ca.bc.gov.nrs.fta.tenure.service.TenureDetailService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure detail API — {@code GET /api/fta/tenures/{forestFileId}}. Returns the
 * single richer detail record for one file, mirroring the GET action of the
 * legacy {@code THE.FTA_100_TENURE.mainline} procedure; 404 when the file does
 * not exist.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TenureDetailController {

  private final TenureDetailService tenureDetailService;
  private final TenureCuttingPermitService tenureCuttingPermitService;
  private final CuttingPermitCreateService cuttingPermitCreateService;

  public TenureDetailController(
      TenureDetailService tenureDetailService,
      TenureCuttingPermitService tenureCuttingPermitService,
      CuttingPermitCreateService cuttingPermitCreateService) {
    this.tenureDetailService = tenureDetailService;
    this.tenureCuttingPermitService = tenureCuttingPermitService;
    this.cuttingPermitCreateService = cuttingPermitCreateService;
  }

  @GetMapping("/{forestFileId}")
  public ResponseEntity<TenureDetailDto> getByForestFileId(
      @PathVariable String forestFileId) {
    TenureDetailDto detail = tenureDetailService.findByForestFileId(forestFileId);
    return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
  }

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/cutting-permits} — the tenure's cutting
   * permits, legacy FTA901 (the Cutting permit / mark tab).
   */
  @GetMapping("/{forestFileId}/cutting-permits")
  public List<TenureCuttingPermitDto> cuttingPermits(@PathVariable String forestFileId) {
    return tenureCuttingPermitService.list(forestFileId);
  }

  /**
   * {@code POST /api/fta/tenures/{forestFileId}/cutting-permits} — add a cutting permit and
   * its timber mark (FTA_ADMIN, by the generic write rule). Returns the CP and the mark made
   * for it.
   */
  @PostMapping("/{forestFileId}/cutting-permits")
  public ResponseEntity<CuttingPermitCreateService.Created> addCuttingPermit(
      @PathVariable String forestFileId,
      @RequestBody CuttingPermitCreateRequest request,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(cuttingPermitCreateService.create(
        forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal)));
  }
}
