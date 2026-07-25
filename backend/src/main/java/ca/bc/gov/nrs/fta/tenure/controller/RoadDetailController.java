package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.RoadDetailDto;
import ca.bc.gov.nrs.fta.tenure.service.RoadDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Road-section detail API — {@code GET /api/fta/roads/{roadId}}. The path id is
 * the {@code road_section_id}; the optional {@code forestFileId} query param
 * narrows to a specific owning file. Mirrors the legacy
 * {@code THE.FTA_131_ROADSECTION.get} inputs. Returns 404 when no section
 * matches.
 */
@RestController
@RequestMapping("/api/fta/roads")
public class RoadDetailController {

  private final RoadDetailService roadDetailService;

  public RoadDetailController(RoadDetailService roadDetailService) {
    this.roadDetailService = roadDetailService;
  }

  @GetMapping("/{roadId}")
  public ResponseEntity<RoadDetailDto> getRoad(
      @PathVariable String roadId,
      @RequestParam(required = false) String forestFileId) {
    return roadDetailService.getRoad(roadId, forestFileId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
