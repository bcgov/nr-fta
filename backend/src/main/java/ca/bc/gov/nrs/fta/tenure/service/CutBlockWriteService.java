package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockEditRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockRelabelRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockSavedDto;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import java.math.BigDecimal;
import java.util.ArrayList;
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

  /** FTA904's {@code CodeExpiryValidator}s: a code changed on Save must be current. */
  private static final String STATUS_CURRENT_SQL =
      "SELECT COUNT(*) FROM the.block_status_code WHERE block_status_code = :code"
          + " AND SYSDATE BETWEEN effective_date AND expiry_date";
  private static final String CUT_REGULATION_CURRENT_SQL =
      "SELECT COUNT(*) FROM the.cut_regulation_code WHERE cut_regulation_code = :code"
          + " AND SYSDATE BETWEEN effective_date AND expiry_date";
  private static final String REFOREST_DECLARE_CURRENT_SQL =
      "SELECT COUNT(*) FROM the.reforest_declare_type_code"
          + " WHERE reforest_declare_type_code = :code"
          + " AND SYSDATE BETWEEN effective_date AND expiry_date";
  private static final String FIRE_REASON_CURRENT_SQL =
      "SELECT COUNT(*) FROM the.fire_harvesting_reason_code"
          + " WHERE fire_harvesting_reason_code = :code"
          + " AND SYSDATE BETWEEN effective_date AND expiry_date";

  /**
   * The JSP's {@code maxlength} on District Override Reason: the reason is written to
   * {@code FDC_AUDIT_LOG.CHANGE_COMMENTS}.
   */
  static final int MAX_OVERRIDE_REASON = 60;

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
          "PE".equals(block.blockStatus()) || "PE".equals(block.fileStatusCode())
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
    List<String> problems = new ArrayList<>(CutBlockRules.editProblems(
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
            block.fileTypeCode(), block.fileStatusCode(), block.markStatusCode(),
            block.blockStatus(), true, rules.plannedLocked(), rules.disturbanceLocked(), false,
            false)));
    expired(problems, "Status", status, block.blockStatus(), STATUS_CURRENT_SQL);
    expired(problems, "Cut Regulation Code", trim(q.cutRegulationCode()),
        block.cutRegulationCode(), CUT_REGULATION_CURRENT_SQL);
    expired(problems, "Reforestation Declaration Code", trim(q.reforestDeclareTypeCode()),
        block.reforestDeclareTypeCode(), REFOREST_DECLARE_CURRENT_SQL);
    expired(problems, "Fire Harvesting Reason Code", trim(q.fireHarvestingReasonCode()),
        block.fireHarvestingReasonCode(), FIRE_REASON_CURRENT_SQL);

    // The reason is taken only when FTA904 asks for it; otherwise it is dropped, so the package
    // writes no FDC_AUDIT_LOG row for it.
    boolean overrideRequired =
        rules.seniorAuthority() && overrideRequired(status, block.blockStatus());
    String overrideReason = overrideRequired ? trim(q.overrideReason()) : null;
    if (overrideRequired && overrideReason == null) {
      problems.add(CutBlockRules.MSG_OVERRIDE_REASON);
    } else if (overrideReason != null && overrideReason.length() > MAX_OVERRIDE_REASON) {
      problems.add("District Override Reason must not exceed " + MAX_OVERRIDE_REASON
          + " characters.");
    }
    if (!problems.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
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
   * {@code fta904_edit_status_change}: whether moving a block from {@code oldStatus} to
   * {@code newStatus} is a rollback, suspension or cancellation a senior user must give a
   * District Override Reason for. Never for an unchanged status.
   */
  public boolean overrideRequired(String newStatus, String oldStatus) {
    String next = trim(newStatus);
    String previous = trim(oldStatus);
    if (next == null || previous == null || next.equals(previous)) {
      return false;
    }
    return "Y".equals(jdbc.queryForObject(
        OVERRIDE_NEEDED_SQL, Map.of("newStatus", next, "oldStatus", previous), String.class));
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

  /** Legacy's {@code CodeExpiryValidator}: a changed code must be current. */
  private void expired(
      List<String> problems, String label, String value, String stored, String currentSql) {
    if (value == null || value.equals(stored)) {
      return;
    }
    Integer current = jdbc.queryForObject(currentSql, Map.of("code", value), Integer.class);
    if (current == null || current == 0) {
      problems.add(label + " (" + value + ") is an expired code.");
    }
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
