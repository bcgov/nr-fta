package ca.bc.gov.nrs.fta.tenure.tab.assocfiles;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFileAddRequest;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFileAddResult;
import ca.bc.gov.nrs.fta.tenure.tab.assocfiles.AssocFilesDtos.AssocFilesListDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure Associated files tab API (legacy FTA910):
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{forestFileId}/associated-files} — the rows and the add gate;
 *   <li>{@code POST} the same path — add one ({@link AssocFileAddRequest}); returns the refreshed
 *       tab and legacy's AAC warning, if any;
 *   <li>{@code DELETE .../associated-files/{associatedFileId}?fileSourceCode=&revisionCount=}
 *       — delete one (source F: both directions);
 *   <li>{@code GET /api/fta/tenure-lookups/file-association-types} — the current association
 *       types (sources are {@code /api/fta/code-lists/file-sources}).
 * </ul>
 *
 * Reads follow the tenure's read rule; writes are FTA_ADMIN via the generic rule. The audit
 * user id comes from the JWT.
 */
@RestController
public class AssocFilesController {

  private final AssocFilesService service;

  public AssocFilesController(AssocFilesService service) {
    this.service = service;
  }

  @GetMapping("/api/fta/tenures/{forestFileId}/associated-files")
  public AssocFilesListDto list(@PathVariable String forestFileId) {
    return service.list(forestFileId);
  }

  @PostMapping("/api/fta/tenures/{forestFileId}/associated-files")
  @ResponseStatus(HttpStatus.CREATED)
  public AssocFileAddResult add(
      @PathVariable String forestFileId,
      @RequestBody AssocFileAddRequest request,
      JwtAuthenticationToken principal) {
    String warning =
        service.add(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return new AssocFileAddResult(service.list(forestFileId), warning);
  }

  @DeleteMapping("/api/fta/tenures/{forestFileId}/associated-files/{associatedFileId}")
  public AssocFilesListDto delete(
      @PathVariable String forestFileId,
      @PathVariable String associatedFileId,
      @RequestParam String fileSourceCode,
      @RequestParam(required = false) Long revisionCount) {
    service.delete(forestFileId, associatedFileId, fileSourceCode, revisionCount);
    return service.list(forestFileId);
  }

  @GetMapping("/api/fta/tenure-lookups/file-association-types")
  public List<CodeOptionDto> associationTypes() {
    return service.associationTypes();
  }
}
