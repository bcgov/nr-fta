package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Details tab: legacy FTA100's GET and its Save of an existing file, through
 * {@code THE.FTA_100_TENURE.mainline} (see {@link TenureDetailsPackage}).
 *
 * <p>The save follows {@code Fta100TenureAction.handleSave}: it re-reads the file, refuses a
 * stale form (revision counts), applies only the fields the rules open, makes the action's and
 * form's adjustments, runs the form's validators, then hands the whole screen — every value as
 * the GET returned it, with the edits — to the package's SAVE, rolling back when the package
 * reports an error.
 */
@Service
public class TenureDetailsService {

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  /** The revision counts (and HVA key) the save is locked on. */
  static final List<String> REVISION_PARAMS = List.of(
      "p_pfu_revision_count", "p_tt_revision_count", "p_ltc_revision_count",
      "p_sup_revision_count", "p_pw_revision_count", "p_tla_revision_count",
      "p_td_revision_count", "p_lcf_revision_count", "p_hs_revision_count",
      "p_hva_revision_count", "p_haa_revision_count", "p_rec_proj_revision_count",
      "p_hva_skey");

  /** Package parameters that are not screen values. */
  private static final Set<String> INTERNAL = Set.of(
      "p_action", "p_called_by", "p_userid", "p_trace_ind", "p_error_message",
      "p_is_super_user", "p_user_org_unit_code", "p_user_org_unit_no",
      "p_district_admin_authority_ind", "p_tenure_app_id", "p_client_number",
      "p_client_locn_code");

  /** The GET warning after which legacy's action disables Save. */
  private static final String NO_SPATIAL = "This file does not contain an approved spatial"
      + " submission";

  /**
   * GET's notice on a private mark's file. Not shown: edit mode already opens only those two
   * fields, so the banner told users nothing.
   */
  private static final String PRIVATE_MARK_NOTICE = "Only Zone and quota type may be updated";

  /** Coded values: parameter, option list (for descriptions and the expiry check), label. */
  private record Coded(String param, String list, String label) {}

  private static final List<Coded> CODED = List.of(
      new Coded("p_file_type_code", "fileTypes", "Type"),
      new Coded("p_admin_org_unit_no", "orgUnits", "Admin Organization"),
      new Coded("p_forest_district_no", "districts", "District"),
      new Coded("p_extension_reason", "extendReasons", "Reason"),
      new Coded("p_security_deposit_code", "depositTypes", "Security Deposit Type"),
      new Coded("p_other_deposit_code", "depositTypes", "Other Deposit Type"),
      new Coded("p_quota_type_code", "quotaTypes", "Quota"),
      new Coded("p_marking_method_cd", "markingMethods", "Compliance Method"),
      new Coded("p_markng_instrmnt_cd", "markingInstruments", "Instrument"),
      new Coded("p_lands_region", "landRegions", "Land Region"),
      new Coded("p_bcts_org_unit", "bctsOrgUnits", "BCTS Org"),
      new Coded("p_payment_method_cd", "paymentMethods", "Payment Method"),
      new Coded("p_fup_type_code", "fupTypes", "FUP Type Code"),
      new Coded("p_fup_fn_usage_code", "fupFnUsages", "FUP Usage Code"),
      new Coded("p_map_notn_type_cd", "mapNotationTypes", "Map Notation Code"),
      new Coded("p_fn_held_level_code", "fnHeldLevels", "FN Held Level Code"),
      new Coded("p_mark_extension_reason", "extendReasons", "Mark Ext. Reason"));

  private final TenureDetailsPackage pkg;
  private final TenureDetailsLookups lookups;

  public TenureDetailsService(TenureDetailsPackage pkg, TenureDetailsLookups lookups) {
    this.pkg = pkg;
    this.lookups = lookups;
  }

