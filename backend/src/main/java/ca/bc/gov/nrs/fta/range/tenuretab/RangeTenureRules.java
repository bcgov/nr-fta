package ca.bc.gov.nrs.fta.range.tenuretab;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FTA100R's rules, from {@code Fta100RangeTenureForm} (its validators and getFileTypeEnabled),
 * {@code fta100RangeTenure.jsp} (which fields are enabled) and {@code Fta100RangeTenureAction}
 * (the adjustments before a save and the warning after one). The PL/SQL's own rules
 * (FTA_100RANGE_TENURE.SAVE) run in the package; the E/H type lock is checked here as well so
 * it is reported with the form's errors.
 *
 * <p>Values are the package's parameters ({@code p_file_status_st}, …). {@code get} is what the
 * package's GET returned for the file; {@code form} is that with the user's edits applied.
 *
 * <p>Legacy roles map as: Senior Admin and Super User are FTA_ADMIN or FTA_RANGE_HEADQUARTERS_ADMIN
 * ({@link Who#senior}); the district range tech is FTA_RANGE_DISTRICT_ADMIN; what legacy unlocked
 * for a Super User alone (District) is FTA_ADMIN's ({@link Who#admin}).
 */
public final class RangeTenureRules {

  private RangeTenureRules() {}

  /** Who is editing: FTA_ADMIN or FTA_RANGE_HEADQUARTERS_ADMIN; FTA_ADMIN. */
  public record Who(boolean senior, boolean admin) {}

  /** The form's database checks (single-value PL/SQL functions). */
  public interface Checks {
    /** FTA_EDIT_MGMT_UNIT (legacy's SIL_EDIT_MGMT_UNIT bean): the type/id pair exists. */
    boolean mgmtUnitExists(String type, String id);

    /** FTA_EDIT_EXPIRY_TERM: the award date plus the term is the expiry date. */
    boolean expiryMatchesTerm(String award, String years, String months, String expiry);
  }

  // ─── The editable fields: API key → package parameter ─────────────────────

  public static final Map<String, String> FIELDS = fields();

  private static Map<String, String> fields() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("fileStatusSt", "p_file_status_st");
    m.put("fileStatusDate", "p_file_status_date");
    m.put("fileTypeCode", "p_file_type_code");
    m.put("forestDistrictNo", "p_forest_district_no");
    m.put("mgmtUnitType", "p_mgmt_unit_type");
    m.put("mgmtUnitId", "p_mgmt_unit_id");
    m.put("districtAdminZone", "p_district_admin_zone");
    m.put("fileName", "p_file_name");
    m.put("originalIssueYear", "p_original_issue_year");
    m.put("issueYear", "p_issue_year");
    m.put("tenureTermYears", "p_tenure_term_years");
    m.put("expiryYear", "p_expiry_year");
    m.put("replacementTermYears", "p_replacement_term_years");
    return Map.copyOf(m);
  }

  /** The JSP's max lengths for the text fields. */
  static final int FILE_NAME_MAX = 100;
  static final int OVERRIDE_REASON_MAX = 60;
  static final int CONDITION_TITLE_MAX = 110;
  static final int CONDITION_DESCRIPTION_MAX = 500;

  /** The E01/H01 licences: the Historical and Replacement sections (valTypeCode). */
  public static boolean replaceable(String fileType) {
    return "E01".equals(fileType) || "H01".equals(fileType);
  }

  /**
   * Type: always for a Super User (the JSP's isSuperUser branch); otherwise getFileTypeEnabled —
   * a retrieved status of P or PA, for Senior Admin, the range tech or FPB HQ. So the senior
   * roles always, FTA_RANGE_DISTRICT_ADMIN only on a P or PA file.
   */
  static boolean typeEditable(String prevStatus, Who who) {
    return who.senior() || "P".equals(prevStatus) || "PA".equals(prevStatus);
  }

  /** Status is protected when the retrieved status is PE or EE (protectStatusInd). */
  static boolean statusEditable(String status) {
    return !"PE".equals(status) && !"EE".equals(status);
  }

  /**
   * The fields the user may change on this file, as API keys. Everything else on the screen is
   * read only: Admin Org (Super User only, and SAVE ignores it — it sets FOREST_REGION from the
   * district), Client, Sec. Licensee and Replacement Count (always disabled).
   *
   * @param get the package's GET for the file
   */
  public static List<String> editableFields(Map<String, String> get, Who who) {
    List<String> out = new ArrayList<>();
    if (statusEditable(v(get, "p_file_status_st"))) {
      out.add("fileStatusSt");
    }
    out.add("fileStatusDate");
    if (typeEditable(v(get, "p_prev_file_status_st"), who)) {
      out.add("fileTypeCode");
    }
    // District: enabled only for a Super User.
    if (who.admin()) {
      out.add("forestDistrictNo");
    }
    out.addAll(List.of("mgmtUnitType", "mgmtUnitId", "districtAdminZone", "fileName"));
    boolean replaceable = replaceable(v(get, "p_file_type_code"));
    // protect_terms_section_ind is always N: the term fields are always open.
    if (replaceable) {
      out.add("originalIssueYear");
    }
    out.addAll(List.of("issueYear", "tenureTermYears", "expiryYear"));
    if (replaceable && !"Y".equals(v(get, "p_disable_replace_licence_ind"))) {
      out.add("replacementTermYears");
    }
    return out;
  }

  /** Retire: SET_RETIRED_IND's p_disable_retire_ind = N (AR, CA or RRS, not yet retired). */
  public static boolean retirable(Map<String, String> get) {
    return "N".equals(v(get, "p_disable_retire_ind")) && !v(get, "p_forest_file_id").isEmpty();
  }

  /** Cancel Replacement: an E01/H01 with a replacement term, replaceable by the user. */
  public static boolean canCancelReplacement(Map<String, String> get) {
    return replaceable(v(get, "p_file_type_code"))
        && !"Y".equals(v(get, "p_disable_replace_licence_ind"))
        && !v(get, "p_replacement_term_years").isEmpty();
  }

  /**
   * The type dropdown: the range file types of the file's own family (filterRangeDropList:
   * same first letter), the package allowing E01/E02/E03 and H01/H02/H03 only within theirs.
   */
  public static boolean sameFamily(String fileType, String option) {
    return fileType != null && !fileType.isEmpty() && option != null
        && option.startsWith(fileType.substring(0, 1));
  }

  // ─── Before validating ────────────────────────────────────────────────────

  /**
   * handleSave's and forceFormats' adjustments: a status change whose Status Date was left as
   * retrieved is as of today; the zone and management unit are upper-cased.
   */
  public static void adjust(Map<String, String> form, Map<String, String> get, LocalDate today) {
    if (!v(form, "p_file_status_st").equals(v(get, "p_prev_file_status_st"))
        && v(form, "p_file_status_date").equals(v(get, "p_prev_file_status_date"))) {
      form.put("p_file_status_date", today.toString());
    }
    for (String p : List.of("p_district_admin_zone", "p_mgmt_unit_type", "p_mgmt_unit_id")) {
      form.put(p, v(form, p).toUpperCase());
    }
  }

  // ─── The form's validators ────────────────────────────────────────────────

  /**
   * overrideReason's condition: a replacement term newly entered on a tenure whose expiry year
   * (entered, or effective year + term - 1) is already past. The District Override Reason is
   * then shown and required.
   */
  public static boolean overrideRequired(Map<String, String> form, Map<String, String> get,
      int currentYear) {
    String replacement = v(form, "p_replacement_term_years");
    if (!v(get, "p_prev_replacement_term_years").isEmpty() || replacement.isEmpty()
        || !isInt(replacement)) {
      return false;
    }
    String expiry = v(form, "p_expiry_year");
    String term = v(form, "p_tenure_term_years");
    String issue = v(form, "p_issue_year");
    if (term.isEmpty() && expiry.isEmpty()) {
      return false; // tenureDataMandatory fails: left to the other validators
    }
    int expiryYear;
    if (!expiry.isEmpty() && isInt(expiry)) {
      expiryYear = Integer.parseInt(expiry);
    } else if (!term.isEmpty() && isInt(term) && !issue.isEmpty() && isInt(issue)) {
      expiryYear = Integer.parseInt(issue) + Integer.parseInt(term) - 1;
    } else {
      return false;
    }
    return currentYear > expiryYear;
  }

  /**
   * Fta100RangeTenureForm's Save validators, in its order, and the E/H type lock. Returns
   * legacy's messages; empty when the form may go to the package.
   *
   * @param currentCodes the current status, type and district codes, by parameter; a value
   *                     changed to one not listed is an expired code (CodeExpiryValidator)
   */
  public static List<String> validate(Map<String, String> form, Map<String, String> get,
      Map<String, Set<String>> currentCodes, Checks checks, int currentYear) {
    List<String> e = new ArrayList<>();
    String status = v(form, "p_file_status_st");
    boolean ee = "EE".equals(status);

    // CodeExpiryValidator: status, type, district.
    expired(e, form, get, currentCodes, "p_file_status_st", "Status");
    expired(e, form, get, currentCodes, "p_file_type_code", "Type");
    expired(e, form, get, currentCodes, "p_forest_district_no", "District");

    // SAVE's type lock, reported up front.
    typeLock(form, get).ifPresent(e::add);

    // overrideReason
    if (overrideRequired(form, get, currentYear) && !ee
        && v(form, "p_district_override_reason").isBlank()) {
      e.add("District Override Reason is mandatory.");
    }
    if (v(form, "p_district_override_reason").length() > OVERRIDE_REASON_MAX) {
      e.add("District Override Reason cannot exceed a maximum of " + OVERRIDE_REASON_MAX
          + " characters.");
    }

    // statusDate, statusDateMandatory, typeMandatory, mgmtUnitTypeMandatory
    boolean dateValid = validDate(v(form, "p_file_status_date"));
    if (!v(form, "p_file_status_date").isEmpty() && !dateValid) {
      e.add("Status Date must be a valid date in the format yyyy-MM-dd.");
    }
    mandatory(e, form, "p_file_status_date", "Status Date", ee);
    mandatory(e, form, "p_file_type_code", "Type", ee);
    mandatory(e, form, "p_mgmt_unit_type", "Mgmt Unit Type", ee);

    // mgmtValidator, mgmntUnitIdBlank
    String muType = v(form, "p_mgmt_unit_type");
    String muId = v(form, "p_mgmt_unit_id");
    if (!muType.isEmpty() && !checks.mgmtUnitExists(muType, muId)) {
      e.add("Invalid Management Unit type/id combination.");
    }
    if (muType.isEmpty() && !muId.isEmpty()) {
      e.add("If Mgmt Unit ID is blank, Mgmt Unit Type must be blank.");
    }

    // The year fields must be whole numbers before the term rules can read them
    // (IntegerFieldValidator: issueYearInteger, expiresYearInteger, replacementTermInteger).
    integer(e, form, "p_issue_year", "Effective Year");
    integer(e, form, "p_expiry_year", "Expires Year");
    integer(e, form, "p_original_issue_year", "Original Start Year");
    integer(e, form, "p_replacement_term_years", "Replacement Term");

    // The term chain — issueYearRequired, termYearsInteger, issueYearPlusTenureEqualsExpiryYear,
    // tenureDataRequired, tenureTermLimits — stops at its first error.
    termChain(form, get, checks, dateValid, ee).ifPresent(e::add);

    replacementTermLimits(form, get).ifPresent(e::add);

    // districtRequired, districtAdminZoneRequired, adminOrgMandatory
    mandatory(e, form, "p_forest_district_no", "District", ee);
    mandatory(e, form, "p_district_admin_zone", "Zone", ee);
    mandatory(e, form, "p_admin_org_unit_no", "Admin Org", ee);

    // expiryYearGreater
    String issue = v(form, "p_issue_year");
    String expiry = v(form, "p_expiry_year");
    if (showTenureTerm(get) && isInt(issue) && isInt(expiry)
        && Integer.parseInt(issue) > Integer.parseInt(expiry)) {
      e.add("Expiry Year must be greater or equal to Effective Year.");
    }

    if (v(form, "p_file_name").length() > FILE_NAME_MAX) {
      e.add("File Name cannot exceed a maximum of " + FILE_NAME_MAX + " characters.");
    }
    return e;
  }

  /**
   * SAVE's type lock: a range type changes only within its family (E01/E02/E03, H01/H02/H03).
   */
  static Optional<String> typeLock(Map<String, String> form, Map<String, String> get) {
    String prev = v(get, "p_prev_file_type_code");
    String type = v(form, "p_file_type_code");
    if (type.isEmpty() || type.equals(prev)) {
      return Optional.empty();
    }
    Set<String> e = Set.of("E01", "E02", "E03");
    Set<String> h = Set.of("H01", "H02", "H03");
    if ((e.contains(prev) && !e.contains(type)) || (h.contains(prev) && !h.contains(type))) {
      return Optional.of("Type change from " + prev + " to " + type + " is invalid.");
    }
    return Optional.empty();
  }

  /** SET_SHOW_SECTION_IND as GET set it: the term applies unless the file was PA. */
  static boolean showTenureTerm(Map<String, String> get) {
    String ind = v(get, "p_show_tenure_term_ind");
    return ind.isEmpty() ? !"PA".equals(v(get, "p_file_status_st")) : "Y".equals(ind);
  }

  private static Optional<String> termChain(Map<String, String> form, Map<String, String> get,
      Checks checks, boolean dateValid, boolean ee) {
    String status = v(form, "p_file_status_st");
    boolean show = showTenureTerm(get);
    String issue = v(form, "p_issue_year");
    String term = v(form, "p_tenure_term_years");
    String expiry = v(form, "p_expiry_year");

    // issueYearRequired
    if (show && !"PA".equals(status) && !ee && issue.isEmpty()) {
      return Optional.of("Effective Date is mandatory.");
    }
    // termYearsInteger
    if (!term.isEmpty() && !isInt(term)) {
      return Optional.of("Term (yr) must be an integer.");
    }
    // issueYearPlusTenureEqualsExpiryYear: only when one of the three changed from the GET.
    if (show && !expiry.isEmpty() && !term.isEmpty() && isInt(issue) && isInt(expiry)
        && dateValid) {
      boolean changed = !issue.equals(v(get, "p_issue_year"))
          || !term.equals(v(get, "p_tenure_term_years"))
          || !expiry.equals(v(get, "p_expiry_year"));
      if (changed && !checks.expiryMatchesTerm(issue + "-01-01", term, "0",
          expiry + "-12-31")) {
        return Optional.of("If both tenure term and Initial Expiry date have been entered then"
            + " Effective date plus tenure term must equal Initial Expiry date.");
      }
    }
    // tenureDataRequired
    if (show && !"PA".equals(status) && term.isEmpty() && expiry.isEmpty()) {
      return Optional.of(
          "You must enter values for either the Tenure Term (years) or the Expiry Date field.");
    }
    return tenureTermLimits(form, get);
  }

  /**
   * tenureTermLimits: the term by file type, checked only when the expiry or term changed and
   * neither the retrieved nor the new status is PA. Either the term or the expiry year's span
   * (expiry - start + 1) may satisfy the E01/H01 range.
   */
  static Optional<String> tenureTermLimits(Map<String, String> form, Map<String, String> get) {
    if (!showTenureTerm(get)) {
      return Optional.empty();
    }
    String issue = v(form, "p_issue_year");
    String expiry = v(form, "p_expiry_year");
    String term = v(form, "p_tenure_term_years");
    if ((!issue.isEmpty() && !isInt(issue)) || (!expiry.isEmpty() && !isInt(expiry))) {
      return Optional.empty();
    }
    int termYears = !term.isEmpty() && isInt(term) ? Integer.parseInt(term) : 0;
    int startYear = issue.isEmpty() ? 0 : Integer.parseInt(issue);
    int endYear = expiry.isEmpty() ? 0 : Integer.parseInt(expiry);
    int span = endYear - startYear + 1;

    if (expiry.equals(v(get, "p_prev_expiry_year"))
        && term.equals(v(get, "p_prev_tenure_term_years"))) {
      return Optional.empty();
    }
    if ("PA".equals(v(get, "p_prev_file_status_st"))
        || "PA".equals(v(form, "p_file_status_st"))) {
      return Optional.empty();
    }
    String type = v(form, "p_file_type_code");
    switch (type) {
      case "E01", "H01" -> {
        // From 2014; an older licence not being replaced keeps the old 10-year limit.
        if (startYear >= 2014
            && !((termYears >= 15 && termYears <= 25) || (span >= 15 && span <= 25))) {
          return Optional.of(range(type, 15, 25));
        }
        if (startYear < 2014 && v(form, "p_replacement_term_years").isEmpty()
            && termYears > 10) {
          return Optional.of(range(type, 1, 10));
        }
      }
      case "E02", "H02" -> {
        if (startYear >= 2014
            && !((termYears >= 1 || span >= 1) && (termYears <= 10 && span <= 10))) {
          return Optional.of(range(type, 1, 10));
        }
        if (startYear < 2014 && termYears > 5) {
          return Optional.of(range(type, 1, 5));
        }
      }
      case "H03" -> {
        if (termYears != 1 || endYear != startYear) {
          return Optional.of("For H03, the Tenure term must be 1 year(s).");
        }
      }
      case "E03" -> {
        if (termYears > 5 || span > 5) {
          return Optional.of("For E03, Tenure term must be for at least one month and no more"
              + " than 5 years.");
        }
      }
      default -> {
        // no limit
      }
    }
    return Optional.empty();
  }

  private static String range(String type, int min, int max) {
    return "For " + type + ", Tenure term must be at least " + min + " and no more than " + max
        + " years.";
  }

  /**
   * replacementTermLimits: on a replaceable licence the user may replace, a replacement term is
   * 15 to 25 years.
   */
  static Optional<String> replacementTermLimits(Map<String, String> form,
      Map<String, String> get) {
    if (!replaceable(v(get, "p_file_type_code"))
        || "Y".equals(v(get, "p_disable_replace_licence_ind")) || !showTenureTerm(get)) {
      return Optional.empty();
    }
    String r = v(form, "p_replacement_term_years");
    int replaceTerm = !r.isEmpty() && isInt(r) ? Integer.parseInt(r) : 0;
    if (replaceTerm != 0 && (replaceTerm < 15 || replaceTerm > 25)) {
      return Optional.of("For " + v(form, "p_file_type_code")
          + ", Replacement term must be at least 15 and no more than 25 years.");
    }
    return Optional.empty();
  }

  // ─── After a save ─────────────────────────────────────────────────────────

  static final String REPLACEMENT_ENTRY_PERIOD = "Replacement Range licences should be entered"
      + " no more than 24 and no less than 12 months prior to expiry of the current licence.";

  /**
   * handleSave's replacementEntryPeriod warning, when a replacement term was added or changed.
   * Legacy meant "between 24 and 12 months before expiry" but set its calendar with a 0-based
   * month of 12 — January 31 of the expiry year — and so warns after January 31 of the expiry
   * year or before February 1 of the year before it. That is what users saw, so it is kept.
   *
   * @param saved the values as saved (the package's output)
   */
  public static Optional<String> replacementEntryWarning(Map<String, String> saved,
      Map<String, String> get, LocalDate today) {
    String replacement = v(saved, "p_replacement_term_years");
    String prev = v(get, "p_prev_replacement_term_years");
    if (replacement.isEmpty() || replacement.equals(prev)) {
      return Optional.empty();
    }
    String expiry = v(saved, "p_expiry_year");
    int expiryYear;
    if (isInt(expiry)) {
      expiryYear = Integer.parseInt(expiry);
    } else if (isInt(v(saved, "p_issue_year")) && isInt(v(saved, "p_tenure_term_years"))) {
      expiryYear = Integer.parseInt(v(saved, "p_issue_year"))
          + Integer.parseInt(v(saved, "p_tenure_term_years")) - 1;
    } else {
      return Optional.empty();
    }
    LocalDate late = LocalDate.of(expiryYear, 1, 31);
    LocalDate early = LocalDate.of(expiryYear - 1, 2, 1);
    if (today.isAfter(late) || today.isBefore(early)) {
      return Optional.of(REPLACEMENT_ENTRY_PERIOD);
    }
    return Optional.empty();
  }

  // ─── Special conditions ───────────────────────────────────────────────────

  /** The code whose title the user types; every other code's title is the code's own. */
  public static final String OTHER = "OTH";

  /**
   * The SaveSpecCond validators: code required, title and description required (the message
   * names "Other", as legacy's did for every code), description at most 500 characters, and a
   * changed code must be current.
   */
  public static List<String> validateCondition(String code, String title, String description,
      String previousCode, Set<String> currentCodes) {
    List<String> e = new ArrayList<>();
    if (blank(code)) {
      e.add("Condition Code is mandatory.");
    }
    if (blank(title)) {
      e.add("Condition Title is mandatory if Condition Type is Other.");
    }
    if (blank(description)) {
      e.add("Condition Description is mandatory if Condition Type is Other.");
    }
    if (description != null && description.length() > CONDITION_DESCRIPTION_MAX) {
      e.add("Condition Description cannot exceed a maximum of " + CONDITION_DESCRIPTION_MAX
          + " characters.");
    }
    if (title != null && title.length() > CONDITION_TITLE_MAX) {
      e.add("Condition Title cannot exceed a maximum of " + CONDITION_TITLE_MAX
          + " characters.");
    }
    if (!blank(code) && !code.equals(previousCode) && !currentCodes.contains(code)) {
      e.add("Special Condition (" + code + ") is an expired code.");
    }
    return e;
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private static void expired(List<String> e, Map<String, String> form, Map<String, String> get,
      Map<String, Set<String>> current, String param, String label) {
    String code = v(form, param);
    if (code.isEmpty() || code.equals(v(get, param)) || !current.containsKey(param)) {
      return;
    }
    if (!current.get(param).contains(code)) {
      e.add(label + " (" + code + ") is an expired code.");
    }
  }

  /** EERequiredFieldValidator: mandatory unless the status is EE. */
  private static void mandatory(List<String> e, Map<String, String> form, String param,
      String label, boolean ee) {
    if (!ee && v(form, param).isBlank()) {
      e.add(label + " is mandatory.");
    }
  }

  private static void integer(List<String> e, Map<String, String> form, String param,
      String label) {
    String value = v(form, param);
    if (!value.isEmpty() && !isInt(value)) {
      e.add(label + " must be an integer.");
    }
  }

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  static boolean validDate(String s) {
    if (s == null || s.isEmpty()) {
      return true;
    }
    try {
      LocalDate.parse(s, DATE);
      return true;
    } catch (DateTimeParseException ex) {
      return false;
    }
  }

  static boolean isInt(String s) {
    return s != null && s.matches("\\d{1,9}");
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  static String v(Map<String, String> m, String key) {
    String s = m.get(key);
    return s == null ? "" : s.trim();
  }
}
