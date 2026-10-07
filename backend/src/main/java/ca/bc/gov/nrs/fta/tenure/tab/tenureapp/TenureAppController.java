package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitResult;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppProfDecDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppTabDto;
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
 * Tenure "Tenure application" tab API (legacy FTA950, Tenure Application List).
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{forestFileId}/tenure-applications} — the smart-form CP
 *       requests, CP rejections and spatial submissions, with the columns shown for the file
 *       type and whether each application can be issued;
 *   <li>{@code GET .../tenure-applications/{tenureAppId}/professional-declarations
 *       ?cuttingPermitId=&hvaSkey=} — the Prof Dec popup;
 *   <li>{@code POST .../tenure-applications/{tenureAppId}/issue-permit} with
 *       {@code {"hvaSkey": 123, "documentUri": "..."}} — Issue Permit (FTA_ADMIN via the
 *       generic write rule). The audit user id comes from the JWT.
 * </ul>
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class TenureAppController {

  private final TenureAppService service;
  private final TenureAppIssuePermitService issueService;

  public TenureAppController(TenureAppService service, TenureAppIssuePermitService issueService) {
    this.service = service;
    this.issueService = issueService;
  }

  @GetMapping("/{forestFileId}/tenure-applications")
  public TenureAppTabDto tab(@PathVariable String forestFileId) {
    return service.tab(forestFileId);
  }

  @GetMapping("/{forestFileId}/tenure-applications/{tenureAppId}/professional-declarations")
  public List<TenureAppProfDecDto> professionalDeclarations(
      @PathVariable String forestFileId,
      @PathVariable long tenureAppId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) Long hvaSkey) {
    return service.professionalDeclarations(forestFileId, tenureAppId, cuttingPermitId, hvaSkey);
  }

  @PostMapping("/{forestFileId}/tenure-applications/{tenureAppId}/issue-permit")
  public TenureAppIssuePermitResult issuePermit(
      @PathVariable String forestFileId,
      @PathVariable long tenureAppId,
      @RequestBody TenureAppIssuePermitRequest request,
      JwtAuthenticationToken principal) {
    return issueService.issue(
        forestFileId, tenureAppId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }
}
