package ca.bc.gov.nrs.fta.tenure.tab.assocclients;

import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientRequest;
import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientWriteResult;
import ca.bc.gov.nrs.fta.tenure.tab.assocclients.AssocClientsDtos.AssocClientsListDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure Associated clients tab API (legacy FTA920, file level):
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{forestFileId}/associated-clients} — the rows and gates;
 *   <li>{@code POST} the same path — add ({@link AssocClientRequest});
 *   <li>{@code PUT .../associated-clients/{skey}} — update (with {@code revisionCount});
 *   <li>{@code DELETE .../associated-clients/{skey}?revisionCount=} — delete a C or S client.
 * </ul>
 *
 * Writes return the refreshed tab and legacy's "set to previous licensee" note when a new Main
 * Licensee demoted the current one. Reads follow the tenure's read rule; writes are FTA_ADMIN
 * via the generic rule. Client types come from {@code /api/fta/code-lists/file-client-types}.
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class AssocClientsController {

  private final AssocClientsService service;

  public AssocClientsController(AssocClientsService service) {
    this.service = service;
  }

  @GetMapping("/{forestFileId}/associated-clients")
  public AssocClientsListDto list(@PathVariable String forestFileId) {
    return service.list(forestFileId);
  }

  @PostMapping("/{forestFileId}/associated-clients")
  @ResponseStatus(HttpStatus.CREATED)
  public AssocClientWriteResult add(
      @PathVariable String forestFileId,
      @RequestBody AssocClientRequest request,
      JwtAuthenticationToken principal) {
    String note = service.add(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return new AssocClientWriteResult(service.list(forestFileId), note);
  }

  @PutMapping("/{forestFileId}/associated-clients/{skey}")
  public AssocClientWriteResult update(
      @PathVariable String forestFileId,
      @PathVariable long skey,
      @RequestBody AssocClientRequest request,
      JwtAuthenticationToken principal) {
    String note = service.update(
        forestFileId, skey, request, JwtPrincipalUtil.getAuditUserId(principal));
    return new AssocClientWriteResult(service.list(forestFileId), note);
  }

  @DeleteMapping("/{forestFileId}/associated-clients/{skey}")
  public AssocClientsListDto delete(
      @PathVariable String forestFileId,
      @PathVariable long skey,
      @RequestParam(required = false) Long revisionCount) {
    service.delete(forestFileId, skey, revisionCount);
    return service.list(forestFileId);
  }
}
