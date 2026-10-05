package ca.bc.gov.nrs.fta.tenure.tab.aac;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacAreasRequest;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacResponse;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacSaveRequest;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenure detail's AAC tab — legacy FTA930. Reads are open to every role that can open the
 * tenure; writes are FTA_ADMIN by the generic write rule. The dialog's code lists are under
 * {@code /api/fta/tenure-lookups/aac-*}.
 */
@RestController
@RequestMapping("/api/fta")
public class AacController {

  private final AacService service;

  public AacController(AacService service) {
    this.service = service;
  }

  /** {@code GET /api/fta/tenures/{forestFileId}/aac} — AAC history, areas and rules. */
  @GetMapping("/tenures/{forestFileId}/aac")
  public AacResponse get(@PathVariable String forestFileId) {
    return service.get(forestFileId);
  }

  /** {@code POST …/aac} — add an AAC history row. */
  @PostMapping("/tenures/{forestFileId}/aac")
  public ResponseEntity<Void> add(
      @PathVariable String forestFileId,
      @RequestBody AacSaveRequest request,
      JwtAuthenticationToken principal) {
    service.add(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** {@code PUT …/aac/areas} — {@code {scheduleAArea, scheduleBArea, revisionCount}}. */
  @PutMapping("/tenures/{forestFileId}/aac/areas")
  public ResponseEntity<Void> saveAreas(
      @PathVariable String forestFileId,
      @RequestBody AacAreasRequest request,
      JwtAuthenticationToken principal) {
    service.saveAreas(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** {@code PUT …/aac/{amountId}} — change a row (with both revision counts). */
  @PutMapping("/tenures/{forestFileId}/aac/{amountId}")
  public ResponseEntity<Void> update(
      @PathVariable String forestFileId,
      @PathVariable long amountId,
      @RequestBody AacSaveRequest request,
      JwtAuthenticationToken principal) {
    service.update(forestFileId, amountId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code DELETE …/aac/{amountId}?periodRevisionCount=n&amountRevisionCount=m} — delete a
   * row, and its period once empty.
   */
  @DeleteMapping("/tenures/{forestFileId}/aac/{amountId}")
  public ResponseEntity<Void> delete(
      @PathVariable String forestFileId,
      @PathVariable long amountId,
      @RequestParam(required = false) Long periodRevisionCount,
      @RequestParam(required = false) Long amountRevisionCount) {
    service.delete(forestFileId, amountId, periodRevisionCount, amountRevisionCount);
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code GET /api/fta/tenure-lookups/…}: {@code aac-area-types}, {@code aac-cut-types},
   * {@code aac-adjustment-reasons} and {@code aac-harvest-units}.
   */
  @GetMapping({
    "/tenure-lookups/aac-area-types",
    "/tenure-lookups/aac-cut-types",
    "/tenure-lookups/aac-adjustment-reasons",
    "/tenure-lookups/aac-harvest-units"
  })
  public List<CodeOptionDto> lookup(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return service.lookup(uri.substring(uri.lastIndexOf('/') + 1));
  }
}
