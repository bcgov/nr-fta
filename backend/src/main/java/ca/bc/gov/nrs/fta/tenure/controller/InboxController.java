package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.dto.InboxDto;
import ca.bc.gov.nrs.fta.tenure.service.InboxService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FTA300 Inbox worklist API — {@code GET /api/fta/inbox}. Parameters mirror the
 * legacy {@code THE.FTA_300N_INBOX} inbox ({@code GET}) filter inputs; each is
 * optional and applied only when supplied.
 */
@RestController
@RequestMapping("/api/fta/inbox")
public class InboxController {

  private final InboxService inboxService;

  public InboxController(InboxService inboxService) {
    this.inboxService = inboxService;
  }

  @GetMapping
  public ResponseEntity<List<InboxDto>> search(
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String orgUnit,
      @RequestParam(required = false) String applTypeCode,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocnCode,
      @RequestParam(required = false) String harvestTypeCode,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo,
      @RequestParam(required = false) String exACleared,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String msrmInd,
      @RequestParam(required = false) String mapTechInd,
      @RequestParam(required = false) String recStaffInd,
      @RequestParam(required = false) String seniorAdminInd,
      @RequestParam(required = false) String excludeSrmInd,
      @RequestParam(required = false) String userOrgUnitNo) {
    return ResponseEntity.ok(
        inboxService.search(
            forestFileId,
            orgUnit,
            applTypeCode,
            fileTypeCode,
            clientNumber,
            clientLocnCode,
            harvestTypeCode,
            dateFrom,
            dateTo,
            exACleared,
            sortBy,
            msrmInd,
            mapTechInd,
            recStaffInd,
            seniorAdminInd,
            excludeSrmInd,
            userOrgUnitNo));
  }
}
