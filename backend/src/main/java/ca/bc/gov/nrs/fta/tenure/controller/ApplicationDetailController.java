package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.ApplicationDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.ApplicationDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure-application detail API — {@code GET /api/fta/applications/{esfId}}.
 * The path id and optional filter mirror the legacy
 * {@code THE.FTA_952X_TAMF_DET.mainline} header inputs.
 */
@RestController
@RequestMapping("/api/fta/applications")
public class ApplicationDetailController {

  private final ApplicationDetailService applicationDetailService;

  public ApplicationDetailController(ApplicationDetailService applicationDetailService) {
    this.applicationDetailService = applicationDetailService;
  }

  @GetMapping("/{esfId}")
  public ResponseEntity<ApplicationDetailDto> findByEsfId(
      @PathVariable String esfId,
      @RequestParam(required = false) String forestFileId) {
    ApplicationDetailDto detail = applicationDetailService.findByEsfId(esfId, forestFileId);
    return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
  }
}
