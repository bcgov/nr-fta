package ca.bc.gov.nrs.fta.tenure.controller;

import ca.bc.gov.nrs.fta.tenure.service.HarvestAuthorizationLetterReport;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /api/fta/cutting-permits/{hvaSkey}/letter} — the FTA413 Harvest Authorization
 * letter of a Fort St. John authority, as a PDF (legacy FTA902's "Letter"). A read: who may call
 * it is {@code ApiAuthorizationCustomizer}.
 */
@RestController
@RequestMapping("/api/fta")
public class HarvestAuthorizationLetterController {

  static final String NO_LETTER =
      "There is no harvest authorization letter for this permit: it has no electronic"
          + " submission or no licensee.";

  private final HarvestAuthorizationLetterReport report;

  public HarvestAuthorizationLetterController(HarvestAuthorizationLetterReport report) {
    this.report = report;
  }

  @GetMapping("/cutting-permits/{hvaSkey}/letter")
  public ResponseEntity<byte[]> letter(
      @PathVariable long hvaSkey, JwtAuthenticationToken principal) {
    byte[] pdf = report.render(hvaSkey, JwtPrincipalUtil.getAuditUserId(principal));
    if (pdf == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_LETTER);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(ContentDisposition.attachment()
        .filename("FTA413-harvest-authorization-" + hvaSkey + ".pdf")
        .build());
    headers.setCacheControl("no-store");
    return ResponseEntity.ok().headers(headers).body(pdf);
  }
}
