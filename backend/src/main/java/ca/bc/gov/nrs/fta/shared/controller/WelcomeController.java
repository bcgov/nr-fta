package ca.bc.gov.nrs.fta.shared.controller;

import ca.bc.gov.nrs.fta.shared.dto.WelcomeDto;
import ca.bc.gov.nrs.fta.shared.dto.WelcomeDto.BusinessArea;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-cutting endpoint that advertises the application and the business
 * lines it serves. Backs the SPA dashboard's area tiles.
 */
@RestController
@RequestMapping("/api/fta")
public class WelcomeController {

  @GetMapping("/welcome")
  public ResponseEntity<WelcomeDto> welcome() {
    return ResponseEntity.ok(new WelcomeDto(
        "Forest Tenure Administration",
        "Administer forest tenures, range agreements, and private timber marks.",
        List.of(
            new BusinessArea("tenures", "Harvest Authorizations & Tenures", "/tenures"),
            new BusinessArea("range", "Range", "/range"),
            new BusinessArea("marks", "Private Timber Marks", "/marks"))));
  }
}
