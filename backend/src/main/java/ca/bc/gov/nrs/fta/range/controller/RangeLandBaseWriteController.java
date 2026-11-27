package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.Options;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.RemoveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.Result;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeLandBaseWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA615R Associated Land Base editing on the range tenure page.
 *
 * <ul>
 *   <li>{@code GET .../land-base/options} — the ownership types and land purposes;
 *   <li>{@code POST .../land-base} — add a record;
 *   <li>{@code PUT .../land-base/{rlbSkey}} — update a record;
 *   <li>{@code POST .../land-base/{rlbSkey}/remove} — de-activate a record, with its comment.
 * </ul>
 *
 * <p>The writes are open to FTA_ADMIN and both range roles by the URL rules, and limited to the
 * files the user may edit by the service.
 */
@RestController
@RequestMapping("/api/fta/range-tenures/{forestFileId}/land-base")
public class RangeLandBaseWriteController {

  private final RangeLandBaseWriteService service;

  public RangeLandBaseWriteController(RangeLandBaseWriteService service) {
    this.service = service;
  }

  @GetMapping("/options")
  public Options options(@PathVariable String forestFileId) {
    return service.options();
  }

  @PostMapping
  public Result add(
      @PathVariable String forestFileId,
      @RequestBody SaveRequest request,
      JwtAuthenticationToken principal) {
    return service.add(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PutMapping("/{rlbSkey}")
  public Result update(
      @PathVariable String forestFileId,
      @PathVariable long rlbSkey,
      @RequestBody SaveRequest request,
      JwtAuthenticationToken principal) {
    return service.update(
        forestFileId, rlbSkey, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/{rlbSkey}/remove")
  public Result remove(
      @PathVariable String forestFileId,
      @PathVariable long rlbSkey,
      @RequestBody RemoveRequest request,
      JwtAuthenticationToken principal) {
    return service.remove(
        forestFileId, rlbSkey, request, JwtPrincipalUtil.getAuditUserId(principal));
  }
}
