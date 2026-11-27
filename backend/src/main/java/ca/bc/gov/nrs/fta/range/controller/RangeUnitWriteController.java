package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitPastureRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitSaveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitWriteResultDto;
import ca.bc.gov.nrs.fta.range.service.RangeUnitWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA630 Maintain Range Unit / Pasture's writes, porting {@code FTA_630_MN_RG_UN_PST}'s SAVE,
 * REMOVE, SAVE_ITEM and REMOVE_ITEM:
 * <ul>
 *   <li>{@code POST /api/fta/range-units} — Add New, then Save Range;</li>
 *   <li>{@code PUT /api/fta/range-units/{unitId}} — Save Range;</li>
 *   <li>{@code DELETE /api/fta/range-units/{unitId}?revisionCount=} — Delete Range;</li>
 *   <li>{@code POST /api/fta/range-units/{unitId}/pastures} — add a pasture;</li>
 *   <li>{@code PUT /api/fta/range-units/{unitId}/pastures/{pastureId}} — update one;</li>
 *   <li>{@code DELETE /api/fta/range-units/{unitId}/pastures/{pastureId}?pastureRevisionCount=}
 *       — delete one.</li>
 * </ul>
 * FTA_ADMIN and both range roles reach these (ApiAuthorizationCustomizer); which districts each
 * may write is {@link RangeUnitWriteService}'s check. The audit user id comes from the token.
 */
@RestController
@RequestMapping("/api/fta/range-units")
public class RangeUnitWriteController {

  private final RangeUnitWriteService service;

  public RangeUnitWriteController(RangeUnitWriteService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<RangeUnitWriteResultDto> add(
      @RequestBody RangeUnitSaveRequest request, JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.add(request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/{unitId}")
  public RangeUnitWriteResultDto save(
      @PathVariable String unitId,
      @RequestBody RangeUnitSaveRequest request,
      JwtAuthenticationToken principal) {
    return service.save(unitId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/{unitId}")
  public ResponseEntity<Void> delete(
      @PathVariable String unitId, @RequestParam(required = false) Integer revisionCount) {
    service.delete(unitId, revisionCount);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{unitId}/pastures")
  public ResponseEntity<RangeUnitWriteResultDto> addPasture(
      @PathVariable String unitId,
      @RequestBody RangeUnitPastureRequest request,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
        service.addPasture(unitId, request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/{unitId}/pastures/{pastureId}")
  public RangeUnitWriteResultDto updatePasture(
      @PathVariable String unitId,
      @PathVariable String pastureId,
      @RequestBody RangeUnitPastureRequest request,
      JwtAuthenticationToken principal) {
    return service.updatePasture(
        unitId, pastureId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/{unitId}/pastures/{pastureId}")
  public ResponseEntity<Void> deletePasture(
      @PathVariable String unitId,
      @PathVariable String pastureId,
      @RequestParam(required = false) Integer pastureRevisionCount) {
    service.deletePasture(unitId, pastureId, pastureRevisionCount);
    return ResponseEntity.noContent().build();
  }
}
