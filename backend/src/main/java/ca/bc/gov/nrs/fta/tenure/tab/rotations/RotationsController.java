package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationsDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationsDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The range tenures' rotations, read only: Grazing rotation (legacy FTA611) and Hay cutting
 * rotation (FTA612), under {@code /api/fta/tenures/{forestFileId}/rotations}. Open to every
 * role that can open the tenure. The tenure page no longer shows rotations, so their writes
 * are gone.
 */
@RestController
@RequestMapping("/api/fta")
public class RotationsController {

  private static final String BASE = "/tenures/{forestFileId}/rotations";

  private final GrazingRotationService grazing;
  private final HayCuttingRotationService hay;

  public RotationsController(GrazingRotationService grazing, HayCuttingRotationService hay) {
    this.grazing = grazing;
    this.hay = hay;
  }

  /** The grazing tab for {@code year}, or legacy's default year. */
  @GetMapping(BASE + "/grazing")
  public GrazingRotationsDto grazing(
      @PathVariable String forestFileId, @RequestParam(required = false) Integer year) {
    return grazing.get(forestFileId, year);
  }

  /** The hay cutting tab for {@code year}, or legacy's default year. */
  @GetMapping(BASE + "/hay-cutting")
  public HayRotationsDto hayCutting(
      @PathVariable String forestFileId, @RequestParam(required = false) Integer year) {
    return hay.get(forestFileId, year);
  }
}
