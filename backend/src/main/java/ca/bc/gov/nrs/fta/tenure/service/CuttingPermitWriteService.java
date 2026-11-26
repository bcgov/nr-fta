package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitDetailDto;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitRules;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitConditionRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitConditionRow;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitPostponementRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitPostponementRow;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitSaveRequest;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitWriteResult;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA902's writes: Save, Delete, Retire, permit issuance conditions and postponements.
 *
 * <p>Each recomputes {@link CuttingPermitRules} and refuses what the screen would not have
 * offered, then runs legacy's PL/SQL ({@link CuttingPermitDetailLegacy}), which judges the rest:
 * status transitions, term limits, expiry against the tenure. Any error it reports is thrown so
 * the transaction rolls back whatever it already wrote.
 *
 * <p>The package's org-unit parameter stays null: {@code SET_AUTHORITY_IND} is the only code that
 * reads it, and only for its {@code p_disable_*} indicators, which these writes do not use.
 */
@Service
public class CuttingPermitWriteService {

  private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,4}");
  /** HARVESTING_AUTHORITY.HARVEST_AREA is NUMBER(11,4): 7 whole digits, 4 decimals. */
  private static final Pattern AREA = Pattern.compile("\\d{1,7}(\\.\\d{1,4})?");
  private static final int MAX_LOCATION = 50;
  /** HARVESTING_AUTHORITY's column widths. */
  private static final int MAX_DISTRICT_ADMN_ZONE = 4;
  private static final int MAX_MGMT_UNIT_TYPE = 1;
  private static final int MAX_MGMT_UNIT_ID = 4;
  private static final int MAX_CONDITION = 3000;

  private final CuttingPermitDetailService read;
  private final CuttingPermitDetailLegacy legacy;

  public CuttingPermitWriteService(
      CuttingPermitDetailService read, CuttingPermitDetailLegacy legacy) {
    this.read = read;
    this.legacy = legacy;
  }

  /** FTA902's Save ({@code MAINLINE} action SAVE). */
  @Transactional
  public PermitWriteResult save(
      long hvaSkey, PermitSaveRequest q, Authentication caller, String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    CuttingPermitRules rules = permit.rules();
    require(rules.canSave());
    String status = text(q.statusCode());
    if (status == null) {
      throw invalid("Status is mandatory.");
    }
    checkFormats(q);
    // Fta902CpDetailForm.initFromRequest: a blank Expires is filled from the issue date and a
    // term above zero. Locked terms are left as the package read them.
    String expiry = text(q.expiryDate());
    if (expiry == null && !rules.termsLocked() && text(q.issueDate()) != null
        && termGiven(q.termYears(), q.termMonths())) {
      expiry = legacy.calcExpiryDate(
          text(q.issueDate()), text(q.termYears()), text(q.termMonths()));
    }
    Map<String, String> call = current(permit, caller, userId, "SAVE");
    String catastrophic = CuttingPermitRules.catastrophicProblem(
        status, date(call.get("p_hdr_award_date")), q.catastrophicInd());
    if (catastrophic != null) {
      throw invalid(catastrophic);
    }

    String problem = CuttingPermitRules.statusChangeProblem(
        permit.statusCode(), status, HarvestAccess.isSenior(caller),
        latestExpiry(hvaSkey), LocalDate.now());
    if (problem != null) {
      throw invalid(problem);
    }
    refuseChange(rules.termsLocked(), call, Map.of(
        "p_mark_issue_date", nullable(q.issueDate()),
        "p_mark_expiry_date", nullable(q.expiryDate()),
        "p_mark_tenure_term_years", nullable(q.termYears()),
        "p_mark_tenure_term_months", nullable(q.termMonths()),
        "p_mark_extend_count", nullable(q.extendCount())), "the permit's terms");
    refuseChange(!rules.canEditExtend(), call, Map.of(
        "p_mark_extend_date", nullable(q.extendDate()),
        "p_mark_extend_rsn_cd", nullable(q.extendReasonCode())), "the extended date");

    call.put("p_mark_status_st", status);
    call.put("p_mark_status_date", text(q.statusDate()));
    call.put("p_mark_issue_date", text(q.issueDate()));
    call.put("p_mark_expiry_date", expiry);
    call.put("p_mark_tenure_term_years", text(q.termYears()));
    call.put("p_mark_tenure_term_months", text(q.termMonths()));
    call.put("p_mark_extend_count", text(q.extendCount()));
    call.put("p_mark_extend_date", text(q.extendDate()));
    call.put("p_mark_extend_rsn_cd", text(q.extendReasonCode()));
    call.put("p_quota_type_code", text(q.quotaTypeCode()));
    call.put("p_salvage_type_code", text(q.salvageTypeCode()));
    call.put("p_deciduous_ind", text(q.deciduousInd()));
    call.put("p_catastrophic_ind", text(q.catastrophicInd()));
    call.put("p_cruise_based_ind", text(q.cruiseBasedInd()));
    call.put("p_lands_region", text(q.crownLandsRegionCode()));
    call.put("p_district_admn_zone", text(q.districtAdmnZone()));
    call.put("p_location", text(q.location()));
    call.put("p_mgmt_unit_type_code", text(q.mgmtUnitTypeCode()));
    call.put("p_mgmt_unit_id", text(q.mgmtUnitId()));
    call.put("p_ext_app_fee_waived", text(q.extAppFeeWaived()));
    call.put("p_ext_app_fee_paid", text(q.extAppFeePaid()));
    call.put("p_frz_ind", text(q.withinFibreRecoveryZone()));
    // Everything below is left as GET read it when the permit does not offer it.
    if (rules.canEditArea()) {
      call.put("p_object_area", text(q.harvestArea()));
    }
    if (rules.canEditPurpose()) {
      call.put("p_purpose_code", text(q.purposeCode()));
    }
    if (rules.canEditMarking()) {
      call.put("p_marking_method_cd", text(q.markingMethodCode()));
      call.put("p_markng_instrmnt_cd", text(q.markingInstrumentCode()));
    }
    call.put("p_hva_revision_count", revision(q.revisionCount()));
    PermitWriteResult saved = outcome(legacy.mainline(call));
    String warning = issuePrecedesAward(
        date(text(q.issueDate())), date(call.get("p_hdr_award_date")));
    if (warning == null) {
      return saved;
    }
    List<String> warnings = new ArrayList<>(saved.warnings());
    warnings.add(warning);
    return new PermitWriteResult(List.copyOf(warnings));
  }

  /** Fta902CpDetailAction.addWarning, after a successful Save. */
  static final String MSG_ISSUE_PRECEDES_AWARD = "Issue Date precedes file issue/award date.";

  /** Legacy's after-save warning when the permit is issued before its file's award, or null. */
  static String issuePrecedesAward(LocalDate issue, LocalDate award) {
    return issue != null && award != null && issue.isBefore(award)
        ? MSG_ISSUE_PRECEDES_AWARD : null;
  }

  /** FTA902's Delete, of an EE permit ({@code DELETE_EE_FILE}, level CUTP). */
  @Transactional
  public PermitWriteResult delete(
      long hvaSkey, Long revisionCount, Authentication caller, String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    require(permit.rules().canDelete());
    Map<String, String> call = current(permit, caller, userId, "DELETE");
    call.put("p_hva_revision_count", revision(revisionCount));
    return outcome(legacy.mainline(call));
  }

  /** FTA902's Retire: stamps the retirement date, leaving the status as it is. */
  @Transactional
  public PermitWriteResult retire(
      long hvaSkey, Long revisionCount, Authentication caller, String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    require(permit.rules().canRetire());
    Map<String, String> call = new HashMap<>();
    call.put("p_action", "RETIRE");
    call.put("p_hva_skey", Long.toString(hvaSkey));
    call.put("p_hva_revision_count", revision(revisionCount));
    call.put("p_userid", userId);
    return outcome(legacy.mainline(call));
  }

  /** Adds a permit issuance condition. */
  @Transactional
  public PermitWriteResult addCondition(
      long hvaSkey, PermitConditionRequest q, Authentication caller, String userId) {
    require(permit(hvaSkey, caller).rules().canManageConditions());
    String theme = text(q.themeCode());
    // FTA_902_CP_CONDITIONS.MAINLINE's own text; the screen offers only current themes.
    if (theme == null) {
      throw invalid("Permit Issuance Condition is required.");
    }
    if (read.conditionThemes().stream().noneMatch(c -> c.code().equals(theme))) {
      throw invalid("Permit Issuance Condition (" + theme + ") is an expired code.");
    }
    String description = text(q.description());
    if (description != null && description.length() > MAX_CONDITION) {
      throw invalid("Condition Description must not exceed " + MAX_CONDITION + " characters.");
    }
    return outcome(legacy.condition(
        "SAVE", hvaSkey, null, theme, description, userId));
  }

  /** Deletes one of the permit's conditions; the package would delete any permit's by id. */
  @Transactional
  public PermitWriteResult deleteCondition(
      long hvaSkey, long conditionId, Authentication caller, String userId) {
    require(permit(hvaSkey, caller).rules().canManageConditions());
    boolean ours = read.conditions(hvaSkey).stream()
        .map(PermitConditionRow::id).anyMatch(id -> id == conditionId);
    if (!ours) {
      throw notFound("Condition not found on this permit.");
    }
    return outcome(legacy.condition("DELETE", hvaSkey, conditionId, null, null, userId));
  }

  /** Adds a postponement ({@code MAINLINE} action POSTPONE), which puts the permit in HP. */
  @Transactional
  public PermitWriteResult addPostponement(
      long hvaSkey, PermitPostponementRequest q, Authentication caller, String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    if (!permit.rules().canPostpone()) {
      String why = CuttingPermitRules.postponeProblem(
          permit.statusCode(), permit.issueDate(), latestRescinded(hvaSkey));
      if (why != null && permit.rules().canSave() && HarvestAccess.isSenior(caller)) {
        throw invalid(why);
      }
      throw forbidden();
    }
    checkPostponement(q);
    Map<String, String> call = current(permit, caller, userId, "POSTPONE");
    call.put("p_cpp_postponement_id", null);
    call.put("p_mark_status_st", "HP");
    putPostponement(call, q);
    call.put("p_hva_revision_count", revision(q.revisionCount()));
    return outcome(legacy.mainline(call));
  }

  /** Updates or rescinds a postponement ({@code MAINLINE} action POSTPONE_UPDATE). */
  @Transactional
  public PermitWriteResult updatePostponement(
      long hvaSkey,
      long postponementId,
      PermitPostponementRequest q,
      Authentication caller,
      String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    require(permit.rules().canChangePostponements());
    PermitPostponementRow row = postponement(hvaSkey, postponementId);
    checkPostponement(q);
    if ("Y".equals(q.rescinded()) && !"Y".equals(row.rescinded())) {
      String problem = CuttingPermitRules.rescindProblem(
          permit.rules().canRescindAnytime(), row.startDate(), row.expiryDate(), LocalDate.now());
      if (problem != null) {
        throw invalid(problem);
      }
    }
    Map<String, String> call = current(permit, caller, userId, "POSTPONE_UPDATE");
    call.put("p_cpp_postponement_id", Long.toString(postponementId));
    putPostponement(call, q);
    call.put("p_hva_revision_count", revision(q.revisionCount()));
    return outcome(legacy.mainline(call));
  }

  /** Deletes a postponement ({@code MAINLINE} action POSTPONE_DELETE). */
  @Transactional
  public PermitWriteResult deletePostponement(
      long hvaSkey, long postponementId, Long revisionCount, Authentication caller, String userId) {
    CuttingPermitDetailDto permit = permit(hvaSkey, caller);
    require(permit.rules().canChangePostponements());
    PermitPostponementRow row = postponement(hvaSkey, postponementId);
    Map<String, String> call = current(permit, caller, userId, "POSTPONE_DELETE");
    call.put("p_cpp_postponement_id", Long.toString(postponementId));
    call.put("p_cpp_postponement_start_date", iso(row.startDate()));
    call.put("p_cpp_postponement_expiry_date", iso(row.expiryDate()));
    call.put("p_cpp_postponement_term_yrs", number(row.termYears()));
    call.put("p_cpp_postponement_term_mnths", number(row.termMonths()));
    call.put("p_cpp_rescinded", row.rescinded());
    call.put("p_hva_revision_count", revision(revisionCount));
    return outcome(legacy.mainline(call));
  }

  private CuttingPermitDetailDto permit(long hvaSkey, Authentication caller) {
    return read.findByHvaSkey(hvaSkey, caller)
        .orElseThrow(() -> notFound("Cutting permit not found."));
  }

  private PermitPostponementRow postponement(long hvaSkey, long id) {
    return read.postponements(hvaSkey).stream()
        .filter(r -> r.id() == id)
        .findFirst()
        .orElseThrow(() -> notFound("Postponement not found on this permit."));
  }

  private LocalDate latestExpiry(long hvaSkey) {
    return read.postponements(hvaSkey).stream().findFirst()
        .map(PermitPostponementRow::expiryDate).orElse(null);
  }

  private boolean latestRescinded(long hvaSkey) {
    return read.postponements(hvaSkey).stream().findFirst()
        .map(r -> "Y".equals(r.rescinded())).orElse(false);
  }

  /**
   * MAINLINE's GET, ready to send back with {@code action}: the {@code p_prev_*} values and
   * header dates SAVE compares against are the ones the package itself read, as on legacy's page.
   */
  private Map<String, String> current(
      CuttingPermitDetailDto permit, Authentication caller, String userId, String action) {
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "GET");
    in.put("p_hva_skey", Long.toString(permit.hvaSkey()));
    in.put("p_hdr_forest_file_id", permit.forestFileId());
    in.put("p_hdr_cutting_permit_id", permit.cuttingPermitId());
    in.put("p_cp_id", permit.cuttingPermitId());
    String senior = HarvestAccess.isSenior(caller) ? "Y" : "N";
    in.put("p_fta_senior_admin_staff_ind", senior);
    in.put("p_fta_oil_gas_commission_ind", "N");
    Map<String, String> out = legacy.mainline(in);
    CuttingPermitMessages.Parsed got = CuttingPermitMessages.parse(out.get("p_error_message"));
    if (got.failed()) {
      throw invalid(String.join(" ", got.errors()));
    }
    Map<String, String> call = new HashMap<>(out);
    call.put("p_action", action);
    call.put("p_error_message", null);
    call.put("p_userid", userId);
    call.put("p_trace_ind", "N");
    call.put("p_fta_senior_admin_staff_ind", senior);
    call.put("p_fta_oil_gas_commission_ind", "N");
    // Fta902CpDetailAction: an FSJ permit's id is its harvesting authority id; others have none.
    call.put("p_hva_id", "Y".equals(out.get("p_fsj_ind")) ? out.get("p_cp_id") : null);
    return call;
  }

  private static void putPostponement(Map<String, String> call, PermitPostponementRequest q) {
    call.put("p_cpp_postponement_start_date", text(q.startDate()));
    call.put("p_cpp_postponement_expiry_date", text(q.expiryDate()));
    call.put("p_cpp_postponement_term_yrs", text(q.termYears()));
    call.put("p_cpp_postponement_term_mnths", text(q.termMonths()));
    call.put("p_cpp_rescinded", "Y".equals(q.rescinded()) ? "Y" : "N");
  }

  /** Refuses a change to read-only fields; {@code locked} says whether they are. */
  private static void refuseChange(
      boolean locked, Map<String, String> current, Map<String, String> sent, String what) {
    if (!locked) {
      return;
    }
    sent.forEach((key, value) -> {
      if (!Objects.equals(text(current.get(key)), text(value))) {
        throw forbidden("You may not change " + what + " on this permit.");
      }
    });
  }

  private static PermitWriteResult outcome(Map<String, String> out) {
    return outcome(out.get("p_error_message"));
  }

  /** Throwing rolls back whatever the procedure wrote before it failed. */
  private static PermitWriteResult outcome(String raw) {
    CuttingPermitMessages.Parsed parsed = CuttingPermitMessages.parse(raw);
    if (parsed.failed()) {
      throw new ResponseStatusException(
          parsed.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
          String.join(" ", parsed.errors()));
    }
    return new PermitWriteResult(List.copyOf(parsed.warnings()));
  }

  /** What would otherwise surface as an ORA- error from the package's conversions. */
  static void checkFormats(PermitSaveRequest q) {
    checkDate(q.statusDate(), "Status As of");
    checkDate(q.issueDate(), "Issue Date");
    checkDate(q.expiryDate(), "Expires");
    checkDate(q.extendDate(), "Extended Date");
    checkNumber(q.termYears(), "Mark Term years");
    checkNumber(q.termMonths(), "Mark Term months");
    checkNumber(q.extendCount(), "Extension Count");
    String area = text(q.harvestArea());
    if (area != null && !AREA.matcher(area).matches()) {
      throw invalid(
          "Area must be hectares, at most 9,999,999.9999, with up to 4 decimal places.");
    }
    checkLength(q.location(), MAX_LOCATION, "Geographic Location");
    checkLength(q.districtAdmnZone(), MAX_DISTRICT_ADMN_ZONE, "Zone");
    checkLength(q.mgmtUnitTypeCode(), MAX_MGMT_UNIT_TYPE, "Management Unit Type");
    checkLength(q.mgmtUnitId(), MAX_MGMT_UNIT_ID, "Management Unit");
  }

  /** Legacy's test: a term that is not blank or zero in both its parts. */
  static boolean termGiven(String years, String months) {
    return !(isZeroOrBlank(years) && isZeroOrBlank(months));
  }

  private static boolean isZeroOrBlank(String value) {
    String v = text(value);
    return v == null || v.equals("0");
  }

  private static void checkLength(String value, int max, String label) {
    String v = text(value);
    if (v != null && v.length() > max) {
      throw invalid(label + " must not exceed " + max + " characters.");
    }
  }

  private static void checkPostponement(PermitPostponementRequest q) {
    checkDate(q.startDate(), "Start");
    checkDate(q.expiryDate(), "Expiry");
    checkNumber(q.termYears(), "Term years");
    checkNumber(q.termMonths(), "Term months");
    if (text(q.startDate()) == null) {
      throw invalid("Postponement start date is mandatory.");
    }
    if (text(q.expiryDate()) == null
        && text(q.termYears()) == null && text(q.termMonths()) == null) {
      throw invalid("Postponement expiry date or term is mandatory.");
    }
  }

  private static void checkDate(String value, String label) {
    String v = text(value);
    if (v == null) {
      return;
    }
    try {
      LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      throw invalid(label + " must be a date in the form yyyy-MM-dd.");
    }
  }

  private static void checkNumber(String value, String label) {
    String v = text(value);
    if (v != null && !WHOLE_NUMBER.matcher(v).matches()) {
      throw invalid(label + " must be a whole number.");
    }
  }

  /** Refused rather than defaulted: a stale page must not overwrite a newer revision. */
  private static String revision(Long revisionCount) {
    if (revisionCount == null) {
      throw invalid("The permit's revision is missing. Reload the page and try again.");
    }
    return revisionCount.toString();
  }

  private static void require(boolean allowed) {
    if (!allowed) {
      throw forbidden();
    }
  }

  /** A package date (yyyy-mm-dd), or null when absent or unreadable. */
  private static LocalDate date(String value) {
    if (value == null || value.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(value.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static ResponseStatusException forbidden() {
    return forbidden("This action is not available on this permit.");
  }

  private static ResponseStatusException forbidden(String message) {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
  }

  private static ResponseStatusException invalid(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException notFound(String message) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
  }

  private static String text(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  /** {@link Map#of} takes no nulls; a blank reads the same to {@link #refuseChange}. */
  private static String nullable(String s) {
    return s == null ? "" : s;
  }

  private static String iso(LocalDate d) {
    return d == null ? null : d.toString();
  }

  private static String number(Integer n) {
    return n == null ? null : n.toString();
  }
}
