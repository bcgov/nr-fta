package ca.bc.gov.nrs.fta.billing.controller;

import ca.bc.gov.nrs.fta.billing.dto.BillingApprovalRequest;
import ca.bc.gov.nrs.fta.billing.dto.BillingInstructionRowDto;
import ca.bc.gov.nrs.fta.billing.dto.BillingInstructionsCriteria;
import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto;
import ca.bc.gov.nrs.fta.billing.dto.PrepareInstructionsRequest;
import ca.bc.gov.nrs.fta.billing.dto.PrepareInstructionsResult;
import ca.bc.gov.nrs.fta.billing.service.BillingApprovalService;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionsService;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewService;
import ca.bc.gov.nrs.fta.billing.service.PostBillingReport;
import ca.bc.gov.nrs.fta.billing.service.PreBillingReport;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Range billing — {@code /api/fta/admin/billing}:
 * <ul>
 *   <li>FTA680 Tenure Billing Instructions: {@code GET /instructions},
 *       {@code POST /instructions/prepare}</li>
 *   <li>FTA695 Invoice Preview: {@code GET /invoice-preview}</li>
 *   <li>FTA690 Tenure Approval Submission: {@code POST /approval}</li>
 *   <li>FTA685 Pre Billing Report (FTA408): {@code GET /pre-billing-report}, a PDF</li>
 *   <li>FTA686 Post Billing Report (FTA409): {@code GET /post-billing-report}, a PDF</li>
 * </ul>
 * Access is {@code FTA_ADMIN} by the URL-level authorization rules
 * ({@code ApiAuthorizationCustomizer}); the audit user id is taken from the JWT.
 */
@RestController
@RequestMapping("/api/fta/admin/billing")
public class BillingController {

  private final BillingInstructionsService instructions;
  private final InvoicePreviewService invoicePreview;
  private final BillingApprovalService approval;
  private final PreBillingReport preBillingReport;
  private final PostBillingReport postBillingReport;

  public BillingController(
      BillingInstructionsService instructions,
      InvoicePreviewService invoicePreview,
      BillingApprovalService approval,
      PreBillingReport preBillingReport,
      PostBillingReport postBillingReport) {
    this.instructions = instructions;
    this.invoicePreview = invoicePreview;
    this.approval = approval;
    this.preBillingReport = preBillingReport;
    this.postBillingReport = postBillingReport;
  }

  @GetMapping("/instructions")
  public ResponseEntity<List<BillingInstructionRowDto>> instructions(
      @RequestParam(required = false) Long orgUnitNo,
      @RequestParam(required = false) String calendarYear,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String clientName) {
    return ResponseEntity.ok(instructions.search(new BillingInstructionsCriteria(
        orgUnitNo, calendarYear, forestFileId, fileTypeCode, clientName)));
  }

  @PostMapping("/instructions/prepare")
  public ResponseEntity<PrepareInstructionsResult> prepare(
      @RequestBody PrepareInstructionsRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    return ResponseEntity.ok(instructions.prepare(request, userId));
  }

  @GetMapping("/invoice-preview")
  public ResponseEntity<InvoicePreviewDto> invoicePreview(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String calendarYear) {
    return ResponseEntity.ok(invoicePreview.preview(forestFileId, calendarYear));
  }

  @PostMapping("/approval")
  public ResponseEntity<Void> approval(
      @RequestBody BillingApprovalRequest request,
      JwtAuthenticationToken principal) {
    String userId = JwtPrincipalUtil.getAuditUserId(principal);
    approval.submit(request, userId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @GetMapping("/pre-billing-report")
  public ResponseEntity<byte[]> preBillingReport(
      @RequestParam(required = false) Long orgUnitNo,
      @RequestParam(required = false) String calendarYear,
      JwtAuthenticationToken principal) {
    byte[] pdf = preBillingReport.render(
        orgUnitNo, calendarYear, JwtPrincipalUtil.getAuditUserId(principal));
    return pdf("FTA408-pre-billing-" + calendarYear + ".pdf", pdf);
  }

  /** {@code orgUnit} is a district's ORG_UNIT_NO, or "*" for all of them. */
  @GetMapping("/post-billing-report")
  public ResponseEntity<byte[]> postBillingReport(
      @RequestParam(required = false) String orgUnit,
      @RequestParam(required = false) String calendarYear,
      @RequestParam(required = false, defaultValue = "1") String sortBy,
      JwtAuthenticationToken principal) {
    byte[] pdf = postBillingReport.render(
        orgUnit, calendarYear, sortBy, JwtPrincipalUtil.getAuditUserId(principal));
    return pdf("FTA409-post-billing-" + calendarYear + ".pdf", pdf);
  }

  private static ResponseEntity<byte[]> pdf(String filename, byte[] body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
    return ResponseEntity.ok().headers(headers).body(body);
  }
}
