package ca.bc.gov.nrs.fta.tenure.tab.notes;

import ca.bc.gov.nrs.fta.tenure.tab.notes.NotesDtos.NotesAddRequest;
import ca.bc.gov.nrs.fta.tenure.tab.notes.NotesDtos.NotesListDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure Notes tab API (legacy FTA970): {@code GET /api/fta/tenures/{forestFileId}/notes} and
 * {@code POST} the same path with {@code {"note": "..."}}. Reads follow the tenure's read rule;
 * the write is FTA_ADMIN via the generic rule. The audit user id comes from the JWT.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class NotesController {

  private final NotesService notesService;

  public NotesController(NotesService notesService) {
    this.notesService = notesService;
  }

  @GetMapping("/{forestFileId}/notes")
  public NotesListDto list(@PathVariable String forestFileId) {
    return notesService.list(forestFileId);
  }

  @PostMapping("/{forestFileId}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NotesListDto add(
      @PathVariable String forestFileId,
      @RequestBody NotesAddRequest request,
      JwtAuthenticationToken principal) {
    notesService.add(
        forestFileId, request == null ? null : request.note(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return notesService.list(forestFileId);
  }
}
