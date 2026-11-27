package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.CopyRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.SaveRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The checks legacy {@code Fta613RangeUsageForm} ran before FTA613R's Save and Copy, with its
 * messages, in its order. Each check reports independently, as legacy's validators did.
 *
 * <p>Grazing files (type starting E) carry a non-use (billable and non-billable, with an
 * agreement number and a reason); hay cutting files (type starting H) a temporary reduction
 * (the non-billable column, with an agreement number and a reason). Labels follow.
 *
 * <p>Copy ran the same field checks as Save on the row being copied, but not the PE status
 * check, which legacy added to Save alone.
 */
public final class RangeUsageRules {

  static final int MAX_AMOUNT = 99999;
  static final int MIN_YEAR = 1000;
  static final int MAX_YEAR = 99999;

  static final String NO_SAVE_WHEN_PE =
      "No updates can be performed on this file when the status is PE.";
  static final String YEAR_REQUIRED = "Year is mandatory.";
  static final String PENDING = "You may not update while application is in the Inbox.";

  static final String COPY_ALL_AND_YEARS =
      "Source/To years must be blank when All remaining years is blank";
  static final String COPY_NEITHER =
      "One of either All Remaining years or Source/To years must be entered, but not both.";
  static final String COPY_BOTH_YEARS =
      "If either Source years or To years are entered they both must contain values.";
  static final String COPY_FROM_AFTER_TO = "From Year must be before To Year.";

  static final String SAVE_SUCCESS = "Save successful.";
  static final String COPY_SUCCESS = "Copy successful for range usage records.";

  /** What the checks read besides the entered values. */
  public record Context(
      String fileTypeCode,
      String fileStatus,
      Set<String> currentIncreaseReasons,
      Set<String> currentNonUseReasons,
      String storedIncreaseReason,
      String storedNonUseReason) {}

  private RangeUsageRules() {}

  /** Legacy's "Save" validators. */
  public static List<String> validateSave(Integer year, SaveRequest r, Context ctx) {
    List<String> e = new ArrayList<>();
    String status = ctx.fileStatus() == null ? "" : ctx.fileStatus();
    if (status.length() >= 2 && status.substring(0, 2).equalsIgnoreCase("PE")) {
      e.add(NO_SAVE_WHEN_PE);
    }
    if (year == null) {
      e.add(YEAR_REQUIRED);
    }
    e.addAll(fieldChecks(r, ctx));
    return e;
  }

  /** Legacy's "Copy" validators: the copy's years, then the copied row's field checks. */
  public static List<String> validateCopy(Integer year, CopyRequest c, SaveRequest row,
      Context ctx) {
    List<String> e = new ArrayList<>();
    if (year == null) {
      e.add(YEAR_REQUIRED);
    }
    boolean all = c.allRemainingYears();
    boolean noFrom = blank(c.copyFromYear());
    boolean noTo = blank(c.copyToYear());
    if (all && (!noFrom || !noTo)) {
      e.add(COPY_ALL_AND_YEARS);
    } else if (!all && noFrom && noTo) {
      e.add(COPY_NEITHER);
    } else if (!all && (noFrom || noTo)) {
      e.add(COPY_BOTH_YEARS);
    }
    Integer from = intOrNull(c.copyFromYear());
    Integer to = intOrNull(c.copyToYear());
    if (from != null && to != null && from > to) {
      e.add(COPY_FROM_AFTER_TO);
    }
    yearLimit(e, c.copyFromYear(), "Year Source");
    yearLimit(e, c.copyToYear(), "Year To");
    e.addAll(fieldChecks(row, ctx));
    return e;
  }

