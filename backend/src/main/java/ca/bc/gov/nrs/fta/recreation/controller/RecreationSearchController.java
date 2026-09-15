package ca.bc.gov.nrs.fta.recreation.controller;

import ca.bc.gov.nrs.fta.recreation.dto.RecreationSearchCriteria;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationSearchDto;
import ca.bc.gov.nrs.fta.recreation.service.RecreationSearchService;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA007 Recreation Search API — {@code GET /api/fta/recreation}. Parameters
 * mirror {@code THE.FTA_007_REC_SEARCH.MAINLINE}.
 */
@RestController
@RequestMapping("/api/fta/recreation")
public class RecreationSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final RecreationSearchService recreationSearchService;

  public RecreationSearchController(RecreationSearchService recreationSearchService) {
    this.recreationSearchService = recreationSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<RecreationSearchDto>> search(
      @RequestParam(required = false) String orgUnit,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitNumber,
      @RequestParam(required = false) String fileId,
      @RequestParam(required = false) String fileStatus,
      @RequestParam(required = false) String fileStatusFrom,
      @RequestParam(required = false) String fileStatusTo,
      @RequestParam(required = false) String projectName,
      @RequestParam(required = false) String projectType,
      @RequestParam(required = false) String riskRating,
      @RequestParam(required = false) String controlledAccessType,
      @RequestParam(required = false) String maintenanceStandard,
      @RequestParam(required = false) String definedCampingSpaces,
      @RequestParam(required = false) String oldFileInd,
      @RequestParam(required = false) String recreationDistrict,
      @RequestParam(required = false) String resourceFeatureInd,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

    RecreationSearchCriteria criteria = new RecreationSearchCriteria(
        orgUnit, mgmtUnitType, mgmtUnitNumber, fileId, fileStatus, fileStatusFrom, fileStatusTo,
        projectName, projectType, riskRating, controlledAccessType, maintenanceStandard,
        definedCampingSpaces, oldFileInd, recreationDistrict, resourceFeatureInd, sortBy);

    // The one mandatory field, from the legacy form's RequiredFieldValidator.
    if (orgUnit == null || orgUnit.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin Org Unit is required.");
    }
    if (definedCampingSpaces != null && !definedCampingSpaces.isBlank()) {
      int spaces;
      try {
        spaces = Integer.parseInt(definedCampingSpaces.trim());
      } catch (NumberFormatException e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Defined Camping Spaces must be a whole number.");
      }
      // Legacy's form limits this to 1..200; its TO_NUMBER(x,'999') mask would
      // fail above 999 regardless.
      if (spaces < 1 || spaces > 200) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Defined Camping Spaces must be between 1 and 200.");
      }
    }
    if (isDateOutOfOrder(fileStatusFrom, fileStatusTo)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "File Status From must be on or before File Status To.");
    }

    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(recreationSearchService.search(criteria, safePage, safeSize));
  }

  private static boolean isDateOutOfOrder(String from, String to) {
    return from != null && !from.isBlank()
        && to != null && !to.isBlank()
        && from.trim().compareTo(to.trim()) > 0;
  }
}
