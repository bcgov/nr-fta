package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import ca.bc.gov.nrs.fta.tenure.service.AssignMarksWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assign-marks write API — {@code POST
 * /api/fta/cutting-permits/{cpId}/assign-marks} (FTA908: assign hauling timber
 * marks to the cut blocks of a cutting permit). Write access is enforced to
 * {@code FTA_ADMIN} by the URL-level authorization rules
 * ({@code ApiAuthorizationCustomizer}); the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/cutting-permits")
public class AssignMarksWriteController {

  private final AssignMarksWriteService assignMarksWriteService;

  public AssignMarksWriteController(AssignMarksWriteService assignMarksWriteService) {
    this.assignMarksWriteService = assignMarksWriteService;
  }

  @PostMapping("/{cpId}/assign-marks")
  public ResponseEntity<Map<String, Object>> assignMarks(
      @PathVariable String cpId,
      @RequestBody AssignMarksRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    int updated = assignMarksWriteService.assignMarks(request, userId);
    return ResponseEntity.ok(Map.of("cpId", cpId, "updated", updated));
  }
}