  /** Authorized Use + Increase - (Billable + Non-Billable), blanks as 0, as legacy saved it. */
  public static int totalAnnualUse(SaveRequest r) {
    try {
      return (intOrZero(r.authorizedUse()) + intOrZero(r.tempIncrease()))
          - (intOrZero(r.nonUseBillable()) + intOrZero(r.nonUseNonbillable()));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  static boolean grazing(String fileTypeCode) {
    return fileTypeCode != null && fileTypeCode.startsWith("E");
  }

  static boolean hay(String fileTypeCode) {
    return fileTypeCode != null && fileTypeCode.startsWith("H");
  }

  /** The non-billable column's label: Non-Use Non-Billable (grazing) or Temporary Reduction. */
  static String nonBillableLabel(String fileTypeCode) {
    return grazing(fileTypeCode) ? "Non-Use Non-Billable" : "Temporary Reduction";
  }

  /** The non-use's label in the combined checks: Non-Use (grazing) or Temporary Reduction. */
  static String nonUseLabel(String fileTypeCode) {
    return grazing(fileTypeCode) ? "Non-Use" : "Temporary Reduction";
  }

  private static List<String> fieldChecks(SaveRequest r, Context ctx) {
    List<String> e = new ArrayList<>();
    String type = ctx.fileTypeCode();
    if (blank(r.authorizedUse())) {
      e.add("Authorized Use is mandatory.");
    } else {
      integer(e, r.authorizedUse(), "Authorized Use");
    }
    integer(e, r.tempIncrease(), "Temporary Increase Amount");
    integer(e, r.nonUseAgreementNo(), "Non-Use Agreement No");
    integer(e, r.nonUseBillable(), "Non-Use Billable");
    integer(e, r.nonUseNonbillable(), nonBillableLabel(type));

    expired(e, "Increase Reason", r.rangeIncreaseReasonCode(), ctx.currentIncreaseReasons(),
        ctx.storedIncreaseReason());
    expired(e, "Non-Use Reason", r.rangeNonUseReasonCode(), ctx.currentNonUseReasons(),
        ctx.storedNonUseReason());

    boolean noIncrease = emptyOrZero(r.tempIncrease());
    boolean noIncreaseReason = blank(r.rangeIncreaseReasonCode());
    if (noIncrease != noIncreaseReason) {
      e.add(both("Temporary Increase amount", "Increase Reason"));
    }
    boolean noBillable = emptyOrZero(r.nonUseBillable());
    boolean noNonBillable = emptyOrZero(r.nonUseNonbillable());
    boolean noAgreement = emptyOrZero(r.nonUseAgreementNo());
    boolean noNonUseReason = blank(r.rangeNonUseReasonCode());
    if ((!noBillable || !noNonBillable) && noAgreement && grazing(type)) {
      e.add(ifThen("Billable or Non-Billable non-use", "Agreement No."));
    }
    if (noBillable && noNonBillable && (!noAgreement || !noNonUseReason) && grazing(type)) {
      e.add("Agreement No. and Non-Billable Reason must be blank when Billable or Non-Billable"
          + " non-use is 0.");
    }
    if (!noNonBillable && noAgreement && hay(type)) {
      e.add(ifThen("Temporary Reduction amount", "Agreement No."));
    }
    if (noNonBillable != noNonUseReason) {
      String label = nonBillableLabel(type);
      e.add(both(label + " amount", label + " Reason"));
    }
    if ((!noBillable || !noNonBillable) && !noIncrease) {
      e.add("You may only have a Temporary Increase or a " + nonUseLabel(type)
          + ". Not both. Please update and re-submit.");
    }
    if (nonUseExceedsAuthorized(r)) {
      e.add("The " + nonUseLabel(type)
          + " amount cannot be greater than the authorized_use for the year");
    }
    return e;
  }

  /** fta.web.error.usr.field.and. */
  private static String both(String a, String b) {
    return "If either " + a + " or " + b + " are entered they both must contain values.";
  }

  /** fta.web.error.blankWhenBlank2. */
  private static String ifThen(String a, String b) {
    return "If " + a + " is provided, " + b + " must be provided.";
  }

  /** IntegerFieldValidator, then IntegerLimitsValidator 0..99999. */
  private static void integer(List<String> e, String value, String label) {
    if (blank(value)) {
      return;
    }
    Integer n = intOrNull(value);
    if (n == null) {
      e.add(label + " must be an integer.");
    } else if (n < 0 || n > MAX_AMOUNT) {
      e.add(label + " field must be between 0 and " + MAX_AMOUNT + ".");
    }
  }

  /** IntegerLimitsValidator 1000..99999 on a copy year. */
  private static void yearLimit(List<String> e, String value, String label) {
    if (blank(value)) {
      return;
    }
    Integer n = intOrNull(value);
    if (n == null || n < MIN_YEAR || n > MAX_YEAR) {
      e.add(label + " field must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
    }
  }

  /** CodeExpiryValidator: a code must be current, unless the row already holds it. */
  private static void expired(List<String> e, String label, String code, Set<String> current,
      String stored) {
    if (blank(code) || Objects.equals(code.trim(), stored) || current.contains(code.trim())) {
      return;
    }
    e.add(label + " (" + code.trim() + ") is an expired code.");
  }

  /** checkAuthUseLessThanNonUse: billable + non-billable over the authorized use. */
  private static boolean nonUseExceedsAuthorized(SaveRequest r) {
    try {
      return intOrZero(r.nonUseBillable()) + intOrZero(r.nonUseNonbillable())
          > intOrZero(r.authorizedUse());
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  /** isEmptyNumericZero: blank, or digits worth 0. Anything else is a value. */
  static boolean emptyOrZero(String s) {
    if (blank(s)) {
      return true;
    }
    String t = s.trim();
    try {
      return t.chars().allMatch(Character::isDigit) && Integer.parseInt(t) == 0;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  static boolean blank(String s) {
    return s == null || s.trim().isEmpty();
  }

  static Integer intOrNull(String s) {
    if (blank(s)) {
      return null;
    }
    try {
      return Integer.valueOf(s.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static int intOrZero(String s) {
    return blank(s) ? 0 : Integer.parseInt(s.trim());
  }

  /** Blank to null, else trimmed. */
  static String trimToNull(String s) {
    return blank(s) ? null : s.trim();
  }
}
