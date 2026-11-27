package ca.bc.gov.nrs.fta.range.tenuretab;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Action;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Condition;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Create;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Created;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Result;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Save;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureTabDto.Zone;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/**
 * The range Tenure tab — legacy FTA100R — and range Add New.
 *
 * <ul>
 *   <li>{@code GET /{id}/tenure-tab} — the form, with what the user may do;</li>
 *   <li>{@code PUT /{id}/tenure-tab} — Save;</li>
 *   <li>{@code POST /{id}/tenure-tab/retire}, {@code POST /{id}/tenure-tab/cancel-replacement};</li>
 *   <li>{@code POST /{id}/special-conditions}, {@code PUT /{id}/special-conditions/{skey}},
 *       {@code DELETE /{id}/special-conditions/{skey}?revisionCount=} — Save Condition,
 *       Update, Delete;</li>
 *   <li>{@code GET /{id}/invoice-preview} — Preview Invoice (FTA695, this year);</li>
 *   <li>{@code POST} (the collection) — Add New, returning the new file id;</li>
 *   <li>{@code GET /lookups/zones?districtNo=}, {@code GET /lookups/create-types}.</li>
 * </ul>
 *
 * <p>Reads are open to every role that reads range tenures; the writes to FTA_ADMIN,
 * FTA_RANGE_HEADQUARTERS_ADMIN and FTA_RANGE_DISTRICT_ADMIN (ApiAuthorizationCustomizer), each then
 * limited to the files of the user's districts (RangeFileAccess).
 */
@RestController
@RequestMapping("/api/fta/range-tenures")
public class RangeTenureTabController {

  private final RangeTenureTabService service;

  public RangeTenureTabController(RangeTenureTabService service) {
    this.service = service;
  }

  @GetMapping("/{forestFileId}/tenure-tab")
  public RangeTenureTabDto get(@PathVariable String forestFileId) {
    return service.get(forestFileId);
  }

  @PutMapping("/{forestFileId}/tenure-tab")
  public Result save(@PathVariable String forestFileId, @RequestBody Save request,
      JwtAuthenticationToken principal) {
    return service.save(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/{forestFileId}/tenure-tab/retire")
  public Result retire(@PathVariable String forestFileId, @RequestBody(required = false)
      Action request, JwtAuthenticationToken principal) {
    return service.retire(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/{forestFileId}/tenure-tab/cancel-replacement")
  public Result cancelReplacement(@PathVariable String forestFileId,
      @RequestBody(required = false) Action request, JwtAuthenticationToken principal) {
    return service.cancelReplacement(forestFileId, request,
        JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PostMapping("/{forestFileId}/special-conditions")
  public Result addCondition(@PathVariable String forestFileId, @RequestBody Condition request,
      JwtAuthenticationToken principal) {
    return service.saveCondition(forestFileId, null, request,
        JwtPrincipalUtil.getAuditUserId(principal));
  }

  @PutMapping("/{forestFileId}/special-conditions/{skey}")
  public Result updateCondition(@PathVariable String forestFileId, @PathVariable long skey,
      @RequestBody Condition request, JwtAuthenticationToken principal) {
    return service.saveCondition(forestFileId, skey, request,
        JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/{forestFileId}/special-conditions/{skey}")
  public Result deleteCondition(@PathVariable String forestFileId, @PathVariable long skey,
      @RequestParam(required = false) Integer revisionCount, JwtAuthenticationToken principal) {
    return service.deleteCondition(forestFileId, skey, revisionCount,
        JwtPrincipalUtil.getAuditUserId(principal));
  }

  @GetMapping("/{forestFileId}/invoice-preview")
  public InvoicePreviewDto invoicePreview(@PathVariable String forestFileId) {
    return service.invoicePreview(forestFileId);
  }

  @PostMapping
  public ResponseEntity<Created> create(@RequestBody Create request,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @GetMapping("/lookups/zones")
  public List<Zone> zones(@RequestParam(required = false) String districtNo) {
    return service.zonesFor(districtNo);
  }

  @GetMapping("/lookups/create-types")
  public List<CodeOptionDto> createTypes() {
    return service.createTypes();
  }
}
