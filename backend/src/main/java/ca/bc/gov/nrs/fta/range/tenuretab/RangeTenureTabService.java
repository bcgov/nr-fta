package ca.bc.gov.nrs.fta.range.tenuretab;

import ca.bc.gov.nrs.fta.billing.dto.InvoicePreviewDto;
import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewService;
import ca.bc.gov.nrs.fta.range.service.ManageZoneService;
import ca.bc.gov.nrs.fta.range.service.RangeFileAccess;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureLookups.StoredCondition;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Action;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Condition;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Create;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Created;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Result;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Save;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRules.Who;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureTabDto.ConditionCode;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureTabDto.Zone;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.service.CodeListService;
import ca.bc.gov.nrs.fta.tenure.tab.details.TenureDetailsLookups;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The range Tenure tab — legacy FTA100R ({@code Fta100RangeTenureAction}) — through
 * {@code THE.FTA_100RANGE_TENURE.mainline} ({@link RangeTenurePackage}).
 *
 * <p>Each write follows its legacy handler: re-read the file with the package's GET (as a
 * Headquarters user, so the package's own authority indicators never refuse — who may edit is
 * {@link RangeFileAccess#requireEditable}, the role's district), refuse a stale form (revision
 * counts), apply only the fields the user may change, run the form's adjustments and validators
 * ({@link RangeTenureRules}), then hand the whole form to the package, rolling back when it
 * reports an error.
 */
@Service
public class RangeTenureTabService {

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  static final String STALE = "This tenure was changed by someone else since you opened it."
      + " Reload it and make your changes again.";

  /** The revision counts the save is locked on: API key → parameter. */
  static final Map<String, String> REVISIONS = Map.of(
      "pfuRevisionCount", "p_pfu_revision_count",
      "ttRevisionCount", "p_tt_revision_count",
      "rtRevisionCount", "p_rt_revision_count");

  private final RangeTenurePackage pkg;
  private final RangeTenureLookups lookups;
  private final TenureDetailsLookups tenureLookups;
  private final RangeFileAccess access;
  private final LoggedUserHelper auth;
  private final ManageZoneService zones;
  private final CodeListService codeLists;
  private final InvoicePreviewService invoicePreview;
  private final Clock clock;

  @Autowired
  public RangeTenureTabService(RangeTenurePackage pkg, RangeTenureLookups lookups,
      TenureDetailsLookups tenureLookups, RangeFileAccess access, LoggedUserHelper auth,
      ManageZoneService zones, CodeListService codeLists, InvoicePreviewService invoicePreview) {
    this(pkg, lookups, tenureLookups, access, auth, zones, codeLists, invoicePreview,
        Clock.system(PACIFIC));
  }

  RangeTenureTabService(RangeTenurePackage pkg, RangeTenureLookups lookups,
      TenureDetailsLookups tenureLookups, RangeFileAccess access, LoggedUserHelper auth,
      ManageZoneService zones, CodeListService codeLists, InvoicePreviewService invoicePreview,
      Clock clock) {
    this.pkg = pkg;
    this.lookups = lookups;
    this.tenureLookups = tenureLookups;
    this.access = access;
    this.auth = auth;
    this.zones = zones;
    this.codeLists = codeLists;
    this.invoicePreview = invoicePreview;
    this.clock = clock;
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }

  /**
   * Senior Admin and Super User are FTA_ADMIN or FTA_RANGE_HEADQUARTERS_ADMIN; what only a Super User
   * could change (District) is FTA_ADMIN's.
   */
  private Who who() {
    boolean admin = auth.isAdmin();
    return new Who(admin || auth.isRangeHeadquartersAdmin(), admin);
  }

  // ─── GET ──────────────────────────────────────────────────────────────────

