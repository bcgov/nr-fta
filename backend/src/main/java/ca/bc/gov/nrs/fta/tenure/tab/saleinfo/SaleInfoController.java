package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoResponse;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoSaveResult;
import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoUpdateRequest;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenure detail's Sale info tab — legacy FTA940. Reads are open to every role that can
 * open the tenure; the save is FTA_ADMIN by the generic write rule. The edit form's code lists
 * are under {@code /api/fta/tenure-lookups/sale-info-*}.
 */
@RestController
@RequestMapping("/api/fta")
public class SaleInfoController {

  private final SaleInfoService service;

  public SaleInfoController(SaleInfoService service) {
    this.service = service;
  }

  /** {@code GET /api/fta/tenures/{forestFileId}/sale-info} — the record and its rules. */
  @GetMapping("/tenures/{forestFileId}/sale-info")
  public SaleInfoResponse get(@PathVariable String forestFileId) {
    return service.get(forestFileId);
  }

  /**
   * {@code PUT …/sale-info} — save the record (creating it when it does not exist yet), with
   * the revision counts from the GET. Returns legacy's non-blocking warnings.
   */
  @PutMapping("/tenures/{forestFileId}/sale-info")
  public SaleInfoSaveResult save(
      @PathVariable String forestFileId,
      @RequestBody SaleInfoUpdateRequest request,
      JwtAuthenticationToken principal) {
    return service.save(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /**
   * {@code GET /api/fta/tenure-lookups/…}: {@code sale-info-sale-methods},
   * {@code sale-info-sale-types}, {@code sale-info-deposit-types} and
   * {@code sale-info-bcts-categories}.
   */
  @GetMapping({
    "/tenure-lookups/sale-info-sale-methods",
    "/tenure-lookups/sale-info-sale-types",
    "/tenure-lookups/sale-info-deposit-types",
    "/tenure-lookups/sale-info-bcts-categories"
  })
  public List<CodeOptionDto> lookup(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return service.lookup(uri.substring(uri.lastIndexOf('/') + 1));
  }
}
