package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptClientDto;
import ca.bc.gov.nrs.fta.apt.service.AptClientService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** APT's client picker: {@code GET /api/fta/apt/clients} (the related-client search). */
@RestController
@RequestMapping("/api/fta/apt")
public class AptClientController {

  private final AptClientService clients;

  public AptClientController(AptClientService clients) {
    this.clients = clients;
  }

  /** Clients by partial number and/or name (APT2's APT_CLIENTS_GET search); one is required. */
  @GetMapping("/clients")
  public List<AptClientDto> search(
      @RequestParam(required = false) String clientNumber,
      @RequestParam(required = false) String clientName) {
    return clients.search(clientNumber, clientName);
  }
}
