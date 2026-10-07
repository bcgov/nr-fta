package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.csv.CsvExport;
import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.service.ClientSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

/**
 * Client search API — {@code GET /api/fta/clients}. Parameters mirror the legacy
 * {@code THE.FTA_SIL_21_CLIENT_SEARCH_V002.get_client_search} search inputs.
 */
@RestController
@RequestMapping("/api/fta/clients")
public class ClientSearchController {

  /** Rows per page when the caller does not say; matches the frontend default. */
  private static final int DEFAULT_PAGE_SIZE = 10;

  /** Upper bound on page size so a hand-built request cannot ask for the world. */
  private static final int MAX_PAGE_SIZE = 100;

  private final ClientSearchService clientSearchService;

  public ClientSearchController(ClientSearchService clientSearchService) {
    this.clientSearchService = clientSearchService;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<ClientSearchDto>> search(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientAcronym,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String legalFirstName,
      @RequestParam(required = false) String legalMiddleName,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    return ResponseEntity.ok(clientSearchService.search(
        clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName,
        safePage, safeSize));
  }

  /** Most suggestions a type-ahead returns; enough to choose from, cheap to render. */
  private static final int SUGGEST_LIMIT = 20;

  /**
   * Type-ahead for the search screens' single client field: the clients and
   * locations matching what the user has typed so far.
   *
   * <p>Separate from the paged search because it answers a different question —
   * one typed value, a short list, no total — and because the browser calls it
   * on almost every keystroke.
   */
  @GetMapping("/suggest")
  public ResponseEntity<List<ClientSearchDto>> suggest(@RequestParam(required = false) String q) {
    return ResponseEntity.ok(clientSearchService.suggest(q, SUGGEST_LIMIT));
  }

  /**
   * Every matching client as a CSV download — the same criteria as the search,
   * with no paging, streamed as the rows arrive.
   */
  @GetMapping("/export")
  public ResponseEntity<StreamingResponseBody> exportCsv(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientAcronym,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String legalFirstName,
      @RequestParam(required = false) String legalMiddleName) {
    return CsvExport.response(
        "clients",
        csv -> clientSearchService.exportCsv(
            clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName, csv));
  }
}
