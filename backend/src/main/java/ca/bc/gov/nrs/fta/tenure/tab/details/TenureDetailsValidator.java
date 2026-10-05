package ca.bc.gov.nrs.fta.tenure.tab.details;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checks {@code Fta100TenureForm} runs on Save before calling the package — its "Save"
 * validator chain, for an existing file, with the message texts of ApplicationResources. The
 * ones that only apply to a new file (file-id formats, Admin Org level, duplicate file) are
 * left out, as is {@code districtWithinAdminOrg} (Region users only). A Headquarters Senior
 * Admin is not a super user, so the term limits apply.
 *
 * <p>The PL/SQL SAVE runs its own checks afterwards (status changes, BCTS, cash sales, PA
 * moves…); those come back as its messages.
 */
final class TenureDetailsValidator {

  /** The database lookups some checks need (the legacy validators' function beans). */
  interface Checks {
    /** FTA_EDIT_MGMT_UNIT. */
    boolean mgmtUnitExists(String type, String id);

    /** FTA_EDIT_EXPIRY_TERM: Effective Date plus the term (less a day) is the expiry date. */
    boolean expiryMatchesTerm(String award, String years, String months, String expiry);

    /** FTA_EDIT_QUOTA_TYPE. */
    boolean quotaTypeValid(String fileType, String quotaType);

    /** FTA_EDIT_TIMBER_MARK. */
    boolean timberMarkExists(String mark);

    /** FTA_EDIT_FILE_ID. */
    boolean fileExists(String forestFileId);

    /** FTA_EDIT_PULPWOOD_FILE: the two files share their licensee. */
    boolean sameLicensee(String forestFileId, String pulpwoodFile);

    /** FTA100_EDIT_STATUS_CHANGE: the status change needs a district override reason. */
    boolean statusChangeNeedsOverride(String newStatus, String oldStatus);
  }

  private static final DateTimeFormatter ISO =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  private static final int OVERRIDE_REASON_MAX = 60;

  private final TenureDetailsForm before;
  private final Map<String, String> after;
  private final Checks checks;
  private final List<String> errors = new ArrayList<>();

  private TenureDetailsValidator(
      TenureDetailsForm before, Map<String, String> after, Checks checks) {
    this.before = before;
    this.after = after;
    this.checks = checks;
  }

  /**
   * The problems with saving {@code after} (the GET values with the user's changes applied)
   * over {@code before}, in legacy's order; empty when the save may go ahead.
   */
  static List<String> validate(
      TenureDetailsForm before, Map<String, String> after, Checks checks) {
    TenureDetailsValidator v = new TenureDetailsValidator(before, after, checks);
    v.run();
    return v.errors;
  }

  /**
   * Whether this change needs a District Override Reason — legacy's {@code overrideReason}
   * validator for a Senior Admin: a status change FTA100_EDIT_STATUS_CHANGE flags, or, on a
   * held file (H… other than HN), a change to Effective Date, the term, Ext. Count or Initial
   * Expiry Date.
   */
  static boolean overrideNeeded(
      TenureDetailsForm before, Map<String, String> after, Checks checks) {
    String prev = before.prevStatus();
    String status = s(after, "p_file_status_st");
    boolean statusFlag = !prev.isEmpty() && !status.isEmpty()
        && checks.statusChangeNeedsOverride(status, prev);
    boolean termChanged = prev.startsWith("H") && !"HN".equals(prev)
        && (changed(before, after, "p_award_date")
            || changed(before, after, "p_tenure_term_years")
            || changed(before, after, "p_tenure_term_months")
            || changed(before, after, "p_extension_count")
            || changed(before, after, "p_expiry_date"));
    return statusFlag || termChanged;
  }

  private static boolean changed(TenureDetailsForm before, Map<String, String> after, String p) {
    return !s(after, p).equals(before.get(p).trim());
  }

