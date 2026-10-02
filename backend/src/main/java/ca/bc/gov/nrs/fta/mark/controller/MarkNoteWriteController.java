package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkNoteRequest;
import ca.bc.gov.nrs.fta.mark.service.MarkNoteWriteService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private mark notes API — {@code POST /api/fta/marks/{id}/notes} (add a note).
 * As on the detail endpoint, the id is the timber mark, or the certificate with
 * {@code ?by=certificate}. Write access is {@code FTA_ADMIN} or either timber mark role,
 * enforced by {@code ApiAuthorizationCustomizer}; the audit user id is taken from the
 * authenticated JWT.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkNoteWriteController {

  private final MarkNoteWriteService markNoteWriteService;

  public MarkNoteWriteController(MarkNoteWriteService markNoteWriteService) {
    this.markNoteWriteService = markNoteWriteService;
  }

  @PostMapping("/{markNumber}/notes")
  public ResponseEntity<Map<String, String>> add(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody MarkNoteRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getIdpUsername(principal);
    String forestFileId = markNoteWriteService.add(
        markNumber, "certificate".equals(by), request.note(), userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("forestFileId", forestFileId));
  }
}
