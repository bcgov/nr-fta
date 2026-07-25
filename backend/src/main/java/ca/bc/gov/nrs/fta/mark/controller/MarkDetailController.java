package ca.bc.gov.nrs.fta.mark.controller;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.service.MarkDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Private Mark detail API — {@code GET /api/fta/marks/{markNumber}}. The path id
 * is the timber mark; the response mirrors the legacy
 * {@code THE.FTA_510_PRIVATE_MARK.GET} record enriched with the land index
 * (FTA_511), associated clients (FTA_513) and amendment history.
 */
@RestController
@RequestMapping("/api/fta/marks")
public class MarkDetailController {

  private final MarkDetailService markDetailService;

  public MarkDetailController(MarkDetailService markDetailService) {
    this.markDetailService = markDetailService;
  }

  @GetMapping("/{markNumber}")
  public ResponseEntity<MarkDetailDto> getMark(@PathVariable String markNumber) {
    return markDetailService.findByMarkNumber(markNumber)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