  /** The tab, or 404 when FTA100 has nothing for the file. */
  public TenureDetailsDto get(String forestFileId) {
    Read read = read(forestFileId);
    TenureDetailsForm form = read.form;
    String reason = read.reason;
    boolean editable = reason == null;
    List<String> fields = editable ? form.editableFields(null) : List.of();
    List<String> conditional = new ArrayList<>();
    if (editable && "B10".equals(form.type())) {
      conditional.addAll(List.of("p_fup_fn_usage_code", "p_fup_cedar_species_ind",
          "p_fup_treaty_purpose_ind"));
    }
    if (editable && (form.showDepositPurpose() || form.showMarkPurpose())
        && !form.protectPurpose()) {
      conditional.add("p_pulpwood_file");
    }
    TenureDetailsRules rules = new TenureDetailsRules(
        editable,
        reason,
        keys(fields),
        keys(conditional),
        editable && fields.contains("p_file_status_st")
            ? lookups.overrideStatuses(form.prevStatus()) : List.of(),
        form.yes("p_recreation_ind") ? "recreationStatuses" : "statuses",
        switch (form.purposeList()) {
          case "FOR" -> "forLicenceToCut";
          case "SPEC" -> "specialUse";
          default -> "occLicenceToCut";
        });

    Map<String, String> values = new LinkedHashMap<>();
    form.values().forEach((p, v) -> {
      if (!INTERNAL.contains(p) && !REVISION_PARAMS.contains(p)
          && !p.startsWith("p_show_") && !p.startsWith("p_disable_")) {
        values.put(TenureDetailsForm.key(p), v);
      }
    });
    Map<String, String> revisions = new LinkedHashMap<>();
    REVISION_PARAMS.forEach(p -> revisions.put(TenureDetailsForm.key(p), form.get(p)));

    return new TenureDetailsDto(forestFileId, values, descriptions(form, rules), form.layout(),
        rules, revisions, read.warnings);
  }

  /** "CODE - description" for each coded value, expired codes included. */
  private Map<String, String> descriptions(TenureDetailsForm form, TenureDetailsRules rules) {
    Map<String, List<CodeOptionDto>> all = lookups.allCodes();
    Map<String, String> out = new LinkedHashMap<>();
    List<Coded> coded = new ArrayList<>(CODED);
    coded.add(new Coded("p_file_status_st", rules.statusList(), "Status"));
    coded.add(new Coded("p_purpose_code", rules.purposeList(), "Purpose"));
    coded.add(new Coded("p_b05_purpose", rules.purposeList(), "Purpose"));
    for (Coded c : coded) {
      String code = form.get(c.param());
      if (code.isEmpty()) {
        continue;
      }
      String list = "p_forest_district_no".equals(c.param()) ? "orgUnits" : c.list();
      all.getOrDefault(list, List.of()).stream()
          .filter(o -> code.equals(o.code()))
          .findFirst()
          .ifPresent(o -> out.put(TenureDetailsForm.key(c.param()), o.description()));
    }
    return out;
  }

