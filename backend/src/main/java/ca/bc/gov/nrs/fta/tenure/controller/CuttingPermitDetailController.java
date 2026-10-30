package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitDetailDto;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitConditionRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitConditionRow;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitLookups;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitPostponementRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitPostponementRow;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitRetireRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitSaveRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitWriteResult;
import ca.bc.gov.nrs.fta.tenure.service.CuttingPermitDetailService;
import ca.bc.gov.nrs.fta.tenure.service.CuttingPermitWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
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
 * Cutting permit detail API — legacy FTA902.
 *
 * <ul>
 *   <li>{@code GET /api/fta/cutting-permits/{cpId}?forestFileId=} — the permit, with what the
 *       caller may do to it. A permit id is unique only within its tenure, so the tenure is
 *       required;
 *   <li>{@code PUT}/{@code DELETE /api/fta/cutting-permits/{hvaSkey}}, {@code POST .../retire} —
 *       Save, Delete and Retire;
 *   <li>{@code GET}/{@code POST .../conditions}, {@code DELETE .../conditions/{id}};
 *   <li>{@code GET}/{@code POST .../postponements}, {@code PUT}/{@code DELETE
 *       .../postponements/{id}};
 *   <li>{@code GET /api/fta/tenure-lookups/cutting-permit} — the edit dialog's lists.
 * </ul>
 *
 * <p>Who reaches which write is {@code ApiAuthorizationCustomizer}; what each permit allows them
 * is {@code CuttingPermitRules}. The audit user id comes from the authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta")
public class CuttingPermitDetailController {

  private final CuttingPermitDetailService service;
  private final CuttingPermitWriteService writes;

  public CuttingPermitDetailController(
      CuttingPermitDetailService service, CuttingPermitWriteService writes) {
    this.service = service;
    this.writes = writes;
  }

  @GetMapping("/cutting-permits/{cpId}")
  public ResponseEntity<CuttingPermitDetailDto> get(
      @PathVariable String cpId,
      @RequestParam String forestFileId,
      JwtAuthenticationToken principal) {
    return service.findByCpId(cpId, forestFileId, principal)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/cutting-permits/{hvaSkey}")
  public PermitWriteResult save(
      @PathVariable long hvaSkey,
      @RequestBody PermitSaveRequest request,
      JwtAuthenticationToken principal) {
    return writes.save(hvaSkey, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/cutting-permits/{hvaSkey}")
  public PermitWriteResult delete(
      @PathVariable long hvaSkey,
      @RequestParam Long revisionCount,
      JwtAuthenticationToken principal) {
    return writes.delete(
        hvaSkey, revisionCount, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/cutting-permits/{hvaSkey}/retire")
  public PermitWriteResult retire(
      @PathVariable long hvaSkey,
      @RequestBody PermitRetireRequest request,
      JwtAuthenticationToken principal) {
    return writes.retire(
        hvaSkey, request.revisionCount(), principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @GetMapping("/cutting-permits/{hvaSkey}/conditions")
  public List<PermitConditionRow> conditions(@PathVariable long hvaSkey) {
    return service.conditions(hvaSkey);
  }

  @PostMapping("/cutting-permits/{hvaSkey}/conditions")
  public PermitWriteResult addCondition(
      @PathVariable long hvaSkey,
      @RequestBody PermitConditionRequest request,
      JwtAuthenticationToken principal) {
    return writes.addCondition(
        hvaSkey, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/cutting-permits/{hvaSkey}/conditions/{conditionId}")
  public PermitWriteResult deleteCondition(
      @PathVariable long hvaSkey,
      @PathVariable long conditionId,
      JwtAuthenticationToken principal) {
    return writes.deleteCondition(
        hvaSkey, conditionId, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @GetMapping("/cutting-permits/{hvaSkey}/postponements")
  public List<PermitPostponementRow> postponements(@PathVariable long hvaSkey) {
    return service.postponements(hvaSkey);
  }

  @PostMapping("/cutting-permits/{hvaSkey}/postponements")
  public PermitWriteResult addPostponement(
      @PathVariable long hvaSkey,
      @RequestBody PermitPostponementRequest request,
      JwtAuthenticationToken principal) {
    return writes.addPostponement(
        hvaSkey, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PutMapping("/cutting-permits/{hvaSkey}/postponements/{postponementId}")
  public PermitWriteResult updatePostponement(
      @PathVariable long hvaSkey,
      @PathVariable long postponementId,
      @RequestBody PermitPostponementRequest request,
      JwtAuthenticationToken principal) {
    return writes.updatePostponement(
        hvaSkey, postponementId, request, principal, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/cutting-permits/{hvaSkey}/postponements/{postponementId}")
  public PermitWriteResult deletePostponement(
      @PathVariable long hvaSkey,
      @PathVariable long postponementId,
      @RequestParam Long revisionCount,
      JwtAuthenticationToken principal) {
    return writes.deletePostponement(
        hvaSkey,
        postponementId,
        revisionCount,
        principal,
        JwtPrincipalUtil.getAuditUserId(principal));
  }

  @GetMapping("/tenure-lookups/cutting-permit")
  public PermitLookups lookups() {
    return service.lookups();
  }
}
