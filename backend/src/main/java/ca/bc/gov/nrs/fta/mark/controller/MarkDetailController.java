package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.service.MarkDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private Mark detail API — {@code GET /api/fta/marks/{markNumber}}. The path id
 * is the timber mark; the response mirrors the legacy
 * {@code THE.FTA_510_PRIVATE_MARK.GET} record enriched with the land index
 * (FTA_511), associated clients (FTA_513) and amendment history.
 *
 * <p>An application that has not been issued has no timber mark yet, only a
 * certificate; {@code ?by=certificate} reads the path id as that certificate.
 * The two are separate keys rather than one lookup over both columns, so a
 * certificate can never resolve to an unrelated mark that happens to share its
 * characters.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkDetailController {

  private final MarkDetailService markDetailService;

  public MarkDetailController(MarkDetailService markDetailService) {
    this.markDetailService = markDetailService;
  }

  @GetMapping("/{markNumber}")
  public ResponseEntity<MarkDetailDto> getMark(
      @PathVariable String markNumber,
      @RequestParam(required = false) String by) {
    var mark = "certificate".equals(by)
        ? markDetailService.findByCertificate(markNumber)
        : markDetailService.findByMarkNumber(markNumber);
    return mark
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
