package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksDto;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import ca.bc.gov.nrs.fta.tenure.service.AssignMarksWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assigning timber marks to cut blocks, legacy FTA908: {@code GET} and {@code POST
 * /api/fta/cutting-permits/{cpId}/assign-marks}, where {@code cpId} is the authorization's HVA id.
 * The POST is FTA_ADMIN or either harvest role by the URL rules; specifying a new mark is further
 * limited by {@link HarvestAccess#isSenior}. The audit user id is taken from the authenticated
 * JWT.
 */
@RestController
@RequestMapping("/api/fta/cutting-permits")
public class AssignMarksWriteController {

  private final AssignMarksWriteService assignMarksWriteService;

  public AssignMarksWriteController(AssignMarksWriteService assignMarksWriteService) {
    this.assignMarksWriteService = assignMarksWriteService;
  }

  @GetMapping("/{cpId}/assign-marks")
  public AssignMarksDto find(@PathVariable String cpId, @RequestParam String forestFileId) {
    return assignMarksWriteService.find(cpId, forestFileId);
  }

  @PostMapping("/{cpId}/assign-marks")
  public ResponseEntity<Map<String, Object>> assignMarks(
      @PathVariable String cpId,
      @RequestBody AssignMarksRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    int updated = assignMarksWriteService.assignMarks(
        cpId, request, HarvestAccess.isSenior(principal), userId);
    return ResponseEntity.ok(Map.of("cpId", cpId, "updated", updated));
  }
}
