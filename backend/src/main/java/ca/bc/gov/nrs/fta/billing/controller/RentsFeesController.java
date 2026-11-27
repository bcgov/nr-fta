package ca.bc.gov.nrs.fta.billing.controller;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeListDto;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareRequest;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareResult;
import ca.bc.gov.nrs.fta.billing.service.RentsFeesReport;
import ca.bc.gov.nrs.fta.billing.service.RentsFeesService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA670 Annual Rents and Fees Preparation — {@code /api/fta/admin/rents-fees}:
 * {@code GET} (Go), {@code POST /prepare} (Prepare) and {@code GET /report} (Print, the FTA406
 * Rents and Fees Preparation Report as a PDF).
 *
 * <p>Access is {@code FTA_ADMIN} by the generic {@code /api/fta/admin/**} rule
 * ({@code ApiAuthorizationCustomizer}); which org units a district-scoped administrator may use
 * is {@link RentsFeesService}'s, and Print runs Go's check. The audit user id is taken from the
 * JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/rents-fees")
public class RentsFeesController {

  private final RentsFeesService service;
  private final RentsFeesReport report;

  public RentsFeesController(RentsFeesService service, RentsFeesReport report) {
    this.service = service;
    this.report = report;
  }

  @GetMapping
  public ResponseEntity<RentFeeListDto> search(
      @RequestParam(required = false) Long orgUnitNo,
      @RequestParam(required = false, defaultValue = "false") Boolean rollup) {
    return ResponseEntity.ok(service.search(orgUnitNo, rollup));
  }

  @PostMapping("/prepare")
  public ResponseEntity<RentFeePrepareResult> prepare(
      @RequestBody RentFeePrepareRequest request, JwtAuthenticationToken principal) {
    return ResponseEntity.ok(
        service.prepare(request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @GetMapping("/report")
  public ResponseEntity<byte[]> report(
      @RequestParam(required = false) Long orgUnitNo,
      @RequestParam(required = false, defaultValue = "false") Boolean rollup,
      JwtAuthenticationToken principal) {
    byte[] pdf = report.render(orgUnitNo, rollup, JwtPrincipalUtil.getAuditUserId(principal));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(RentsFeesReport.FILENAME).build());
    return ResponseEntity.ok().headers(headers).body(pdf);
  }
}
