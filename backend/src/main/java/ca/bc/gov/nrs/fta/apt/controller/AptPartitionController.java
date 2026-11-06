package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionRequest;
import ca.bc.gov.nrs.fta.apt.service.AptPartitionService;
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

/** APT2's partitions API (APTM2100) under {@code /api/fta/apt/partitions}. */
@RestController
@RequestMapping("/api/fta/apt/partitions")
public class AptPartitionController {

  private final AptPartitionService partitions;

  public AptPartitionController(AptPartitionService partitions) {
    this.partitions = partitions;
  }

  /** By partition id or by management unit history; one of the two is required. */
  @GetMapping
  public List<AptPartitionDto> list(@RequestParam(required = false) Long id,
      @RequestParam(required = false) Long managementUnitHistoryId) {
    return partitions.list(id, managementUnitHistoryId);
  }

  @PostMapping
  public ResponseEntity<AptPartitionDto> create(@RequestBody AptPartitionRequest body,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(partitions.create(body, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/{id}")
  public AptPartitionDto update(@PathVariable Long id, @RequestBody AptPartitionRequest body,
      JwtAuthenticationToken principal) {
    return partitions.update(id, body, JwtPrincipalUtil.getAuditUserId(principal));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    partitions.delete(id);
    return ResponseEntity.noContent().build();
  }
}
