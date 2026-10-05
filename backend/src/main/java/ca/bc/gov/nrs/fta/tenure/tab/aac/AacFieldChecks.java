package ca.bc.gov.nrs.fta.tenure.tab.aac;

import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacRow;
import ca.bc.gov.nrs.fta.tenure.tab.aac.AacDtos.AacSaveRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The field checks of legacy FTA930's two saves, in legacy's order and with its message
 * texts: {@code Fta930AacForm}'s validators for "Save" (the areas) and "SaveAAC" (an AAC
 * history row), plus the PL/SQL's Schedule A check. Pure, so it is unit-tested without a
 * database. Where legacy had a validator but no message text (the FRA volume rules), the
 * message is written here.
 */
public final class AacFieldChecks {

  private AacFieldChecks() {}

  static final BigDecimal MAX_AREA = new BigDecimal("9999999.9999");
  static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.9999");
  static final BigDecimal MAX_FRA = new BigDecimal("999999999.9999");
  static final int MAX_COMMENT = 100;

  static final String SCHEDULE_A = "Private/Schedule A";
  static final String SCHEDULE_B = "Crown/Schedule B";
  static final String FRA = "FRA(Bill 28,2003)m3";

  /** The "Save" checks of the Area (ha) section — both areas optional. */
  public static List<String> areas(BigDecimal scheduleA, BigDecimal scheduleB) {
    List<String> e = new ArrayList<>();
    decimal(e, SCHEDULE_A, scheduleA, MAX_AREA);
    decimal(e, SCHEDULE_B, scheduleB, MAX_AREA);
    return e;
  }

  /**
   * The "SaveAAC" checks.
   *
   * @param q            the row as entered (codes already trimmed and upper-cased)
   * @param rows         the file's AAC history, most recent period first (legacy's order)
   * @param editing      the row being changed, or null for a new one
   * @param awardDate    the tenure's legal effective date, if any
   * @param expiryDate   the tenure's expiry date, if any
   * @param fileTypeCode the tenure's file type
   */
  public static List<String> save(
      AacSaveRequest q,
      List<AacRow> rows,
      AacRow editing,
      LocalDate awardDate,
      LocalDate expiryDate,
      String fileTypeCode) {
    List<String> e = new ArrayList<>();
    LocalDate date = q.effectiveDate();

    // effectiveDate (DateValidator, required)
    if (date == null) {
      e.add("Effective Date is mandatory.");
    } else {
      // effDateGreaterTenureEffDate: AAC effective date >= the award date
      if (awardDate != null && date.isBefore(awardDate)) {
        e.add("AAC Effective Date must be after Tenure Effective Date.");
      }
      // effectiveDateSequence
      String sequence = sequenceProblem(date, rows, editing, expiryDate);
      if (sequence != null) {
        e.add(sequence);
      }
    }

    // amount: required, range, precision (a chain: the first failure only)
    if (q.amount() == null) {
      e.add("Amount is mandatory.");
    } else {
      decimal(e, "Amount", q.amount(), MAX_AMOUNT);
    }

    if (q.areaTypeCode() == null) {
      e.add("Area Type is mandatory.");
    }
    if (q.cutTypeCode() == null) {
      e.add("Cut type is mandatory.");
    }
    if (q.unitOfMeasureCode() == null) {
      e.add("Unit of Measure is mandatory.");
    }

    // unitConsistent: a new amount joins an existing period only in that period's unit.
    if (editing == null && date != null && q.unitOfMeasureCode() != null) {
      AacRow period = rowByDate(rows, date);
      if (period != null && !period.unitOfMeasureCode().equals(q.unitOfMeasureCode())) {
        e.add("Unit of measure entered for the period " + period.effectiveDate()
            + " must match its existing " + period.unitOfMeasureCode() + " units");
      }
    }

    if (q.reasonCode() == null) {
      e.add("Reason is mandatory.");
    }
    if ("OTH".equals(q.reasonCode()) && q.comment() == null) {
      e.add("Comment is mandatory when Reason is OTH-Other");
    }
    if (q.comment() != null && q.comment().length() > MAX_COMMENT) {
      e.add("Comment cannot exceed " + MAX_COMMENT + " characters.");
    }

    // shareVolRequired (FTA-907): Y needs the FRA volume, N or unknown must not have one.
    String shareable = shareable(q.revenueShareable());
    if (shareable == null) {
      e.add("Revenue Share must be Yes, No or blank.");
    } else if ("Y".equals(shareable) && q.fra2003Volume() == null) {
      e.add(FRA + " is mandatory when Revenue Share is Yes.");
    } else if (!"Y".equals(shareable) && q.fra2003Volume() != null) {
      e.add(FRA + " must be blank unless Revenue Share is Yes.");
    }

    // measureM3FraLessThanAmount
    if ("M3".equals(q.unitOfMeasureCode())
        && q.fra2003Volume() != null
        && q.amount() != null
        && q.fra2003Volume().compareTo(q.amount()) > 0) {
      e.add(FRA + " (" + q.fra2003Volume().toPlainString()
          + ") cannot be more than the Amount (" + q.amount().toPlainString()
          + ") when the unit of measure is M3.");
    }

    if (q.fra2003Volume() != null) {
      decimal(e, FRA, q.fra2003Volume(), MAX_FRA);
    }

    // effectiveDateAreaTypeCutType: a new row's (date, area type, cut type) must be new.
    if (editing == null && date != null && q.areaTypeCode() != null && q.cutTypeCode() != null) {
      boolean duplicate = rows.stream().anyMatch(r -> date.equals(r.effectiveDate())
          && q.areaTypeCode().equals(r.areaTypeCode())
          && q.cutTypeCode().equals(r.cutTypeCode()));
      if (duplicate) {
        e.add("A record already exists with the same effective date, area type and cut type");
      }
    }

    // FTA_930_AAC SAVE_AAC
    if ("A".equals(q.areaTypeCode())
        && (fileTypeCode == null || !AacRules.SCHEDULE_A_TYPES.contains(fileTypeCode))) {
      e.add(AacRules.SCHEDULE_A_MESSAGE);
    }
    return e;
  }