  /** The tab's form, or 404 when the file is not a range tenure. */
  public RangeTenureTabDto get(String forestFileId) {
    Map<String, String> get = read(forestFileId);
    boolean editable = access.mayEdit(forestFileId);
    Who who = who();
    String type = v(get, "p_file_type_code");

    Map<String, String> values = new LinkedHashMap<>();
    RangeTenureRules.FIELDS.forEach((key, param) -> values.put(key, v(get, param)));
    values.put("adminOrgUnitNo", v(get, "p_admin_org_unit_no"));
    values.put("mgmtUnitName", v(get, "p_mgmt_unit_name"));
    values.put("replacementCount", v(get, "p_replacement_count"));
    values.put("replacementLabel", v(get, "p_replacement_label"));
    values.put("prevFileStatusSt", v(get, "p_prev_file_status_st"));
    values.put("prevFileStatusDate", v(get, "p_prev_file_status_date"));
    values.put("prevReplacementTermYears", v(get, "p_prev_replacement_term_years"));

    Map<String, String> revisions = new LinkedHashMap<>();
    REVISIONS.forEach((key, param) -> revisions.put(key, v(get, param)));

    List<Zone> zoneList = zonesFor(v(get, "p_forest_district_no"));
    Map<String, String> descriptions = new LinkedHashMap<>();
    describe(descriptions, "fileStatusSt", v(get, "p_file_status_st"), lookups.allStatuses());
    describe(descriptions, "fileTypeCode", type, lookups.allFileTypes());
    String zone = v(get, "p_district_admin_zone");
    zoneList.stream().filter(z -> z.code().equals(zone)).findFirst().ifPresent(z -> {
      descriptions.put("zoneDescription", z.description() == null ? "" : z.description());
      descriptions.put("zoneContact", z.contact() == null ? "" : z.contact());
    });

    List<CodeOptionDto> types = lookups.fileTypes().stream()
        .filter(o -> RangeTenureRules.sameFamily(type, o.code()))
        .toList();

    return new RangeTenureTabDto(
        forestFileId,
        values,
        revisions,
        descriptions,
        editable,
        editable ? RangeTenureRules.editableFields(get, who) : List.of(),
        RangeTenureRules.replaceable(type),
        RangeTenureRules.showTenureTerm(get),
        editable && RangeTenureRules.retirable(get),
        editable && RangeTenureRules.canCancelReplacement(get),
        lookups.statuses(),
        types,
        zoneList,
        lookups.conditionCodes());
  }

  /** The Zone dropdown for a district (FTA631's GET): code, description and contact. */
  public List<Zone> zonesFor(String districtNo) {
    if (districtNo == null || !districtNo.trim().matches("\\d{1,10}")) {
      return List.of();
    }
    return zones.search(districtNo.trim(), null).stream()
        .map(z -> new Zone(z.rangeZoneCode(), z.zoneDescription(), z.contact()))
        .toList();
  }

  /** FTA010's range types, for Add New Tenure. */
  public List<CodeOptionDto> createTypes() {
    return lookups.createTypes();
  }

  /** FTA100R's Preview Invoice: FTA695 for the file, this year (695's default). */
  public InvoicePreviewDto invoicePreview(String forestFileId) {
    return invoicePreview.preview(forestFileId, null);
  }

  // ─── Save ─────────────────────────────────────────────────────────────────

  /**
   * Save ({@code handleSave}).
   *
   * @throws ResponseStatusException 403 when the user may not edit the file (or move it to that
   *     district), 409 when it changed since it was read, 400 with legacy's messages
   */
  @Transactional
  public Result save(String forestFileId, Save request, String userId) {
    access.requireEditable(forestFileId);
    Map<String, String> get = read(forestFileId);
    checkRevisions(get, request.revisions());
    Who who = who();

    Map<String, String> sent = request.values() == null ? Map.of() : request.values();
    Map<String, String> form = new HashMap<>(get);
    for (String key : RangeTenureRules.editableFields(get, who)) {
      if (sent.containsKey(key)) {
        String value = sent.get(key);
        form.put(RangeTenureRules.FIELDS.get(key), value == null ? "" : value.trim());
      }
    }
    String reason = sent.get("districtOverrideReason");
    form.put("p_district_override_reason", reason == null ? "" : reason.trim());

    LocalDate today = today();
    RangeTenureRules.adjust(form, get, today);
    // The reason is shown, and read, only when the override applies (override_ind).
    if (!RangeTenureRules.overrideRequired(form, get, today.getYear())) {
      form.put("p_district_override_reason", "");
    }

    String district = v(form, "p_forest_district_no");
    if (!district.equals(v(get, "p_forest_district_no"))
        && !auth.mayEditRangeDistrict(lookups.orgUnitCode(district))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You may move a range tenure only to a district you administer.");
    }

    List<String> errors = RangeTenureRules.validate(form, get, currentCodes(), checks(),
        today.getYear());
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }

