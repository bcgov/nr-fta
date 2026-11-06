package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentRequest;
import ca.bc.gov.nrs.fta.apt.service.AptPartitionApportionmentService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * APT2's partition apportionments API (APTM2200) under
 * {@code /api/fta/apt/partition-apportionments}.
 */
@RestController
@RequestMapping("/api/fta/apt/partition-apportionments")
public class AptPartitionApportionmentController {

  private final AptPartitionApportionmentService apportionments;

  public AptPartitionApportionmentController(AptPartitionApportionmentService apportionments) {
    this.apportionments = apportionments;
  }

  /** By apportionment id or by partition; one of the two is required. */
  @GetMapping
  public List<AptPartitionApportionmentDto> list(@RequestParam(required = false) Long id,
      @RequestParam(required = false) Long partitionId) {
    return apportionments.list(id, partitionId);
  }

  @PostMapping
  public ResponseEntity<AptPartitionApportionmentDto> create(
      @RequestBody AptPartitionApportionmentRequest body, JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(apportionments.create(body, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/{id}")
  public AptPartitionApportionmentDto update(@PathVariable Long id,
      @RequestBody AptPartitionApportionmentRequest body, JwtAuthenticationToken principal) {
    return apportionments.update(id, body, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    apportionments.delete(id);
    return ResponseEntity.noContent().build();
  }
}
