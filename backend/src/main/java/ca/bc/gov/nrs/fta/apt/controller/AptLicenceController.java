package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceSearchParams;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceUpdateRequest;
import ca.bc.gov.nrs.fta.apt.service.AptCommitmentService;
import ca.bc.gov.nrs.fta.apt.service.AptLicenceService;
import ca.bc.gov.nrs.fta.shared.csv.CsvExport;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * APT licences (APT2's APTM4000/4200) — {@code /api/fta/apt/licences}. Reads are open to every
 * reading role; the update needs an APT administrator (ApiAuthorizationCustomizer).
 */
@RestController
@RequestMapping("/api/fta/apt/licences")
public class AptLicenceController {

  private final AptLicenceService licences;
  private final AptCommitmentService commitments;

  public AptLicenceController(AptLicenceService licences, AptCommitmentService commitments) {
    this.licences = licences;
    this.commitments = commitments;
  }

  /** One page of licences ({@code APT_LICENCES_GET}); any one criterion will do. */
  @GetMapping
  public PagedResponse<AptLicenceDto> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String orgUnitName,
      @RequestParam(required = false) String managementUnitName,
      @RequestParam(required = false) String managementUnitType,
      @RequestParam(required = false) String managementUnitNumber,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return licences.search(new AptLicenceSearchParams(forestFileId, clientName, orgUnitName,
        managementUnitName, managementUnitType, managementUnitNumber), page, size, sort);
  }

  /**
   * Every licence the search finds, in its column sort, as a CSV download: the results
   * table's columns, plus the licence's management unit type and number and its controlling
   * company. Read before the response starts, so a refused search is still an HTTP error.
   */
  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> export(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String orgUnitName,
      @RequestParam(required = false) String managementUnitName,
      @RequestParam(required = false) String managementUnitType,
      @RequestParam(required = false) String managementUnitNumber,
      @RequestParam(required = false) String sort) {
    List<AptLicenceDto> rows = licences.export(new AptLicenceSearchParams(forestFileId,
        clientName, orgUnitName, managementUnitName, managementUnitType, managementUnitNumber),
        sort);
    return CsvExport.response("apt-licences", csv -> {
      csv.writeRow("Forest File ID", "Licensee", "Controlling Company", "Management Unit",
          "Management Unit Type", "Management Unit No.", "Org. Unit", "File Status",
          "File Status Date");
      rows.forEach(l -> csv.writeRow(l.forestFileId(), l.clientName(), l.controlCompany(),
          l.mgmtUnitName(), l.aptMgmtUnitSt(), l.mgmtUnitNumber(), l.orgUnitName(),
          l.fileStatus() != null ? l.fileStatus() : l.fileStatusSt(), l.fileStatusDate()));
    });
  }

  @GetMapping("/{forestFileId}")
  public AptLicenceDto get(@PathVariable String forestFileId) {
    return licences.get(forestFileId);
  }

  /** Sets the licence's FOA type, replacement and comments ({@code APT_LICENCE_INS_UPD}). */
  @PutMapping("/{forestFileId}")
  public AptLicenceDto update(@PathVariable String forestFileId,
      @RequestBody AptLicenceUpdateRequest request, JwtAuthenticationToken principal) {
    return licences.update(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** The licence's commitments ({@code APT_COMMITMENTS_GET_BY_FFID}). */
  @GetMapping("/{forestFileId}/commitments")
  public List<AptCommitmentDto> commitments(@PathVariable String forestFileId) {
    return commitments.byLicence(forestFileId);
  }
}
