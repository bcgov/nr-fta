package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitResult;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppPermitFile;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppProfDecDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppTabDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Tenure "Tenure application" tab API (legacy FTA950, Tenure Application List).
 *
 * <ul>
 *   <li>{@code GET /api/fta/tenures/{forestFileId}/tenure-applications} — the smart-form CP
 *       requests, CP rejections and spatial submissions, with the columns shown for the file
 *       type and whether each application can be issued;
 *   <li>{@code GET .../tenure-applications/{tenureAppId}/professional-declarations
 *       ?cuttingPermitId=&hvaSkey=} — the Prof Dec popup;
 *   <li>{@code POST .../tenure-applications/{tenureAppId}/issue-permit}, multipart: {@code
 *       file} (the permit PDF), {@code documentName}, {@code recipients} (repeated), {@code
 *       attachDocument}, optional {@code hvaSkey} — Issue Permit: upload, issue, e-mail
 *       (FTA_ADMIN via the generic write rule). The audit user id comes from the JWT.
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

  @PostMapping(
      value = "/{forestFileId}/tenure-applications/{tenureAppId}/issue-permit",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TenureAppIssuePermitResult issuePermit(
      @PathVariable String forestFileId,
      @PathVariable long tenureAppId,
      @RequestParam(required = false) Long hvaSkey,
      @RequestParam(required = false) String documentName,
      @RequestParam(required = false) List<String> recipients,
      @RequestParam(defaultValue = "false") boolean attachDocument,
      @RequestParam(required = false) MultipartFile file,
      JwtAuthenticationToken principal) {
    TenureAppPermitFile permit;
    try {
      permit = file == null
          ? null
          : new TenureAppPermitFile(file.getOriginalFilename(), file.getBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return issueService.issue(
        forestFileId,
        tenureAppId,
        new TenureAppIssuePermitRequest(
            hvaSkey, documentName, recipients == null ? List.of() : recipients, attachDocument),
        permit,
        JwtPrincipalUtil.getAuditUserId(principal));
  }
}
