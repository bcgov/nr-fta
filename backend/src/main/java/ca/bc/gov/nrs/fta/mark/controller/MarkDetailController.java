package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkAmendmentRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkClientRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkUpdateRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkClientUpdateRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexUpdateRequest;
import ca.bc.gov.nrs.fta.mark.service.MarkAmendmentWriteService;
import ca.bc.gov.nrs.fta.mark.service.MarkClientWriteService;
import ca.bc.gov.nrs.fta.mark.service.MarkDetailService;
import ca.bc.gov.nrs.fta.mark.service.MarkLandIndexWriteService;
import ca.bc.gov.nrs.fta.mark.service.MarkPrintService;
import ca.bc.gov.nrs.fta.mark.service.MarkSnapshotService;
import ca.bc.gov.nrs.fta.mark.service.MarkSubmitService;
import ca.bc.gov.nrs.fta.mark.service.MarkUpdateService;
import ca.bc.gov.nrs.fta.mark.service.TimberMarkPreview;
import ca.bc.gov.nrs.fta.security.RoleConstants;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private Mark detail API — {@code GET /api/fta/marks/{markNumber}}, and {@code PUT} on the
 * same path to save it (the FTA510 "Save" button). The path id is the timber mark; the
 * response mirrors the legacy {@code THE.FTA_510_PRIVATE_MARK.GET} record enriched with the
 * land index (FTA_511), associated clients (FTA_513) and amendment history, plus the
 * {@link MarkEditRules} for the caller.
 *
 * <p>An application that has not been issued has no timber mark yet, only a
 * certificate; {@code ?by=certificate} reads the path id as that certificate.
 * The two are separate keys rather than one lookup over both columns, so a
 * certificate can never resolve to an unrelated mark that happens to share its
 * characters.
 *
 * <p>The {@code PUT} is open to {@code FTA_ADMIN} and {@code FTA_TIMBER_MARK_HEADQUARTERS_ADMIN}
 * ({@code ApiAuthorizationCustomizer}); which fields it accepts is {@link MarkEditRules}.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkDetailController {

  private final MarkDetailService markDetailService;
  private final MarkUpdateService markUpdateService;
  private final MarkPrintService markPrintService;
  private final MarkSnapshotService markSnapshotService;
  private final TimberMarkPreview timberMarkPreview;
  private final MarkLandIndexWriteService markLandIndexWriteService;
  private final MarkClientWriteService markClientWriteService;
  private final MarkAmendmentWriteService markAmendmentWriteService;
  private final MarkSubmitService markSubmitService;

  public MarkDetailController(
      MarkDetailService markDetailService,
      MarkUpdateService markUpdateService,
      MarkPrintService markPrintService,
      MarkSnapshotService markSnapshotService,
      TimberMarkPreview timberMarkPreview,
      MarkLandIndexWriteService markLandIndexWriteService,
      MarkClientWriteService markClientWriteService,
      MarkAmendmentWriteService markAmendmentWriteService,
      MarkSubmitService markSubmitService) {
    this.markDetailService = markDetailService;
    this.markUpdateService = markUpdateService;
    this.markPrintService = markPrintService;
    this.markSnapshotService = markSnapshotService;
    this.timberMarkPreview = timberMarkPreview;
    this.markLandIndexWriteService = markLandIndexWriteService;
    this.markClientWriteService = markClientWriteService;
    this.markAmendmentWriteService = markAmendmentWriteService;
    this.markSubmitService = markSubmitService;
  }

  /**
   * {@code POST /api/fta/marks/{id}/submit} — FTA510's Submit to HQ: a district's PA
   * application goes to Headquarters as PI. Body: {@code {"revisionCount": n}}. Returns the
   * re-read mark, as the PUT does.
   */
  @PostMapping("/{markNumber}/submit")
  public ResponseEntity<MarkDetailDto> submit(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody Map<String, Long> body,
      JwtAuthenticationToken principal) {
    boolean districtUser = isDistrictUser(principal);
    markSubmitService.submit(
        markNumber, isCertificate(by), body.get("revisionCount"), districtUser,
        JwtPrincipalUtil.getAuditUserId(principal));
    return find(markNumber, by)
        .map(mark -> mark.withEditRules(MarkEditRules.of(mark, true, districtUser)))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * {@code POST /api/fta/marks/{id}/amendments} — FTA512: request an amendment (PI).
   * Returns nothing; the client re-reads the mark for the new row.
   */
  @PostMapping("/{markNumber}/amendments")
  public ResponseEntity<Void> addAmendment(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody MarkAmendmentRequest request,
      JwtAuthenticationToken principal) {
    markAmendmentWriteService.add(
        markNumber, isCertificate(by), request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code POST /api/fta/marks/{id}/clients} — FTA513's add row: an associated client.
   * Returns {@code {"note": …}} when adding a main licensee demoted the current one.
   */
  @PostMapping("/{markNumber}/clients")
  public ResponseEntity<Map<String, String>> addClient(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody MarkClientRequest request,
      JwtAuthenticationToken principal) {
    String note = markClientWriteService.add(
        markNumber, isCertificate(by), request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.ok(note == null ? Map.of() : Map.of("note", note));
  }

  /**
   * {@code PUT /api/fta/marks/{id}/clients/{skey}} — FTA513's save of an existing client.
   * Returns {@code {"note": …}} when making it the main licensee demoted the current one.
   */
  @PutMapping("/{markNumber}/clients/{skey}")
  public ResponseEntity<Map<String, String>> updateClient(
      @PathVariable String markNumber,
      @PathVariable long skey,
      @RequestParam(required = false) String by,
      @RequestBody MarkClientUpdateRequest request,
      JwtAuthenticationToken principal) {
    String note = markClientWriteService.update(
        markNumber, isCertificate(by), skey, request, isDistrictUser(principal),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.ok(note == null ? Map.of() : Map.of("note", note));
  }

  /**
   * {@code POST /api/fta/marks/{id}/land-index} — FTA511's add row: a land index on the mark.
   * Returns nothing; the client re-reads the mark for the new row.
   */
  @PostMapping("/{markNumber}/land-index")
  public ResponseEntity<Void> addLandIndex(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody MarkLandIndexRequest request,
      JwtAuthenticationToken principal) {
    markLandIndexWriteService.add(
        markNumber, isCertificate(by), request, JwtPrincipalUtil.getAuditUserId(principal));
    // 204, not 201: there is no body, and the client re-reads the mark.
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code PUT /api/fta/marks/{id}/land-index/{skey}} — FTA511's save of an existing land
   * index. Returns nothing; the client re-reads the mark.
   */
  @PutMapping("/{markNumber}/land-index/{skey}")
  public ResponseEntity<Void> updateLandIndex(
      @PathVariable String markNumber,
      @PathVariable long skey,
      @RequestParam(required = false) String by,
      @RequestBody MarkLandIndexUpdateRequest request,
      JwtAuthenticationToken principal) {
    markLandIndexWriteService.update(
        markNumber, isCertificate(by), skey, request, isDistrictUser(principal),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /**
   * {@code POST /api/fta/marks/{id}/print} — FTA510's Print: the FTA402 Registered Timber Mark
   * Certificate as a PDF. A POST, not a GET, because for a district user it also marks the
   * mark issued (HN to HI); see {@link MarkPrintService}.
   */
  @PostMapping("/{markNumber}/print")
  public ResponseEntity<byte[]> print(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      JwtAuthenticationToken principal) {
    boolean districtUser = isDistrictUser(principal);
    byte[] pdf = markPrintService.print(
        markNumber, isCertificate(by), districtUser, JwtPrincipalUtil.getAuditUserId(principal));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename("timber-mark-certificate-" + markNumber + ".pdf")
        .build());
    headers.setCacheControl("no-store");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }

  /**
   * {@code GET /api/fta/marks/next-timber-mark?fileType=B08} — the timber mark that issuing a
   * mark of that type would be given now, without taking it, for the page to show before the
   * save that issues it: {@code {"timberMark": "ECHYG"}}, or 404 for a type that isn't issued
   * here. A literal path, so it wins over {@code /{markNumber}}.
   */
  @GetMapping("/next-timber-mark")
  public ResponseEntity<Map<String, String>> nextTimberMark(@RequestParam String fileType) {
    return timberMarkPreview.nextFor(fileType.trim().toUpperCase(java.util.Locale.ROOT))
        .map(mark -> ResponseEntity.ok(Map.of("timberMark", mark)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * {@code POST /api/fta/marks/skip-timber-mark?fileType=B08} — passes over the next timber
   * mark (the user won't issue it) and returns the one after: {@code {"timberMark": …}}, or
   * 404 for a type that isn't issued here. Takes the number for good, as legacy's repeated
   * Assign Mark did.
   */
  @PostMapping("/skip-timber-mark")
  public ResponseEntity<Map<String, String>> skipTimberMark(@RequestParam String fileType) {
    return timberMarkPreview.skipFor(fileType.trim().toUpperCase(java.util.Locale.ROOT))
        .map(mark -> ResponseEntity.ok(Map.of("timberMark", mark)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * {@code GET /api/fta/marks/{id}/snapshot} — the mark as it stands now, as a PDF stamped with
   * the time and the caller: a point-in-time record (see {@code MarkSnapshotReport}). Read-only,
   * for anyone who may view the mark.
   */
  @GetMapping("/{markNumber}/snapshot")
  public ResponseEntity<byte[]> snapshot(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      JwtAuthenticationToken principal) {
    return markSnapshotService
        .snapshot(markNumber, isCertificate(by), JwtPrincipalUtil.getAuditUserId(principal))
        .map(s -> {
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_PDF);
          headers.setContentDisposition(ContentDisposition.attachment()
              .filename("mark-" + markNumber + "-snapshot-"
                  + s.generatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
                  + ".pdf")
              .build());
          headers.setCacheControl("no-store");
          return ResponseEntity.ok().headers(headers).body(s.pdf());
        })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{markNumber}")
  public ResponseEntity<MarkDetailDto> getMark(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      Authentication authentication) {
    boolean canEdit = canEditMarks(authentication);
    boolean districtUser = isDistrictUser(authentication);
    return find(markNumber, by)
        .map(mark -> mark.withEditRules(MarkEditRules.of(mark, canEdit, districtUser)))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/{markNumber}")
  public ResponseEntity<MarkDetailDto> updateMark(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by,
      @RequestBody MarkUpdateRequest request,
      JwtAuthenticationToken principal) {
    boolean districtUser = isDistrictUser(principal);
    markUpdateService.update(
        markNumber, isCertificate(by), request, districtUser,
        JwtPrincipalUtil.getAuditUserId(principal));
    // Re-read, so the client gets the saved record with its new revision counts and the
    // rules for its new status.
    return find(markNumber, by)
        .map(mark -> mark.withEditRules(MarkEditRules.of(mark, true, districtUser)))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private Optional<MarkDetailDto> find(String id, String by) {
    return isCertificate(by)
        ? markDetailService.findByCertificate(id)
        : markDetailService.findByMarkNumber(id);
  }

  private static boolean isCertificate(String by) {
    return "certificate".equals(by);
  }

  /**
   * The {@code FTA_TIMBER_MARK_DISTRICT_ADMIN} role — legacy's district organization level:
   * its print marks the mark issued, its applications start PA, and it submits them to HQ.
   */
  public static boolean isDistrictUser(Authentication authentication) {
    return authentication != null && authentication.getAuthorities().stream()
        .anyMatch(a -> RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY.equals(a.getAuthority()));
  }

  /** The roles that may write private marks — the same pair the PUT rule admits. */
  private static boolean canEditMarks(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> RoleConstants.ADMIN_AUTHORITY.equals(a)
            || RoleConstants.TIMBER_MARK_HEADQUARTERS_AUTHORITY.equals(a)
            || RoleConstants.TIMBER_MARK_DISTRICT_AUTHORITY.equals(a));
  }
}
