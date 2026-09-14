package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.service.ClientSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