    Map<String, String> in = new HashMap<>(form);
    in.put("p_action", "SAVE");
    in.put("p_district_admin_authority_ind", who.senior() ? "Y" : "N");
    Map<String, String> out = call(in, userId);
    RangeTenureMessages.Parsed result = RangeTenureMessages.parse(out.get("p_error_message"));
    failOn(result);

    // handleSave reads the bean as the package returned it.
    Map<String, String> saved = new HashMap<>(form);
    saved.putAll(out);
    List<String> warnings = new ArrayList<>(result.warnings());
    RangeTenureRules.replacementEntryWarning(saved, get, today).ifPresent(warnings::add);
    return new Result("Save successful.", warnings);
  }

  /** Retire ({@code handleRetire}): RETIRE, when SET_RETIRED_IND allows it. */
  @Transactional
  public Result retire(String forestFileId, Action request, String userId) {
    access.requireEditable(forestFileId);
    Map<String, String> get = read(forestFileId);
    if (!RangeTenureRules.retirable(get)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This file cannot be retired.");
    }
    checkRevisions(get, request == null ? null : request.revisions());
    Map<String, String> in = new HashMap<>(get);
    in.put("p_action", "RETIRE");
    // handleRetire reads errors only (parseToErrors).
    RangeTenureMessages.Parsed result =
        RangeTenureMessages.parse(call(in, userId).get("p_error_message"));
    failOn(result);
    return new Result("Retirement successful.", List.of());
  }

  /** Cancel Replacement ({@code handleCancelReplacement}): CANCEL_REPLACEMENT. */
  @Transactional
  public Result cancelReplacement(String forestFileId, Action request, String userId) {
    access.requireEditable(forestFileId);
    Map<String, String> get = read(forestFileId);
    if (!RangeTenureRules.canCancelReplacement(get)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This tenure has no replacement to cancel.");
    }
    checkRevisions(get, request == null ? null : request.revisions());
    Map<String, String> in = new HashMap<>(get);
    in.put("p_action", "CANCEL_REPLACEMENT");
    RangeTenureMessages.Parsed result =
        RangeTenureMessages.parse(call(in, userId).get("p_error_message"));
    failOn(result);
    return new Result("Cancel Replacement successful.", result.warnings());
  }

  // ─── Special conditions ───────────────────────────────────────────────────

  /**
   * Save Condition ({@code handleSaveSpecialCond}): SAVE_COND, a new condition when
   * {@code skey} is null. The code of an existing condition stays (legacy disabled it); the
   * title is typed only for OTH, any other code keeping its code's title (a new condition) or
   * its own (a change).
   */
  @Transactional
  public Result saveCondition(String forestFileId, Long skey, Condition request,
      String userId) {
    access.requireEditable(forestFileId);
    StoredCondition existing = null;
    if (skey != null) {
      existing = lookups.condition(forestFileId, skey);
      if (existing == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "The selected special condition does not exist.");
      }
    }
    String code = existing != null ? existing.code() : trim(request.code()).toUpperCase();
    List<ConditionCode> codes = lookups.conditionCodes();
    String title;
    if (RangeTenureRules.OTHER.equals(code)) {
      title = trim(request.title());
    } else if (existing != null) {
      title = existing.title();
    } else {
      title = codes.stream().filter(c -> c.code().equals(code)).map(ConditionCode::title)
          .findFirst().orElse("");
    }
    String description = request.description() == null ? "" : request.description();

    Set<String> current = codes.stream().map(ConditionCode::code).collect(Collectors.toSet());
    List<String> errors = RangeTenureRules.validateCondition(code, title, description,
        existing == null ? null : existing.code(), current);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }

    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE_COND");
    in.put("p_forest_file_id", forestFileId);
    in.put("p_rtspc_skey", skey == null ? "" : String.valueOf(skey));
    in.put("p_range_special_condition_code", code);
    in.put("p_condition_title", title);
    in.put("p_condition_description", description);
    in.put("p_rsc_revision_count", skey == null || request.revisionCount() == null ? ""
        : String.valueOf(request.revisionCount()));
    RangeTenureMessages.Parsed result =
        RangeTenureMessages.parse(call(in, userId).get("p_error_message"));
    failOn(result);
    return new Result("Save successful.", result.warnings());
  }

  /** Delete ({@code handleDeleteSpecialCond}): DELETE_COND, locked on the revision count. */
  @Transactional
  public Result deleteCondition(String forestFileId, long skey, Integer revisionCount,
      String userId) {
    access.requireEditable(forestFileId);
    if (lookups.condition(forestFileId, skey) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          "The selected special condition does not exist.");
    }
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "DELETE_COND");
    in.put("p_forest_file_id", forestFileId);
    in.put("p_rtspc_skey", String.valueOf(skey));
    in.put("p_rsc_revision_count", revisionCount == null ? "" : String.valueOf(revisionCount));
    RangeTenureMessages.Parsed result =
        RangeTenureMessages.parse(call(in, userId).get("p_error_message"));
    failOn(result);
    return new Result("Delete successful.", result.warnings());
  }

  // ─── Add New ──────────────────────────────────────────────────────────────

  /**
   * FTA100R's Add New: the range type FTA010 picked, saved by SAVE with no PROV_FOREST_USE
   * revision count ({@code ADD_NEW_REC}), which generates the file id and creates
   * PROV_FOREST_USE and RANGE_TENURE. A new range tenure is always PA (PT #18161; ADD_NEW_REC
   * refuses any other status); its term is entered once it leaves PA.
   *
   * @throws ResponseStatusException 403 for a district the user may not edit; 400 with legacy's
   *     messages
   */
  @Transactional
  public Created create(Create request, String userId) {
    String type = trim(request.fileTypeCode()).toUpperCase();
    String district = trim(request.forestDistrictNo());
    String date = trim(request.fileStatusDate());
    if (date.isEmpty()) {
      date = today().toString();
    }
    String muType = trim(request.mgmtUnitType()).toUpperCase();
    String muId = trim(request.mgmtUnitId()).toUpperCase();
    String zone = trim(request.districtAdminZone()).toUpperCase();
    String fileName = trim(request.fileName());

    List<String> e = new ArrayList<>();
    if (type.isEmpty()) {
      e.add("File Type is mandatory.");
    } else if (!RangeTenureLookups.CREATE_TYPES.contains(type)) {
      e.add("You are not authorized to add " + type + " files.");
    } else if (lookups.createTypes().stream().noneMatch(o -> o.code().equals(type))) {
      e.add("File Type (" + type + ") is an expired code.");
    }
    if (district.isEmpty()) {
      e.add("District is mandatory.");
    } else if (codeLists.districts().stream().noneMatch(o -> o.code().equals(district))) {
      e.add("District (" + district + ") is an expired code.");
    }
    if (!RangeTenureRules.validDate(date)) {
      e.add("Status Date must be a valid date in the format yyyy-MM-dd.");
    }
    if (muType.isEmpty()) {
      e.add("Mgmt Unit Type is mandatory.");
    } else if (!tenureLookups.mgmtUnitExists(muType, muId)) {
      e.add("Invalid Management Unit type/id combination.");
    }
    if (muType.isEmpty() && !muId.isEmpty()) {
      e.add("If Mgmt Unit ID is blank, Mgmt Unit Type must be blank.");
    }
    if (zone.isEmpty()) {
      e.add("Zone is mandatory.");
    } else if (!district.isEmpty()
        && zonesFor(district).stream().noneMatch(z -> z.code().equals(zone))) {
      e.add("Invalid Zone.");
    }
    if (fileName.length() > RangeTenureRules.FILE_NAME_MAX) {
      e.add("File Name cannot exceed a maximum of " + RangeTenureRules.FILE_NAME_MAX
          + " characters.");
    }
    if (!district.isEmpty() && !auth.mayEditRangeDistrict(lookups.orgUnitCode(district))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "You may add range tenures only in a district you administer.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    Map<String, String> in = new HashMap<>();
    in.put("p_action", "SAVE");
    in.put("p_file_type_code", type);
    in.put("p_forest_district_no", district);
    in.put("p_district_admin_zone", zone);
    in.put("p_file_name", fileName);
    in.put("p_file_status_st", "PA");
    in.put("p_file_status_date", date);
    in.put("p_mgmt_unit_type", muType);
    in.put("p_mgmt_unit_id", muId);
    Map<String, String> out = call(in, userId);
    RangeTenureMessages.Parsed result = RangeTenureMessages.parse(out.get("p_error_message"));
    failOn(result);
    String id = v(out, "p_forest_file_id");
    if (id.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "Error occurred generating file id.");
    }
    return new Created(id, result.warnings());
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  /** FTA_100RANGE_TENURE.GET as Headquarters, with handleGet's "0" term shown blank. */
  Map<String, String> read(String forestFileId) {
    String[] hq = tenureLookups.headquarters();
    Map<String, String> in = new HashMap<>();
    in.put("p_action", "GET");
    in.put("p_forest_file_id", forestFileId);
    in.put("p_user_org_unit_no", hq[0]);
    in.put("p_user_org_unit_code", hq[1]);
    in.put("p_district_admin_authority_ind", "Y");
    in.put("p_trace_ind", "N");
    Map<String, String> out = new HashMap<>(pkg.call(in));
    if (v(out, "p_pfu_revision_count").isEmpty() || !"Y".equals(v(out, "p_range_ind"))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
          forestFileId + " is not a range tenure.");
    }
    RangeTenureMessages.Parsed messages = RangeTenureMessages.parse(out.get("p_error_message"));
    if (messages.failed()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          String.join(" ", messages.errors()));
    }
    if ("0".equals(v(out, "p_tenure_term_years"))) {
      out.put("p_tenure_term_years", "");
    }
    out.put("p_error_message", "");
    return out;
  }

  /** Runs mainline with the audit user and Headquarters as the user's org. */
  private Map<String, String> call(Map<String, String> in, String userId) {
    String[] hq = tenureLookups.headquarters();
    Map<String, String> params = new HashMap<>(in);
    params.put("p_userid", userId);
    params.put("p_user_org_unit_no", hq[0]);
    params.put("p_user_org_unit_code", hq[1]);
    params.putIfAbsent("p_district_admin_authority_ind", "Y");
    params.put("p_trace_ind", "N");
    params.put("p_error_message", "");
    return pkg.call(params);
  }

  /** Throwing rolls back whatever the package wrote before it stopped. */
  private static void failOn(RangeTenureMessages.Parsed result) {
    if (result.failed()) {
      throw new ResponseStatusException(
          result.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
          String.join(" ", result.errors()));
    }
  }

  private static void checkRevisions(Map<String, String> get, Map<String, String> revisions) {
    Map<String, String> sent = revisions == null ? Map.of() : revisions;
    REVISIONS.forEach((key, param) -> {
      String s = sent.get(key);
      if (!v(get, param).equals(s == null ? "" : s.trim())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, STALE);
      }
    });
  }

  private Map<String, Set<String>> currentCodes() {
    return Map.of(
        "p_file_status_st", codesOf(lookups.statuses()),
        "p_file_type_code", codesOf(lookups.fileTypes()),
        "p_forest_district_no", codesOf(codeLists.districts()));
  }

  private static Set<String> codesOf(List<CodeOptionDto> options) {
    return options.stream().map(CodeOptionDto::code).collect(Collectors.toSet());
  }

  private RangeTenureRules.Checks checks() {
    return new RangeTenureRules.Checks() {
      @Override
      public boolean mgmtUnitExists(String type, String id) {
        return tenureLookups.mgmtUnitExists(type, id);
      }

      @Override
      public boolean expiryMatchesTerm(String award, String years, String months,
          String expiry) {
        return tenureLookups.expiryMatchesTerm(award, years, months, expiry);
      }
    };
  }

  private static void describe(Map<String, String> out, String key, String code,
      List<CodeOptionDto> options) {
    if (code.isEmpty()) {
      return;
    }
    options.stream().filter(o -> code.equals(o.code())).findFirst()
        .ifPresent(o -> out.put(key, o.description()));
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String v(Map<String, String> m, String key) {
    String s = m.get(key);
    return s == null ? "" : s.trim();
  }
}