  /**
   * Legacy's {@code effectiveDateSequence}: no AAC may start after the tenure expires, and a
   * changed row's period may not move past the next more recent period.
   */
  static String sequenceProblem(
      LocalDate date, List<AacRow> rows, AacRow editing, LocalDate expiryDate) {
    if (expiryDate != null && date.isAfter(expiryDate)) {
      return "Effective Date cannot be after the tenure Expiry Date";
    }
    if (editing == null || !date.isAfter(editing.effectiveDate())) {
      return null;
    }
    int index = rows.indexOf(editing);
    for (int i = index - 1; i >= 0; i--) {
      AacRow row = rows.get(i);
      if (row.periodId() != editing.periodId()) {
        return date.isAfter(row.effectiveDate())
            ? "Effective Date value cannot exceed " + row.effectiveDate()
            : null;
      }
    }
    return null;
  }

  /** The first row whose period starts on {@code date} (legacy's getAACRowByDate). */
  static AacRow rowByDate(List<AacRow> rows, LocalDate date) {
    return rows.stream().filter(r -> date.equals(r.effectiveDate())).findFirst().orElse(null);
  }

  /** Y, N or U (blank); null when it is anything else. */
  static String shareable(String value) {
    if (value == null || value.isBlank()) {
      return "U";
    }
    String v = value.trim().toUpperCase(Locale.ROOT);
    return "Y".equals(v) || "N".equals(v) || "U".equals(v) ? v : null;
  }

  /** Legacy's FloatRange (0 to max) and DecimalPlaces (4) validators for one value. */
  private static void decimal(List<String> e, String label, BigDecimal value, BigDecimal max) {
    if (value == null) {
      return;
    }
    if (value.signum() < 0 || value.compareTo(max) > 0) {
      e.add(label + " field must be between 0.0 and " + max.toPlainString() + ".");
    } else if (value.stripTrailingZeros().scale() > 4) {
      e.add("No more than four decimal places are permitted for " + label);
    }
  }
}
