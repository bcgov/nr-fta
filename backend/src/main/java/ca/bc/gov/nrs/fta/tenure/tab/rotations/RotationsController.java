package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.CopyRotationResult;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingProvisionRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.GrazingRotationsDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HayRotationsDto;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HaySaveRequest;
import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsDtos.HaySaveResult;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
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
 * The tenure detail's rotation tabs (range tenures): Grazing rotation (legacy FTA611), Hay
 * cutting rotation (FTA612) and Copy rotation (FTA613), under
 * {@code /api/fta/tenures/{forestFileId}/rotations}, plus the Animal dropdown at
 * {@code /api/fta/tenure-lookups/livestock-codes}. GETs are open to every role that can open
 * the tenure; writes are FTA_ADMIN by the generic write rule. The audit user id comes from
 * the JWT.
 */
@RestController
@RequestMapping("/api/fta")
public class RotationsController {

  private static final String BASE = "/tenures/{forestFileId}/rotations";

  private final GrazingRotationService grazing;
  private final HayCuttingRotationService hay;
  private final CopyRotationService copy;

  public RotationsController(
      GrazingRotationService grazing, HayCuttingRotationService hay, CopyRotationService copy) {
    this.grazing = grazing;
    this.hay = hay;
    this.copy = copy;
  }

  /** The grazing tab for {@code year}, or legacy's default year. */
  @GetMapping(BASE + "/grazing")
  public GrazingRotationsDto grazing(
      @PathVariable String forestFileId, @RequestParam(required = false) Integer year) {
    return grazing.get(forestFileId, year);
  }

  /** Save Provision: the year's range provision header. */
  @PutMapping(BASE + "/grazing/{year}/provision")
  public ResponseEntity<Void> saveGrazingProvision(
      @PathVariable String forestFileId,
      @PathVariable int year,
      @RequestBody GrazingProvisionRequest request,
      JwtAuthenticationToken principal) {
    grazing.saveProvision(forestFileId, year, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** Add a livestock rotation to the year. */
  @PostMapping(BASE + "/grazing/{year}")
  public ResponseEntity<Void> addGrazingRotation(
      @PathVariable String forestFileId,
      @PathVariable int year,
      @RequestBody GrazingRotationRequest request,
      JwtAuthenticationToken principal) {
    grazing.add(forestFileId, year, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** Change a livestock rotation ({@code revisionCount} in the body). */
  @PutMapping(BASE + "/grazing/{year}/{skey}")
  public ResponseEntity<Void> updateGrazingRotation(
      @PathVariable String forestFileId,
      @PathVariable int year,
      @PathVariable long skey,
      @RequestBody GrazingRotationRequest request,
      JwtAuthenticationToken principal) {
    grazing.update(
        forestFileId, year, skey, request, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** Delete a livestock rotation. */
  @DeleteMapping(BASE + "/grazing/{year}/{skey}")
  public ResponseEntity<Void> deleteGrazingRotation(
      @PathVariable String forestFileId,
      @PathVariable int year,
      @PathVariable long skey,
      @RequestParam long revisionCount,
      JwtAuthenticationToken principal) {
    grazing.delete(
        forestFileId, year, skey, revisionCount, JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }

  /** The hay cutting tab for {@code year}, or legacy's default year. */
  @GetMapping(BASE + "/hay-cutting")
  public HayRotationsDto hayCutting(
      @PathVariable String forestFileId, @RequestParam(required = false) Integer year) {
    return hay.get(forestFileId, year);
  }

  /** Save the year's provision and meadow rotations in one go. */
  @PutMapping(BASE + "/hay-cutting/{year}")
  public HaySaveResult saveHayCutting(
      @PathVariable String forestFileId,
      @PathVariable int year,
      @RequestBody HaySaveRequest request,
      JwtAuthenticationToken principal) {
    return hay.save(forestFileId, year, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** The copy tab: whether it applies, the kind of rotations, the term. */
  @GetMapping(BASE + "/copy")
  public CopyRotationDto copyTab(@PathVariable String forestFileId) {
    return copy.get(forestFileId);
  }

  /**
   * Copy a source year onto target years. Returns {@code copied: false} with a message, and
   * writes nothing, when target years already have rotations and {@code overwrite} is false.
   */
  @PostMapping(BASE + "/copy")
  public CopyRotationResult copyRotations(
      @PathVariable String forestFileId,
      @RequestBody CopyRotationRequest request,
      JwtAuthenticationToken principal) {
    return copy.copy(forestFileId, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** The current livestock codes — the grazing dialog's Animal list. */
  @GetMapping("/tenure-lookups/livestock-codes")
  public List<CodeOptionDto> livestockCodes() {
    return grazing.livestockCodes();
  }
}
