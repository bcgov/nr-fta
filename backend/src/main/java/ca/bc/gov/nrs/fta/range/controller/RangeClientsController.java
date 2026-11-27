package ca.bc.gov.nrs.fta.range.controller;

import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientAssignRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientAssignResult;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientsListDto;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RelatedClientDto;
import ca.bc.gov.nrs.fta.range.service.RangeClientsService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range tenure Assoc. Client tab API (legacy FTA920R):
 *
 * <ul>
 *   <li>{@code GET /api/fta/range-tenures/{forestFileId}/clients} — Licensee (Current &amp;
 *       Previous) and whether Save is enabled;
 *   <li>{@code GET .../clients/related?clientNumber=&clientLocnCode=} — Get: the client and its
 *       partners;
 *   <li>{@code POST .../clients} — Save (Assign/Transfer to Client); returns the refreshed tab.
 * </ul>
 *
 * Reads follow the range read rule; Save is FTA_ADMIN or either range role, then
 * {@link ca.bc.gov.nrs.fta.range.service.RangeFileAccess} for the file.
 */
@RestController
@RequestMapping("/api/fta/range-tenures")
public class RangeClientsController {

  private final RangeClientsService service;

  public RangeClientsController(RangeClientsService service) {
    this.service = service;
  }

  @GetMapping("/{forestFileId}/clients")
  public RangeClientsListDto list(@PathVariable String forestFileId) {
    return service.list(forestFileId);
  }

  @GetMapping("/{forestFileId}/clients/related")
  public List<RelatedClientDto> related(
      @PathVariable String forestFileId,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode) {
    return service.related(forestFileId, clientNumber, clientLocnCode);
  }

  @PostMapping("/{forestFileId}/clients")
  public RangeClientAssignResult assign(
      @PathVariable String forestFileId,
      @RequestBody RangeClientAssignRequest request,
      JwtAuthenticationToken principal) {
    String message =
        service.assign(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
    return new RangeClientAssignResult(service.list(forestFileId), message);
  }
}
