package ca.bc.gov.nrs.fta.recreation.controller;

import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Permissions;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationProjectDetailDto;
import ca.bc.gov.nrs.fta.recreation.service.RecreationProjectService;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA701 Recreation Project API — {@code GET /api/fta/recreation/{fileId}}.
 *
 * <p>Legacy identifies the project through session state with no URL parameter;
 * here it is a path variable, matching the other detail screens in this
 * application.
 */
@RestController
@RequestMapping("/api/fta/recreation")
public class RecreationProjectController {

  private final RecreationProjectService recreationProjectService;
  private final LoggedUserHelper auth;

  public RecreationProjectController(
      RecreationProjectService recreationProjectService, LoggedUserHelper auth) {
    this.recreationProjectService = recreationProjectService;
    this.auth = auth;
  }

  @GetMapping("/{fileId}")
  public ResponseEntity<RecreationProjectDetailDto> detail(@PathVariable String fileId) {
    if (!recreationProjectService.isRecreationFile(fileId)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Not a recreation file. Recreation projects have a file type of F and an id "
              + "beginning REC; older 900 files are held on the tenure screen.");
    }
    return ResponseEntity.ok(
        recreationProjectService.findDetail(fileId, permissionsFor(fileId)));
  }

  /**
   * What the current user may change on this file.
   *
   * <p>Mirrors {@code THE.FTA_RECREATION_SECURITY} against the roles this
   * application actually has. Legacy distinguishes Recreation headquarters from
   * recreation staff; here an <em>unscoped</em> {@code FTA_ADMIN} plays the
   * headquarters part (may save any file) and a district-scoped one plays the
   * staff part (may save only files their district administers).
   *
   * <p>The two hard rules are unchanged and are checked in SQL: the file must be
   * in {@code HI} status and the project must have a spatial description. Child
   * records additionally need the parent project row to exist.
   *
   * <p>Legacy also locks the Project Established date and the establishment
   * order to headquarters; the unscoped administrator inherits that.
   */
  private Permissions permissionsFor(String fileId) {
    String adminOrgCode = recreationProjectService.saveableAdminOrgCode(fileId);
    boolean fileIsSaveable = adminOrgCode != null;
    boolean mayEdit = fileIsSaveable && auth.administersDistrict(adminOrgCode);

    boolean parentSave = mayEdit;
    boolean childSave = mayEdit && recreationProjectService.projectExists(fileId);
    boolean establishedLocked = !auth.isAdmin();

    return new Permissions(parentSave, childSave, establishedLocked);
  }
}
