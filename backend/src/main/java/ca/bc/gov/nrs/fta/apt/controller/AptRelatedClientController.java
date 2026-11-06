package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptRelatedClientCreateRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptRelatedClientDto;
import ca.bc.gov.nrs.fta.apt.service.AptRelatedClientService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Client relationships (linkages) — {@code /api/fta/apt/related-clients}. Reads are open to
 * every APT reader; the POST and DELETE need an APT administrator (ApiAuthorizationCustomizer).
 */
@RestController
@RequestMapping("/api/fta/apt/related-clients")
public class AptRelatedClientController {

  private final AptRelatedClientService relatedClients;

  public AptRelatedClientController(AptRelatedClientService relatedClients) {
    this.relatedClients = relatedClients;
  }

  @GetMapping
  public List<AptRelatedClientDto> list() {
    return relatedClients.list();
  }

  /**
   * 201 with the new relationship's keys; 400 for an unknown client number, 409 for a
   * relationship that exists already or a licensee that already has a parent.
   */
  @PostMapping
  public ResponseEntity<AptRelatedClientCreateRequest> create(
      @RequestBody AptRelatedClientCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(relatedClients.create(request));
  }

  /** Deletes the relationship the three keys name; 204 whether or not it existed (as APT2). */
  @DeleteMapping
  public ResponseEntity<Void> delete(
      @RequestParam String clientNumber,
      @RequestParam String parentClientNumber,
      @RequestParam String relationshipCode) {
    relatedClients.delete(clientNumber, parentClientNumber, relationshipCode);
    return ResponseEntity.noContent().build();
  }
}
