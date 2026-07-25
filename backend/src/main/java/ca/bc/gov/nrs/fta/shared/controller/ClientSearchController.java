package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.ClientSearchDto;
import ca.bc.gov.nrs.fta.shared.service.ClientSearchService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client search API — {@code GET /api/fta/clients}. Parameters mirror the legacy
 * {@code THE.FTA_SIL_21_CLIENT_SEARCH_V002.get_client_search} search inputs.
 */
@RestController
@RequestMapping("/api/fta/clients")
public class ClientSearchController {

  private final ClientSearchService clientSearchService;

  public ClientSearchController(ClientSearchService clientSearchService) {
    this.clientSearchService = clientSearchService;
  }

  @GetMapping
  public ResponseEntity<List<ClientSearchDto>> search(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientAcronym,
      @RequestParam(required = false) String clientName,
      @RequestParam(required = false) String legalFirstName,
      @RequestParam(required = false) String legalMiddleName) {
    return ResponseEntity.ok(
        clientSearchService.search(
            clientNumber, clientAcronym, clientName, legalFirstName, legalMiddleName));
  }
}
