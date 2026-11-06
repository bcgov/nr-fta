package ca.bc.gov.nrs.fta.apt.controller;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDeleteRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentHistoryDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentRequest;
import ca.bc.gov.nrs.fta.apt.service.AptCommitmentService;
import ca.bc.gov.nrs.fta.util.JwtPrincipalUtil;
import java.util.List;
import java.util.stream.Stream;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * APT commitments (APT2's APTM6000/6200/6300) — {@code /api/fta/apt/commitments}. Reads are
 * open to every reading role; writes need an APT administrator (ApiAuthorizationCustomizer).
 * The journal's user is always the caller's token.
 */
@RestController
@RequestMapping("/api/fta/apt/commitments")
public class AptCommitmentController {

  private final AptCommitmentService commitments;

  public AptCommitmentController(AptCommitmentService commitments) {
    this.commitments = commitments;
  }

  /**
   * Commitments of a management unit's licences ({@code APT_COMMITMENTS_GET_BY_MU}). At least
   * one criterion is needed; with none the function returns every commitment.
   *
   * @param managementUnitNumber the unit's number (APT2's {@code management_unit_id})
   */
  @GetMapping
  public List<AptCommitmentDto> search(
      @RequestParam(required = false) String managementUnitType,
      @RequestParam(required = false) String managementUnitNumber,
      @RequestParam(required = false) String foaType,
      @RequestParam(required = false) String partitionType) {
    boolean none = Stream.of(managementUnitType, managementUnitNumber, foaType, partitionType)
        .allMatch(s -> s == null || s.isBlank());
    if (none) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Give a management unit, FOA type or partition type.");
    }
    return commitments.searchByManagementUnit(managementUnitType, managementUnitNumber, foaType,
        partitionType);
  }

  @GetMapping("/{id}")
  public AptCommitmentDto get(@PathVariable long id) {
    return commitments.get(id);
  }

  /** The commitment's journal, newest first, or the one entry {@code rowId} names. */
  @GetMapping("/{id}/history")
  public List<AptCommitmentHistoryDto> history(@PathVariable long id,
      @RequestParam(required = false) String rowId) {
    return commitments.history(id, rowId);
  }

  @PostMapping
  public ResponseEntity<AptCommitmentDto> create(@RequestBody AptCommitmentRequest request,
      JwtAuthenticationToken principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commitments.create(request, JwtPrincipalUtil.getAuditUserId(principal)));
  }

  @PutMapping("/{id}")
  public AptCommitmentDto update(@PathVariable long id,
      @RequestBody AptCommitmentRequest request, JwtAuthenticationToken principal) {
    return commitments.update(id, request, JwtPrincipalUtil.getAuditUserId(principal));
  }

  /** Deletes the commitment; the body's reason, if any, goes to its journal. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id,
      @RequestBody(required = false) AptCommitmentDeleteRequest request,
      JwtAuthenticationToken principal) {
    commitments.delete(id, request == null ? null : request.reason(),
        JwtPrincipalUtil.getAuditUserId(principal));
    return ResponseEntity.noContent().build();
  }
}
