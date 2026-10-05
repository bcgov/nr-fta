package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Rec project tab of the tenure detail — legacy FTA701 (Recreation Project Details):
 * {@code /api/fta/tenures/{forestFileId}/rec-project[/…]}, and its dropdowns at
 * {@code /api/fta/tenure-lookups/rec-project}. Reads are open to every role that can open the
 * tenure; writes are FTA_ADMIN by the generic write rule.
 */
@RestController
@RequestMapping("/api/fta")
public class RecProjectController {

  private static final String BASE = "/tenures/{forestFileId}/rec-project";

  private final RecProjectService service;
  private final RecProjectWriteService writes;

  public RecProjectController(RecProjectService service, RecProjectWriteService writes) {
    this.service = service;
    this.writes = writes;
  }

  private static String user(JwtAuthenticationToken principal) {
    return JwtPrincipalUtil.getAuditUserId(principal);
  }

  /** The project, its districts, fees, access types and establishment orders, and the rules. */
  @GetMapping(BASE)
  public RecProjectDto get(@PathVariable String forestFileId) {
    return service.find(forestFileId).orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "Forest file not found."));
  }

  /** Creates or updates the project details (FTA701 Save). */
  @PutMapping(BASE)
  public RecProjectWriteService.SaveResult save(
      @PathVariable String forestFileId,
      @RequestBody RecProjectRequests.Save request,
      JwtAuthenticationToken principal) {
    return writes.saveProject(forestFileId, request, user(principal));
  }

  @PostMapping(BASE + "/districts")
  public ResponseEntity<Void> addDistrict(
      @PathVariable String forestFileId,
      @RequestBody RecProjectRequests.District request,
      JwtAuthenticationToken principal) {
    writes.addDistrict(forestFileId, request, user(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping(BASE + "/districts/{districtCode}")
  public ResponseEntity<Void> removeDistrict(
      @PathVariable String forestFileId, @PathVariable String districtCode) {
    writes.removeDistrict(forestFileId, districtCode);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(BASE + "/fees")
  public ResponseEntity<Void> addFee(
      @PathVariable String forestFileId,
      @RequestBody RecProjectRequests.Fee request,
      JwtAuthenticationToken principal) {
    writes.saveFee(forestFileId, null, request, user(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PutMapping(BASE + "/fees/{feeId}")
  public ResponseEntity<Void> updateFee(
      @PathVariable String forestFileId,
      @PathVariable long feeId,
      @RequestBody RecProjectRequests.Fee request,
      JwtAuthenticationToken principal) {
    writes.saveFee(forestFileId, feeId, request, user(principal));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping(BASE + "/fees/{feeId}")
  public ResponseEntity<Void> deleteFee(
      @PathVariable String forestFileId,
      @PathVariable long feeId,
      @RequestParam Long revisionCount) {
    writes.deleteFee(forestFileId, feeId, revisionCount);
    return ResponseEntity.noContent().build();
  }

  @PostMapping(BASE + "/accesses")
  public ResponseEntity<Void> addAccess(
      @PathVariable String forestFileId,
      @RequestBody RecProjectRequests.Access request,
      JwtAuthenticationToken principal) {
    writes.addAccess(forestFileId, request, user(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping(BASE + "/accesses/{accessCode}/{subAccessCode}")
  public ResponseEntity<Void> deleteAccess(
      @PathVariable String forestFileId,
      @PathVariable String accessCode,
      @PathVariable String subAccessCode,
      @RequestParam Long revisionCount) {
    writes.deleteAccess(forestFileId, accessCode, subAccessCode, revisionCount);
    return ResponseEntity.noContent().build();
  }

  /** Uploads an establishment order: {@code {fileName, contentBase64}} of a PDF. */
  @PostMapping(BASE + "/attachments")
  public ResponseEntity<Void> addAttachment(
      @PathVariable String forestFileId,
      @RequestBody RecProjectRequests.Attachment request,
      JwtAuthenticationToken principal) {
    writes.addAttachment(forestFileId, request, user(principal));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** The establishment order's PDF. */
  @GetMapping(BASE + "/attachments/{attachmentId}")
  public ResponseEntity<byte[]> attachment(
      @PathVariable String forestFileId, @PathVariable long attachmentId) {
    RecProjectWriteService.AttachmentFile file = writes.attachment(forestFileId, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.content());
  }

  @DeleteMapping(BASE + "/attachments/{attachmentId}")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable String forestFileId, @PathVariable long attachmentId) {
    writes.deleteAttachment(forestFileId, attachmentId);
    return ResponseEntity.noContent().build();
  }

  /** FTA701's dropdowns. */
  @GetMapping("/tenure-lookups/rec-project")
  public RecProjectLookupsDto lookups() {
    return service.lookups();
  }
}
