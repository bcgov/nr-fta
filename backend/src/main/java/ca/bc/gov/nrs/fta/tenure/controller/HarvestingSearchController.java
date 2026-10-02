package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.shared.csv.CsvExport;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchDto;
import ca.bc.gov.nrs.fta.tenure.service.HarvestingSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpStatus;

/**
 * FTA005 Harvesting Authority search API —
 * {@code GET /api/fta/harvesting-authorities}. Parameters mirror the standalone
 * procedure {@code THE.FTA_005_HVA_SEARCH}.
 */
@RestController
@RequestMapping("/api/fta/harvesting-authorities")
public class HarvestingSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  /**
   * The minimum number of criteria a non-key search needs, from
   * {@code HarvestingAuthoritySearchBOImpl}.
   */
  private static final int MIN_CRITERIA = 2;

  /**
   * The oil and gas file type. Legacy's {@code getShowOilAndGasColumns} shows the
   * five oil and gas result columns when this file type is chosen, whether or not
   * the "only oil and gas" box is ticked — only the box filters rows.
   */
  private static final String OIL_AND_GAS_FILE_TYPE = "A11";

  private final HarvestingSearchService harvestingSearchService;

  public HarvestingSearchController(HarvestingSearchService harvestingSearchService) {
    this.harvestingSearchService = harvestingSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<HarvestingSearchDto>> search(
      @RequestParam(required = false) String forestDistrict,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String hvaId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String harvestAuthStatusCode,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocationCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String clientTypeCode,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String salvageTypeCode,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String invoiceNumber,
      @RequestParam(required = false) String searchOnlyOg,
      @RequestParam(required = false) String ogcNumber,
      @RequestParam(required = false) String geographicIdentifier,
      @RequestParam(required = false) String purposeCode,
      @RequestParam(required = false) String ntsQuarter,
      @RequestParam(required = false) String ntsMapUnit,
      @RequestParam(required = false) String ntsMapBlock,
      @RequestParam(required = false) String ntsMapsheetGrid,
      @RequestParam(required = false) String ntsMapsheetLetter,
      @RequestParam(required = false) String ntsMapsheetSquare,
      @RequestParam(required = false) String sortBy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

    HarvestingSearchCriteria criteria = new HarvestingSearchCriteria(
        forestDistrict, mgmtUnitType, mgmtUnitId, forestFileId, cuttingPermitId, timberMark,
        hvaId, fileTypeCode, harvestAuthStatusCode, clientNumber, clientLocationCode, clientName,
        clientTypeCode, issueDateFrom, issueDateTo, expiryDateFrom, expiryDateTo, salvageTypeCode,
        zone, invoiceNumber, searchOnlyOg, ogcNumber, geographicIdentifier, purposeCode,
        ntsQuarter, ntsMapUnit, ntsMapBlock, ntsMapsheetGrid, ntsMapsheetLetter,
        ntsMapsheetSquare, sortBy);

    criteria = validated(criteria);

    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(harvestingSearchService.search(criteria, safePage, safeSize));
  }

  /**
   * Every matching harvesting authority as a CSV download — the same criteria as
   * the search, with no paging, streamed as the rows arrive.
   *
   * <p>The criteria go through the same key-search rewriting and validation as
   * the search, so the file covers exactly the result set the table is paging
   * through.
   */
  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> exportCsv(
      @RequestParam(required = false) String forestDistrict,
      @RequestParam(required = false) String mgmtUnitType,
      @RequestParam(required = false) String mgmtUnitId,
      @RequestParam(required = false) String forestFileId,
      @RequestParam(required = false) String cuttingPermitId,
      @RequestParam(required = false) String timberMark,
      @RequestParam(required = false) String hvaId,
      @RequestParam(required = false) String fileTypeCode,
      @RequestParam(required = false) String harvestAuthStatusCode,
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientLocationCode,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String clientTypeCode,
      @RequestParam(required = false) String issueDateFrom,
      @RequestParam(required = false) String issueDateTo,
      @RequestParam(required = false) String expiryDateFrom,
      @RequestParam(required = false) String expiryDateTo,
      @RequestParam(required = false) String salvageTypeCode,
      @RequestParam(required = false) String zone,
      @RequestParam(required = false) String invoiceNumber,
      @RequestParam(required = false) String searchOnlyOg,
      @RequestParam(required = false) String ogcNumber,
      @RequestParam(required = false) String geographicIdentifier,
      @RequestParam(required = false) String purposeCode,
      @RequestParam(required = false) String ntsQuarter,
      @RequestParam(required = false) String ntsMapUnit,
      @RequestParam(required = false) String ntsMapBlock,
      @RequestParam(required = false) String ntsMapsheetGrid,
      @RequestParam(required = false) String ntsMapsheetLetter,
      @RequestParam(required = false) String ntsMapsheetSquare,
      @RequestParam(required = false) String sortBy) {

    HarvestingSearchCriteria criteria = new HarvestingSearchCriteria(
        forestDistrict, mgmtUnitType, mgmtUnitId, forestFileId, cuttingPermitId, timberMark,
        hvaId, fileTypeCode, harvestAuthStatusCode, clientNumber, clientLocationCode, clientName,
        clientTypeCode, issueDateFrom, issueDateTo, expiryDateFrom, expiryDateTo, salvageTypeCode,
        zone, invoiceNumber, searchOnlyOg, ogcNumber, geographicIdentifier, purposeCode,
        ntsQuarter, ntsMapUnit, ntsMapBlock, ntsMapsheetGrid, ntsMapsheetLetter,
        ntsMapsheetSquare, sortBy);

    // Decided from the criteria as submitted, before applyKeySearch clears them:
    // the page decides its columns from the submitted form the same way, so this
    // is the only reading that gives the file the columns on screen.
    boolean showOilAndGas =
        "Y".equals(searchOnlyOg) || OIL_AND_GAS_FILE_TYPE.equals(fileTypeCode);

    HarvestingSearchCriteria effective = validated(criteria);

    return CsvExport.response(
        "harvesting-authorities",
        csv -> harvestingSearchService.exportCsv(effective, showOilAndGas, csv));
  }

  /**
   * The criteria a request actually runs with: legacy's key-search rewriting,
   * then the file-key combination check.
   *
   * <p>Shared by the search and the export so the export can never resolve to a
   * different result set than the table it mirrors.
   */
  private HarvestingSearchCriteria validated(HarvestingSearchCriteria criteria) {
    HarvestingSearchCriteria effective = applyKeySearch(criteria);

    String validationError = harvestingSearchService.validateFileKeys(
        effective.forestFileId(), effective.cuttingPermitId(), effective.hvaId());
    if (validationError != null && !validationError.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validationError);
    }
    return effective;
  }

  /**
   * Applies the legacy key-search rules.
   *
   * <p>From {@code HarvestingAuthoritySearchBOImpl.validateKeySearch}: if a
   * valid timber mark, or a file id with a cutting permit, or a file id with an
   * HVA id is supplied, then <em>every other criterion is ignored</em>. Legacy
   * does this by building a brand-new empty criteria object holding only the key
   * fields, which is what the fresh records below reproduce — a narrowing
   * {@code AND} would not be equivalent.
   *
   * <p>All three paths force {@code searchOnlyOg} to {@code N}, so a key search
   * never shows the oil and gas columns.
   *
   * <p>The two guards are legacy's as well: a cutting permit or HVA id without a
   * file id is an error, and supplying both of them together is an error.
   */
  private HarvestingSearchCriteria applyKeySearch(HarvestingSearchCriteria c) {
    boolean hasFile = hasValue(c.forestFileId());
    boolean hasCp = hasValue(c.cuttingPermitId());
    boolean hasHva = hasValue(c.hvaId());

    if (!hasFile && (hasCp || hasHva)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "A Cutting Permit or HVA ID can only be used together with a File ID.");
    }
    if (hasFile && hasCp && hasHva) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Supply either a Cutting Permit or an HVA ID with the File ID, not both.");
    }

    // A timber mark is unique, so legacy checks it first — but only a mark that
    // validates against the database short-circuits; an invalid one falls
    // through to an ordinary search.
    if (hasValue(c.timberMark()) && harvestingSearchService.isTimberMarkValid(c.timberMark())) {
      return keyCriteria(null, null, null, c.timberMark(), c.sortBy());
    }
    if (hasFile && hasCp) {
      return keyCriteria(c.forestFileId(), c.cuttingPermitId(), null, null, c.sortBy());
    }
    if (hasFile && hasHva) {
      return keyCriteria(c.forestFileId(), null, c.hvaId(), null, c.sortBy());
    }

    if (countCriteria(c) < MIN_CRITERIA) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Please enter at least " + MIN_CRITERIA + " search criteria.");
    }
    if (!hasValue(c.forestDistrict())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "District is required unless you search by a key.");
    }
    return c;
  }

  /** Builds the stripped-down criteria a key search runs with. */
  private static HarvestingSearchCriteria keyCriteria(
      String forestFileId, String cuttingPermitId, String hvaId, String timberMark,
      String sortBy) {
    return new HarvestingSearchCriteria(
        null, null, null, forestFileId, cuttingPermitId, timberMark, hvaId, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        "N", null, null, null, null, null, null, null, null, null, sortBy);
  }

  /**
   * Counts the supplied criteria. Mirrors legacy's {@code getCriteriaCount},
   * where the "Only Oil and Gas" checkbox counts as one criterion and the sort
   * does not count at all.
   */
  private static int countCriteria(HarvestingSearchCriteria c) {
    String[] fields = {
        c.forestDistrict(), c.mgmtUnitType(), c.mgmtUnitId(), c.forestFileId(),
        c.cuttingPermitId(), c.timberMark(), c.hvaId(), c.fileTypeCode(),
        c.harvestAuthStatusCode(), c.clientNumber(), c.clientLocationCode(), c.clientName(),
        c.clientTypeCode(), c.issueDateFrom(), c.issueDateTo(), c.expiryDateFrom(),
        c.expiryDateTo(), c.salvageTypeCode(), c.zone(), c.invoiceNumber(), c.ogcNumber(),
        c.geographicIdentifier(), c.purposeCode(), c.ntsQuarter(), c.ntsMapUnit(),
        c.ntsMapBlock(), c.ntsMapsheetGrid(), c.ntsMapsheetLetter(), c.ntsMapsheetSquare(),
    };
    int count = 0;
    for (String f : fields) {
      if (hasValue(f)) {
        count++;
      }
    }
    if ("Y".equals(c.searchOnlyOg())) {
      count++;
    }
    return count;
  }

  private static boolean hasValue(String s) {
    return s != null && !s.isBlank();
  }
}
