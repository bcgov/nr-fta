package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitRequest;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The checks APT2 made on a management unit and its AAC determinations before calling the
 * routines, plus the column limits of {@code APT_MANAGEMENT_UNITS} and
 * {@code APT_MGMT_UNIT_HISTORY}, so a bad value is a readable 400 instead of an ORA- message.
 * Pure, so it is unit-tested; the partition rules reuse its number checks.
 */
public final class AptManagementUnitRules {

  /** NUMBER(9) columns: AAC, AAC (ha), long term harvest level. */
  static final BigDecimal MAX_WHOLE = new BigDecimal("999999999");
  /** NUMBER(9,2) columns: the four areas. */
  static final BigDecimal MAX_AREA = new BigDecimal("9999999.99");
  static final int MAX_COMMENTS = 2000;

  /** APT2's answer to a management unit id that is zero or negative. */
  public static final String MANAGEMENT_UNIT_ID_POSITIVE =
      "Management Unit ID must be a positive integer.";

  private AptManagementUnitRules() {}

  /**
   * Refuses a management unit id that is not a positive integer, as APT2 does for the id in
   * its {@code /management-units/{id}} paths and APTR011's unit.
   *
   * @throws ResponseStatusException 400 with {@link #MANAGEMENT_UNIT_ID_POSITIVE}
   */
  public static void requirePositiveId(Long id) {
    if (id == null || id < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MANAGEMENT_UNIT_ID_POSITIVE);
    }
  }

  /** Null when the management unit can be saved, else the first problem. */
  public static String managementUnitProblem(AptManagementUnitRequest r) {
    if (r == null) {
      return "A management unit is required.";
    }
    if (r.orgUnitNo() == null) {
      return "Org Unit is required.";
    }
    if (blank(r.muType())) {
      return "Management Unit Type is required.";
    }
    if (r.muType().trim().length() > 1) {
      return "Management Unit Type must be a single character.";
    }
    if (blank(r.muNo())) {
      return "Management Unit Number is required.";
    }
    if (r.muNo().trim().length() > 4) {
      return "Management Unit Number can be at most 4 characters.";
    }
    if (blank(r.status())) {
      return "Status is required.";
    }
    if (r.status().trim().length() > 3) {
      return "Status must be a status code.";
    }
    if (r.effectiveDate() == null) {
      return "Status Effective Date is required.";
    }
    if (r.orcsFileNumber() != null && r.orcsFileNumber().trim().length() > 13) {
      return "ORCS File Number can be at most 13 characters.";
    }
    return commentsProblem(r.comments());
  }

  /** Null when the determination can be saved, else the first problem. */
  public static String historyProblem(AptManagementUnitHistoryRequest r) {
    if (r == null) {
      return "A management unit history record is required.";
    }
    if (r.effectiveDate() == null) {
      return "Effective Date is required.";
    }
    if (r.determinationDate() == null) {
      return "AAC Determination Date is required.";
    }
    if (r.allowableAnnualCut() == null) {
      return "AAC (m³) is required.";
    }
    String p = firstNonNull(
        wholeProblem("AAC (m³)", r.allowableAnnualCut()),
        wholeProblem("AAC (ha)", r.allowableAnnualCutHa()),
        wholeProblem("Long Term Harvest Level", r.longTermHarvestLevel()),
        areaProblem("Total Area", r.totalArea()),
        areaProblem("Productive Area", r.productiveArea()),
        areaProblem("Current Net Operating Area", r.currentNetOperatingArea()),
        areaProblem("Future Harvest Landbase Area", r.futureHarvestLandbaseArea()));
    return p != null ? p : commentsProblem(r.comments());
  }

  /**
   * Splits {@code APT_MGMT_UNIT_UNASSIGNED_GET}'s {@code MGMT_UNIT_CODE} (type and number run
   * together) into the two. The type column is {@code VARCHAR2(1)}, so the type is the first
   * character and the number the rest; APT2's {@code ^([A-Z]+)(\d+)$} would misread a number with
   * a letter in it.
   *
   * @return {@code {type, number}}; number is null when the code has none (the {@code Z} type)
   */
  public static String[] splitUnassignedCode(String code) {
    if (code == null || code.isBlank()) {
      return new String[] {null, null};
    }
    String c = code.trim();
    String number = c.length() > 1 ? c.substring(1) : null;
    return new String[] {c.substring(0, 1), number};
  }

  // ── shared checks ──

  static String wholeProblem(String label, BigDecimal v) {
    if (v == null) {
      return null;
    }
    if (v.signum() < 0 || v.compareTo(MAX_WHOLE) > 0) {
      return label + " must be between 0 and 999,999,999.";
    }
    if (v.stripTrailingZeros().scale() > 0) {
      return label + " must be a whole number.";
    }
    return null;
  }

  static String areaProblem(String label, BigDecimal v) {
    if (v == null) {
      return null;
    }
    if (v.signum() < 0 || v.compareTo(MAX_AREA) > 0) {
      return label + " must be between 0 and 9,999,999.99.";
    }
    if (v.stripTrailingZeros().scale() > 2) {
      return label + " can have at most 2 decimal places.";
    }
    return null;
  }

  static String commentsProblem(String comments) {
    return comments != null && comments.length() > MAX_COMMENTS
        ? "Comments cannot exceed 2000 characters." : null;
  }

  static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String firstNonNull(String... problems) {
    for (String p : problems) {
      if (p != null) {
        return p;
      }
    }
    return null;
  }
}
