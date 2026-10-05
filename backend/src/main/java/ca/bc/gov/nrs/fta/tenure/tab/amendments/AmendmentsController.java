package ca.bc.gov.nrs.fta.tenure.tab.amendments;

import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsBlockDetailDto;
import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsListDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenure CP/CB amendments tab API (legacy FTA905, read-only):
 * {@code GET /api/fta/tenures/{forestFileId}/cp-cb-amendments} (the amended blocks with their
 * totals) and {@code GET .../cp-cb-amendments/blocks/{cbSkey}} (one block's amendments).
 */
@RestController
@RequestMapping("/api/fta/tenures")
public class AmendmentsController {

  private final AmendmentsService amendmentsService;

  public AmendmentsController(AmendmentsService amendmentsService) {
    this.amendmentsService = amendmentsService;
  }

  @GetMapping("/{forestFileId}/cp-cb-amendments")
  public AmendmentsListDto list(@PathVariable String forestFileId) {
    return amendmentsService.list(forestFileId);
  }

  @GetMapping("/{forestFileId}/cp-cb-amendments/blocks/{cbSkey}")
  public AmendmentsBlockDetailDto block(
      @PathVariable String forestFileId, @PathVariable long cbSkey) {
    return amendmentsService.block(forestFileId, cbSkey);
  }
}