  /**
   * Saves the edits — legacy's Save of an existing file. Returns the package's warnings.
   *
   * @throws ResponseStatusException 409 when the file cannot be saved or was changed since it
   *                                 was read; 400 with legacy's messages when a check fails
   */
  @Transactional
  public TenureDetailsSaveResult update(
      String forestFileId, TenureDetailsUpdateRequest request, String userId) {
    Read read = read(forestFileId);
    TenureDetailsForm form = read.form;
    if (read.reason != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, read.reason);
    }
    Map<String, String> sentRevisions =
        request.revisions() == null ? Map.of() : request.revisions();
    for (String p : REVISION_PARAMS) {
      String sent = sentRevisions.getOrDefault(TenureDetailsForm.key(p), "");
      if (!form.get(p).equals(sent == null ? "" : sent)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "This tenure was changed by someone else since you opened it. Reload it and make"
                + " your changes again.");
      }
    }

    // The proposed values: the GET's, with the opened fields the request carries.
    Map<String, String> sent = request.values() == null ? Map.of() : request.values();
    Map<String, String> proposed = new HashMap<>();
    sent.forEach((k, v) -> proposed.put(param(k), v == null ? "" : v.trim()));
    Map<String, String> after = new HashMap<>(form.values());
    for (String p : form.editableFields(proposed)) {
      if (proposed.containsKey(p)) {
        after.put(p, proposed.get(p));
      }
    }
    after.put("p_district_override_reason",
        proposed.getOrDefault("p_district_override_reason", ""));

    List<String> warnings = new ArrayList<>(adjust(form, after));

    List<String> errors = new ArrayList<>(expiredCodes(form, after));
    errors.addAll(TenureDetailsValidator.validate(form, after, lookups));
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }

    String[] hq = lookups.headquarters();
    Map<String, String> in = new HashMap<>(after);
    in.put("p_action", "SAVE");
    in.put("p_userid", userId);
    in.put("p_user_org_unit_no", hq[0]);
    in.put("p_user_org_unit_code", hq[1]);
    in.put("p_is_super_user", "N");
    in.put("p_district_admin_authority_ind", "Y");
    in.put("p_client_number", "");
    in.put("p_client_locn_code", "");
    in.put("p_trace_ind", "N");
    in.put("p_error_message", "");
    TenureDetailsMessages.Parsed result =
        TenureDetailsMessages.parse(pkg.call(in).get("p_error_message"));
    if (result.failed()) {
      // Throwing rolls back whatever the package wrote before it stopped.
      throw new ResponseStatusException(
          result.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
          String.join(" ", result.errors()));
    }
    warnings.addAll(result.warnings());
    return new TenureDetailsSaveResult(warnings);
  }

  /**
   * The adjustments legacy makes before validating and saving ({@code handleSave} and
   * {@code Fta100TenureForm.validate}'s preamble). Returns any warning they raise.
   */
  private List<String> adjust(TenureDetailsForm form, Map<String, String> after) {
    List<String> warnings = new ArrayList<>();
    String status = after.getOrDefault("p_file_status_st", "");
    // A status change without a new As of date is as of today.
    if (!status.equals(form.prevStatus())
        && after.getOrDefault("p_file_status_date", "")
            .equals(form.get("p_prev_file_status_date"))) {
      after.put("p_file_status_date", LocalDate.now(PACIFIC).toString());
    }
    // A protected term is recomputed by the package from changed dates.
    if (form.protectTenureTerm()
        && (!after.getOrDefault("p_award_date", "").equals(form.get("p_prev_award_date"))
            || !after.getOrDefault("p_expiry_date", "").equals(form.get("p_prev_expiry_date")))) {
      after.put("p_tenure_term_months", "");
      after.put("p_tenure_term_years", "");
    }
    // Initial Area alone: obligated is all of it.
    if (form.yes("p_show_a06_a30_areas_ind") && !"A06".equals(form.type())
        && isDecimal(after.get("p_init_licence_area"))
        && blank(after, "p_obligation_area") && blank(after, "p_eliminated_area")
        && blank(after, "p_other_area")) {
      after.put("p_obligation_area", after.get("p_init_licence_area"));
      after.put("p_eliminated_area", "0");
      after.put("p_other_area", "0");
    }
    // A term without an expiry date fills the expiry date in.
    String years = after.getOrDefault("p_tenure_term_years", "");
    String months = after.getOrDefault("p_tenure_term_months", "");
    boolean noTerm = (years.isEmpty() || "0".equals(years))
        && (months.isEmpty() || "0".equals(months));
    if (!Set.of("B05", "B06").contains(form.type()) && blank(after, "p_expiry_date") && !noTerm
        && !blank(after, "p_award_date")) {
      String expiry = lookups.calcExpiryDate(after.get("p_award_date"), years, months);
      if (expiry != null) {
        after.put("p_expiry_date", expiry);
      }
    }
    // HN → HI without an Effective Date takes ECAS's appraisal date (ecasValidator).
    if ("HN".equals(form.prevStatus()) && "HI".equals(status) && blank(after, "p_award_date")) {
      String ecas = lookups.ecasAppraisalDate(form.get("p_forest_file_id"));
      if (ecas != null) {
        after.put("p_award_date", ecas);
      }
    }
    String maxHarvest = after.getOrDefault("p_maximum_harvest_volume", "");
    String maxRevenue = after.getOrDefault("p_maximum_revenue_volume", "");
    if (isInteger(maxHarvest) && isInteger(maxRevenue)
        && Long.parseLong(maxRevenue) >= Long.parseLong(maxHarvest)) {
      warnings.add("Total FRA (Bill 28,2003) must be less than Maximum Harvest Volume.");
    }
    if (blank(after, "p_waste_assess_reqd_ind")) {
      after.put("p_waste_assess_reqd_ind", "U");
    }
    if (blank(after, "p_is_in_frz")) {
      after.put("p_is_in_frz", "U");
    }
    return warnings;
  }

  /** CodeExpiryValidator: a code changed to one that is no longer current. */
  private List<String> expiredCodes(TenureDetailsForm form, Map<String, String> after) {
    Map<String, List<CodeOptionDto>> current = lookups.options();
    List<Coded> coded = new ArrayList<>(CODED);
    coded.add(new Coded("p_file_status_st",
        form.yes("p_recreation_ind") ? "recreationStatuses" : "statuses", "Status"));
    String purposeList = switch (form.purposeList()) {
      case "FOR" -> "forLicenceToCut";
      case "SPEC" -> "specialUse";
      default -> "occLicenceToCut";
    };
    coded.add(new Coded("p_purpose_code", purposeList, "Purpose"));
    coded.add(new Coded("p_b05_purpose", purposeList, "Purpose"));
    List<String> errors = new ArrayList<>();
    for (Coded c : coded) {
      String code = after.getOrDefault(c.param(), "");
      if (code.isEmpty() || code.equals(form.get(c.param()))) {
        continue;
      }
      Set<String> codes = current.getOrDefault(c.list(), List.of()).stream()
          .map(CodeOptionDto::code).collect(Collectors.toSet());
      if (!codes.contains(code)) {
        errors.add(c.label() + " (" + code + ") is an expired code.");
      }
    }
    return errors;
  }

  private static boolean blank(Map<String, String> m, String p) {
    String v = m.get(p);
    return v == null || v.isBlank();
  }

  private static boolean isDecimal(String v) {
    try {
      return v != null && !v.isBlank() && new BigDecimal(v.trim()).signum() >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean isInteger(String v) {
    return v != null && v.matches("\\d{1,18}");
  }

  /** API key → package parameter, for every parameter. */
  private static final Map<String, String> PARAMS_BY_KEY =
      TenureDetailsPackage.MAINLINE_PARAMS.stream()
          .collect(Collectors.toMap(TenureDetailsForm::key, p -> p));

  /** {@code fileStatusSt} → {@code p_file_status_st}; unknown keys map to nothing usable. */
  static String param(String key) {
    return PARAMS_BY_KEY.getOrDefault(key, "?" + key);
  }

  private static List<String> keys(List<String> params) {
    return params.stream().map(TenureDetailsForm::key).toList();
  }

  private record Read(TenureDetailsForm form, List<String> warnings, String reason) {}

  /** FTA_100_TENURE.GET as a Headquarters Senior Admin. */
  private Read read(String forestFileId) {
    String[] hq = lookups.headquarters();
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "GET");
    in.put("p_forest_file_id", forestFileId);
    in.put("p_user_org_unit_no", hq[0]);
    in.put("p_user_org_unit_code", hq[1]);
    in.put("p_district_admin_authority_ind", "Y");
    in.put("p_is_super_user", "N");
    in.put("p_trace_ind", "N");
    Map<String, String> out = pkg.call(in);
    TenureDetailsMessages.Parsed messages =
        TenureDetailsMessages.parse(out.get("p_error_message"));
    if (out.getOrDefault("p_pfu_revision_count", "").isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "FTA100 has no tenure details for file " + forestFileId + ".");
    }
    if (messages.failed()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.join(" ", messages.errors()));
    }
    List<String> warnings = messages.warnings().stream()
        .filter(w -> !w.startsWith(PRIVATE_MARK_NOTICE))
        .toList();
    if (warnings.stream().anyMatch(w -> w.contains(NO_SPATIAL))) {
      out.put("p_disable_save_ind", "Y");
    }
    TenureDetailsForm form = new TenureDetailsForm(out,
        lookups.validLicenceToCutType(out.getOrDefault("p_file_type_code", "")));
    return new Read(form, warnings, form.notEditableReason(warnings));
  }
}
