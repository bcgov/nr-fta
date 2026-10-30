package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockEditRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockRelabelRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockSavedDto;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cut block writes: FTA904's Save and FTA231's Re-label. {@link CutBlockRules} decides what is
 * allowed, as it does for the screen; the legacy packages ({@link CutBlockLegacy}) do the work.
 * A package's reported failure throws, which rolls back what it wrote before failing.
 */
@Service
public class CutBlockWriteService {

  private static final String OVERRIDE_NEEDED_SQL =
      "SELECT the.fta904_edit_status_change(:newStatus, :oldStatus) FROM dual";

  /** The width of {@code CUT_BLOCK_KEY_EVENT.EVENT_COMMENT}, as the block delete allows. */
  private static final int MAX_REASON = 200;

  private static final int MAX_LABEL = 10;

  private final CutblockDetailService detail;
  private final CutBlockLegacy legacy;
  private final NamedParameterJdbcTemplate jdbc;

  public CutBlockWriteService(
      CutblockDetailService detail, CutBlockLegacy legacy, NamedParameterJdbcTemplate jdbc) {
    this.detail = detail;
    this.legacy = legacy;
    this.jdbc = jdbc;
  }

  /**
   * Saves the block.
   *
   * @throws ResponseStatusException 404 if it does not exist, 409 if its rules refuse the save or
   *     it has changed since it was read, 400 if the edit is invalid
   */
  @Transactional
  public CutBlockSavedDto save(
      long cbSkey, CutBlockEditRequest q, Authentication authentication, String userId) {
    CutblockDetailDto block = load(cbSkey, authentication);
    CutBlockRules rules = block.rules();
    if (!rules.canEdit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "PE".equals(block.blockStatus())
              ? CutBlockRules.MSG_STATUS_PE
              : CutBlockRules.MSG_NOT_EDITABLE);
    }
    String status = trim(q.blockStatus());
    boolean statusChanged = status != null && !status.equals(block.blockStatus());
    if (statusChanged && rules.statusLocked()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, CutBlockRules.MSG_STATUS_LOCKED);
    }

    // A locked field is checked, and saved, as it stands on the block.
    BigDecimal gross = rules.plannedLocked()
        ? decimal(block.plannedGrossBlockArea()) : q.plannedGrossBlockArea();
    List<String> problems = CutBlockRules.editProblems(
        new CutBlockRules.Edit(
            status,
            block.salvageTypeCode(),
            trim(q.cutRegulationCode()),
            gross,
            q.plannedNetBlockArea(),
            rules.disturbanceLocked()
                ? decimal(block.disturbanceGrossArea()) : q.disturbanceGrossArea(),
            rules.disturbanceLocked() ? block.disturbanceStartDate() : q.disturbanceStartDate(),
            rules.disturbanceLocked() ? block.disturbanceEndDate() : q.disturbanceEndDate(),
            rules.spExemptLocked() ? block.spExemptInd() : trim(q.spExemptInd()),
            q.cutBlockDescription()),
        new CutBlockRules.Facts(
            block.fileTypeCode(), null, block.markStatusCode(), block.blockStatus(),
            true, rules.plannedLocked(), rules.disturbanceLocked(), false, false));
    if (!problems.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
    }

    String overrideReason = trim(q.overrideReason());
    if (statusChanged && rules.seniorAuthority() && overrideReason == null
        && "Y".equals(jdbc.queryForObject(
            OVERRIDE_NEEDED_SQL,
            Map.of("newStatus", status, "oldStatus", block.blockStatus()),
            String.class))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, CutBlockRules.MSG_OVERRIDE_REASON);
    }

    CutBlockLegacy.Outcome outcome = legacy.save(new CutBlockLegacy.SaveParams(
        cbSkey,
        block.forestFileId(),
        block.cuttingPermitId(),
        block.cutBlockId(),
        rules.seniorAuthority(),
        q.cbRevisionCount(),
        q.cboaRevisionCount(),
        userId,
        status,
        q.blockStatusDate(),
        trim(q.cutBlockDescription()),
        trim(q.cutRegulationCode()),
        trim(q.reforestDeclareTypeCode()),
        trim(q.fireHarvestingReasonCode()),
        trim(q.underPartitionOrder()),
        q.reportedFireDate(),
        trim(q.wasteAssessmentRequired()),
        q.plannedNetBlockArea(),
        overrideReason,
        rules.plannedLocked(),
        q.plannedGrossBlockArea(),
        q.plannedHarvestDate(),
        rules.disturbanceLocked(),
        q.disturbanceGrossArea(),
        q.disturbanceStartDate(),
        q.disturbanceEndDate(),
        trim(q.spExemptInd())));
    failOn(outcome);
    return new CutBlockSavedDto(block.cutBlockId(), outcome.warnings());
  }

  /**
   * Re-labels the block.
   *
   * @throws ResponseStatusException 404 if it does not exist, 409 if its rules refuse it or it
   *     has changed since it was read, 400 if the new label or reason is invalid
   */
  @Transactional
  public CutBlockSavedDto relabel(
      long cbSkey, CutBlockRelabelRequest q, Authentication authentication, String userId) {
    if (!HarvestAccess.isSenior(authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    CutblockDetailDto block = load(cbSkey, authentication);
    if (!block.rules().canRelabel()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, CutBlockRules.MSG_CANNOT_RELABEL);
    }
    String label = trim(q.newCutBlockId());
    label = label == null ? null : label.toUpperCase(Locale.ROOT);
    String reason = trim(q.reason());
    if (label == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New Label is mandatory.");
    }
    if (label.length() > MAX_LABEL) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "New Label must not exceed " + MAX_LABEL + " characters.");
    }
    if (label.equals(block.cutBlockId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "New Label must differ from the current label.");
    }
    if (reason == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relabel Reason is mandatory.");
    }
    if (reason.length() > MAX_REASON) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Relabel Reason must not exceed " + MAX_REASON + " characters.");
    }
    CutBlockLegacy.Outcome outcome = legacy.relabel(
        block.forestFileId(),
        block.cuttingPermitId(),
        block.timberMark(),
        block.cutBlockId(),
        label,
        reason,
        userId,
        cbSkey,
        q.revisionCount());
    failOn(outcome);
    return new CutBlockSavedDto(label, outcome.warnings());
  }

  private CutblockDetailDto load(long cbSkey, Authentication authentication) {
    return detail.find(cbSkey, authentication).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Cut block not found."));
  }

  private static void failOn(CutBlockLegacy.Outcome outcome) {
    if (!outcome.errors().isEmpty()) {
      throw new ResponseStatusException(
          outcome.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
          String.join(" ", outcome.errors()));
    }
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