  private static String s(Map<String, String> m, String p) {
    String v = m.get(p);
    return v == null ? "" : v.trim();
  }

  private String s(String p) {
    return s(after, p);
  }

  private String type() {
    return s("p_file_type_code");
  }

  private boolean typeIn(String... types) {
    return Set.of(types).contains(type());
  }

  /** EERequiredFieldValidator: required unless the file is going to EE or HX. */
  private boolean eeExempt() {
    String status = s("p_file_status_st");
    return "EE".equals(status) || "HX".equals(status);
  }

  private void required(String p, String label) {
    if (!eeExempt() && s(p).isEmpty()) {
      errors.add(label + " is mandatory.");
    }
  }

  private LocalDate date(String p, String label) {
    String value = s(p);
    if (value.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(value, ISO);
    } catch (DateTimeParseException e) {
      errors.add(label + " must be in the format YYYY-MM-DD.");
      return null;
    }
  }

  private static LocalDate parse(String value) {
    try {
      return value.isEmpty() ? null : LocalDate.parse(value, ISO);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** IntegerFieldValidator + IntegerLimitsValidator. Null when blank or invalid. */
  private Long integer(String p, String label, long min, long max) {
    String value = s(p);
    if (value.isEmpty()) {
      return null;
    }
    long n;
    try {
      n = Long.parseLong(value);
    } catch (NumberFormatException e) {
      errors.add(label + " must be an integer.");
      return null;
    }
    if (n < min || n > max) {
      errors.add(label + " must be between " + min + " and " + max + ".");
      return null;
    }
    return n;
  }

  /** FloatFieldValidator + FloatRangeValidator + DecimalPlacesValidator. */
  private BigDecimal decimal(String p, String label, String max, int places, boolean oneDp) {
    String value = s(p);
    if (value.isEmpty()) {
      return null;
    }
    BigDecimal n;
    try {
      n = new BigDecimal(value);
    } catch (NumberFormatException e) {
      errors.add(label + " must be numeric.");
      return null;
    }
    if (n.signum() < 0 || n.compareTo(new BigDecimal(max)) > 0) {
      errors.add(label + " must be between 0 and " + max + ".");
      return null;
    }
    if (n.stripTrailingZeros().scale() > places) {
      errors.add(oneDp
          ? "Only one decimal-place is permitted for " + label
          : label + " cannot contain more than " + places + " places of decimal.");
      return null;
    }
    return n;
  }

  private static boolean greaterThanYearsApart(LocalDate start, LocalDate end, int years) {
    // Legacy datesGreaterThanXYearsApart(…, lessADay = true): end later than start + years - 1 day.
    return start != null && end != null && end.isAfter(start.plusYears(years).minusDays(1));
  }

  private void run() {
    String status = s("p_file_status_st");
    String prev = before.prevStatus();
    boolean showTerm = before.yes("p_show_tenure_term_ind");
    boolean termsOpen = !before.protectTermsSection();

    // Code lists: FN agreement, payment method
    if (before.fnAgreementMandatory() && s("p_fn_agreement").isEmpty()) {
      errors.add("Direct Award FN Agreement is required.");
    }
    if (before.yes("p_show_payment_ind")
        && !"A".equals(s("p_payment_method_cd")) && !"C".equals(s("p_payment_method_cd"))) {
      errors.add("Payment Method must be A or C.");
    }

    required("p_file_status_st", "Status");

    // District Override Reason
    if (overrideNeeded(before, after, checks)) {
      required("p_district_override_reason", "District Override Reason");
    }

    LocalDate asOf = date("p_file_status_date", "As of Date");
    required("p_file_status_date", "As of Date");
    required("p_file_type_code", "Type");

    // Management unit
    required("p_mgmt_unit_type", "Mgmt Unit Type");
    if (!s("p_mgmt_unit_type").isEmpty()
        && !checks.mgmtUnitExists(s("p_mgmt_unit_type"), s("p_mgmt_unit_id"))) {
      errors.add("Invalid Management Unit type/id combination.");
    }
    if (s("p_mgmt_unit_type").isEmpty() && !s("p_mgmt_unit_id").isEmpty()) {
      errors.add("If Mgmt Unit Type is blank, Mgmt Unit ID must be blank.");
    }
    if ("A02".equals(type()) && "T".equals(s("p_mgmt_unit_type"))) {
      String fileId = s("p_forest_file_id");
      String last2 = fileId.length() > 1 ? fileId.substring(fileId.length() - 2) : fileId;
      if (fileId.length() > 1 && !Character.isDigit(last2.charAt(0))) {
        last2 = "0" + last2.charAt(1);
      }
      if (!last2.equals(s("p_mgmt_unit_id"))) {
        errors.add("If file type is A02 and management type is 'T' then management unit must"
            + " be the same number as the last 2 digits in the file ID.");
      }
    }

    // Term
    LocalDate award = date("p_award_date", "Effective Date");
    Long years = integer("p_tenure_term_years", "Term (yr)", Long.MIN_VALUE, Long.MAX_VALUE);
    integer("p_maximum_harvest_volume", "Maximum Harvest Volume", 0, 999_999_999);
    Long months = integer("p_tenure_term_months", "Term (mo)", 0, 12);
    LocalDate expiry = parse(s("p_expiry_date"));
    boolean awardOk = s("p_award_date").isEmpty() || award != null;
    boolean expiryOk = s("p_expiry_date").isEmpty() || expiry != null;
    if (showTerm && termsOpen && !before.hideTenureTerm() && awardOk && expiryOk
        && !"PA".equals(prev) && !"PA".equals(status)) {
      long total = (years == null ? 0 : years) * 12 + (months == null ? 0 : months);
      if (before.yes("p_bcts_file_ind")) {
        if (total > 120 || greaterThanYearsApart(award, expiry, 10)) {
          errors.add("For BCTS Files, the maximum Tenure term is 10 years.");
        }
      } else if (typeIn("A02", "A03", "A28", "A29", "A30")) {
        if (total > 1188 || greaterThanYearsApart(award, expiry, 99)) {
          errors.add("For " + type() + ", the maximum Tenure term is 99 years.");
        }
      } else if (typeIn("A01", "A04", "A41", "A44")) {
        if (total > 240 || greaterThanYearsApart(award, expiry, 20)) {
          errors.add("For " + type() + ", the maximum Tenure term is 20 years.");
        }
      } else if ("P01".equals(type())) {
        if (total > 300 || greaterThanYearsApart(award, expiry, 25)) {
          errors.add("For P01, the maximum Tenure term is 25 years.");
        }
      } else if (typeIn("A18", "A31", "B07", "B05", "B06", "B04")) {
        if (total > 60 || greaterThanYearsApart(award, expiry, 5)) {
          errors.add("For " + type() + ", the maximum Tenure term is 5 years.");
        }
      }
    }
    integer("p_maximum_revenue_volume", "Maximum Revenue Share Volume", 0, 999_999_999);
    if (typeIn("B05", "B06") && greaterThanYearsApart(award, expiry, 5)) {
      errors.add("For " + type() + ", the maximum Tenure term is 5 years.");
    }
    String countLabel = s("p_replaced_date").isEmpty() ? "Ext. Count" : "Rep. Count";
    integer("p_extension_count", countLabel, 0, 99);
    expiry = date("p_expiry_date", "Expires Date");
    if (!s("p_expiry_date").isEmpty() && s("p_award_date").isEmpty()) {
      errors.add("Effective Date must be entered when Expires Date is.");
    }
    LocalDate extended = date("p_extended_date", "Current Expiry Date");
    LocalDate replaced = date("p_replaced_date", "Replaced Date");
    if (showTerm && !before.protectTenureTerm() && !before.hideTenureTerm()
        && expiry != null && award != null
        && (s("p_file_status_date").isEmpty() || asOf != null)
        && (!s("p_tenure_term_months").isEmpty() || !s("p_tenure_term_years").isEmpty())
        && !checks.expiryMatchesTerm(s("p_award_date"), s("p_tenure_term_years"),
            s("p_tenure_term_months"), s("p_expiry_date"))) {
      errors.add("Tenure term and Initial Expiry date do not correspond.");
    }
    if (!"A11".equals(type()) && !before.protectExtendedDate()
        && extended != null && expiry != null && !extended.isAfter(expiry)) {
      errors.add("Current Expiry Date must be after Initial Expiry Date.");
    }
    if (s("p_extended_date").isEmpty() && !s("p_extension_reason").isEmpty()
        && (prev.startsWith("H") || "A".equals(status))) {
      errors.add("If Current Expiry Date is blank, Reason must be blank.");
    }

    // FUP, map notation
    if ("B10".equals(type()) && "PA".equals(status) && s("p_fup_type_code").isEmpty()) {
      errors.add("FUP Type is mandatory when file type code is B10 and status is PA.");
    }
    if ("B10".equals(type()) && "FN".equals(s("p_fup_type_code"))
        && s("p_fup_fn_usage_code").isEmpty()) {
      errors.add("First Nations Purpose is mandatory when FUP type is FN.");
    }
    if ("M01".equals(type()) && s("p_map_notn_type_cd").isEmpty()) {
      errors.add("Map Notation Type is mandatory if File Type is M01 - Map Notation.");
    }
    if (!before.hideDistrict()) {
      required("p_forest_district_no", "District");
    }

    // Mark
    boolean showMark = before.yes("p_show_mark_ind");
    if (showMark && !"B02".equals(type()) && !s("p_quota_type_code").isEmpty()
        && !checks.quotaTypeValid(type(), s("p_quota_type_code"))) {
      errors.add("Not a valid quota type for File Type " + type() + ".");
    }
    String[] ordinals = {"First", "Second", "Third", "Fourth"};
    Set<String> marks = new HashSet<>();
    boolean duplicate = false;
    for (int i = 1; i <= 4; i++) {
      String mark = s("p_other_marks_used" + i);
      if (mark.isEmpty()) {
        continue;
      }
      if (!checks.timberMarkExists(mark)) {
        errors.add("Other Marks Used by this licence must be valid. " + ordinals[i - 1]
            + " Other Mark is not a valid Timber Mark.");
      }
      duplicate |= !marks.add(mark.toUpperCase());
    }
    String purpose = s(before.purposeParam());
    boolean deckedTimber = "DT".equals(purpose)
        && (("B07".equals(type()) && !s("p_bcts_org_unit").isEmpty())
            || typeIn("B05", "B06"));
    boolean wasDecked = before.is("p_prev_b05_purpose", "DT");
    if (!"PA".equals(prev) && showMark && !deckedTimber && !wasDecked) {
      required("p_marking_method_cd", "Compliance Method");
      if (!"E".equals(s("p_marking_method_cd"))) {
        required("p_markng_instrmnt_cd", "Instrument");
      }
    }
    if (duplicate) {
      errors.add("Other Marks Used by this licence cannot contain duplicate entries.");
    }

    // Annual rent, deposits
    decimal("p_annual_rent_fee", "Annual Rent", "9999999.99", 2, false);
    if (!s("p_security_deposit_amount").isEmpty() && s("p_security_deposit_code").isEmpty()) {
      errors.add("Security Deposit Type must be entered when Security Deposit Amount is.");
    }
    if (!s("p_other_deposit_amount").isEmpty() && s("p_other_deposit_code").isEmpty()) {
      errors.add("Other Deposit Type must be entered when Other Deposit Amount is.");
    }
    decimal("p_security_deposit_amount", "Security Deposit Amount", "999999999.99", 2, false);
    decimal("p_other_deposit_amount", "Other Deposit Amount", "999999999.99", 2, false);

    // Pulpwood AAC
    integer("p_coniferous_aac", "Coniferous", 0, 999_999_999);
    integer("p_deciduous_aac", "Deciduous", 0, 999_999_999);
    integer("p_p01_total_aac", "Sched 'B' AAC", 0, 999_999_999);

    // Timber licence areas
    boolean a06Areas = before.yes("p_show_a06_a30_areas_ind") && !"A06".equals(type());
    if (a06Areas) {
      required("p_init_licence_area", "Initial Area(ha)");
    }
    BigDecimal initial = decimal("p_init_licence_area", "Initial Area(ha)", "9999999.9", 1, true);
    BigDecimal obligated =
        decimal("p_obligation_area", "Obligated Area(ha)", "9999999.9", 1, true);
    BigDecimal eliminated =
        decimal("p_eliminated_area", "Eliminated Area(ha)", "9999999.9", 1, true);
    BigDecimal other = decimal("p_other_area", "Other Area(ha)", "9999999.9", 1, true);
    if (!"A06".equals(type()) && a06Areas) {
      BigDecimal sum = zero(obligated).add(zero(eliminated)).add(zero(other));
      if (sum.compareTo(zero(initial)) != 0) {
        errors.add("Initial Area must equal the sum of Obligation, Eliminated and Other areas.");
      }
    }

    // Zone, location, mark issued, quota
    if (before.yes("p_show_zone_ind") && !"EE".equals(status)) {
      required("p_district_admin_zone", "Zone");
    }
    if ("B05".equals(type()) && !"EE".equals(status)) {
      required("p_permit_block_location", "Location");
    }
    if (showMark && typeIn("B04", "B05", "B06") && s("p_quota_type_code").isEmpty()) {
      errors.add("Quota type is mandatory if file type is " + type() + ".");
    }
    if (s("p_district_override_reason").length() > OVERRIDE_REASON_MAX) {
      errors.add("District Override Reason must not exceed " + OVERRIDE_REASON_MAX
          + " characters.");
    }
    if ("D".equals(s("p_quota_type_code")) && !"Y".equals(s("p_bcts_fund_ind"))) {
      errors.add("Quota type should be C-Forest Service Reserve if this sale is not BCTS funded");
    }

    // Purpose
    if (before.showDepositPurpose() && !before.protectPurpose()) {
      required("p_purpose_code", "Purpose");
    }
    if (before.showMarkPurpose() && !before.protectPurpose()) {
      required("p_b05_purpose", "Purpose");
    }

    // Admin Org; Effective Date once held
    required("p_admin_org_unit_no", "Admin Org");
    if (showTerm && termsOpen && status.startsWith("H") && !"HN".equals(status)) {
      required("p_award_date", "Effective Date");
    }
    date("p_mark_award_date", "Mark Issue Date");
    date("p_mark_extended_date", "Mark Ext. Date");
    integer("p_mark_extension_count", "Mark Ext. Count", Long.MIN_VALUE, Long.MAX_VALUE);
    if (typeIn("E02", "E03") && (!"PA".equals(prev) || !"PA".equals(status))) {
      required("p_tenure_term_months", "Tenure Term (months)");
      required("p_tenure_term_years", "Tenure Term (years)");
    }
    if (!before.protectExpiryDate() && showTerm
        && status.startsWith("H") && !"HN".equals(status) && s("p_expiry_date").isEmpty()
        && isZeroOrBlank(s("p_tenure_term_years")) && isZeroOrBlank(s("p_tenure_term_months"))) {
      errors.add(typeIn("B04", "B05", "B06")
          ? "Initial Expiry Date is mandatory."
          : "Tenure term or Initial Expiry date is required.");
    }
    if (showTerm && termsOpen && award != null && expiry != null && !expiry.isAfter(award)) {
      errors.add("Initial Expiry Date must be after Effective Date.");
    }
    if (before.oldRecreation()) {
      required("p_rec_project_name", "File Name");
    }
    if ("B07".equals(type()) && "Y".equals(s("p_bcts_fund_ind"))
        && !"D".equals(s("p_quota_type_code"))) {
      errors.add("If BCTS Fund is Y then Quota Type must be D.");
    }
    if (typeIn("A18", "A31") && showTerm && termsOpen && award != null && extended != null) {
      int max = "A18".equals(type()) ? 10 : 5;
      if (greaterThanYearsApart(award, extended, max)) {
        errors.add(type() + " files can only be extended up to a maximum of " + max + " years.");
      }
    }
    if ("PA".equals(purpose) && !"P".equals(s("p_quota_type_code"))) {
      errors.add("If purpose is PA then quota type can only be P.");
    }

    // Pulpwood agreement file
    boolean pulpwoodShown = before.showPulpwoodAgreement() || "PA".equals(purpose);
    if (pulpwoodShown && !s("p_pulpwood_file").isEmpty()) {
      if (!checks.fileExists(s("p_pulpwood_file"))) {
        errors.add("The pulpwood file specified does not exist.");
      } else if (!checks.sameLicensee(s("p_forest_file_id"), s("p_pulpwood_file"))) {
        errors.add("The licensee for this file must be the same as the pulpwood agreement"
            + " holder.");
      }
    }
    if (pulpwoodShown && before.yes("p_show_b05_ind") && !"A18".equals(type())
        && "PA".equals(purpose) && s("p_pulpwood_file").isEmpty() && "HI".equals(status)) {
      errors.add("Pulpwood File (Associated with Pulpwood Agreement) is required if Purpose is"
          + " PA.");
    }
    if (((before.showDepositPurpose() || before.showMarkPurpose()) && !before.protectPurpose())
        && before.yes("p_show_bcts_ind") && "Y".equals(s("p_bcts_fund_ind"))
        && !s("p_bcts_org_unit").isEmpty()
        && !Set.of("RW", "DT", "TC").contains(purpose)) {
      errors.add("Purpose can only be RW, DT or TC.");
    }
    if ("B07".equals(type()) && showMark && !eeExempt() && s("p_quota_type_code").isEmpty()) {
      errors.add("Quota type is mandatory if file type is B07.");
    }
    if (before.yes("p_show_bcts_ind")) {
      required("p_bcts_fund_ind", "BCTS Fund");
    }
    if (!before.yes("p_disable_area_ind")) {
      decimal("p_permit_block_area", "Sched. B (ha)", "99999999999.9999", 4, false);
    }
    if (before.yes("p_show_payment_ind") && s("p_payment_method_cd").isEmpty()) {
      errors.add("Payment Method is mandatory.");
    }
    if (!before.hideSalvage()) {
      required("p_salvage_ind", "Salvage");
    }
    if (!s("p_pulpwood_file").isEmpty() && !"PA".equals(purpose)) {
      errors.add("Pulpwood Agreement file must be blank if purpose code is not PA.");
    }
    if (typeIn("B05", "B06", "B07") && status.startsWith("P")
        && s("p_sub_spatial_later_ind").isEmpty()) {
      errors.add("Submit Spatial Later is mandatory.");
    }
    if (before.yes("p_show_replaceable_fields_ind") && s("p_licence_replaceable_ind").isEmpty()) {
      errors.add("Replaceable is mandatory.");
    }
    if (replaced != null && award != null && !replaced.isAfter(award)) {
      errors.add("Replaced Date must be after Effective Date.");
    }
  }

  private static boolean isZeroOrBlank(String value) {
    return value.isEmpty() || "0".equals(value);
  }

  private static BigDecimal zero(BigDecimal n) {
    return n == null ? BigDecimal.ZERO : n;
  }
}
