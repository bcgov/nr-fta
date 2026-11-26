package ca.bc.gov.nrs.fta.shared.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FTA675 Audit Report's checks and choices, from {@code Fta675AuditReportForm} and the
 * {@code FTAR403P} / {@code FTAR405P} procedures, with the database reads done by the caller.
 */
final class AuditReportRules {

  /** "*" in the Region and District lists, and in the first File Type: all of them. */
  static final String ALL = "*";

  static final String REGION_REQUIRED = "Region Code is mandatory.";
  static final String DISTRICT_REQUIRED = "District Code is mandatory.";
  static final String FROM_INVALID = "Date Range From must be a valid date in the format YYYY-MM-DD.";
  static final String TO_INVALID = "Date Range To must be a valid date in the format YYYY-MM-DD.";
  static final String FROM_AFTER_TO = "Date Range From must be less than or equal to Date Range To.";
  static final String DUPLICATE_FILE_TYPES = "File Type must have unique entries.";
  static final String NOT_IN_REGION = "District does not fall within the region.";
  static final String REPORT_REQUIRED = "Report is mandatory.";

  private static final int FILE_TYPES = 5;

  private AuditReportRules() {}

  /** The two reports FTA675 offers. */
  enum Report {
    /** FTA403 District Override Audit, from FDC_AUDIT_LOG (FTAR403P). */
    FTA403("reports/FTA403_DistrictOverrideAudit.jrxml", "FTA403-district-override-audit.pdf"),
    /** FTA405 Delete EE Audit, from FTAEE_AUDIT_LOG (FTAR405P). */
    FTA405("reports/FTA405_DeleteEEAudit.jrxml", "FTA405-delete-ee-audit.pdf");

    final String template;
    final String filename;

    Report(String template, String filename) {
      this.template = template;
      this.filename = filename;
    }

    static Report of(String name) {
      if (name == null) {
        return null;
      }
      for (Report r : values()) {
        if (r.name().equalsIgnoreCase(name.trim())) {
          return r;
        }
      }
      return null;
    }
  }

  /**
   * Which org units' audit records a report covers, per the procedures' cursors. Region and
   * District are both mandatory on the form, so their "neither given" branches never run.
   */
  enum Scope {
    /** Region "*", District "*": every district and region. */
    ALL,
    /** A region, District "*": every org unit that rolls up to the region. */
    REGION,
    /** Region "*", a district. */
    DISTRICT_ANY_REGION,
    /** A region and a district: the district's own records. */
    DISTRICT;

    static Scope of(String region, String district) {
      // ALL here would be this enum's constant; "*" is the outer class's.
      boolean allRegions = AuditReportRules.ALL.equals(region);
      boolean allDistricts = AuditReportRules.ALL.equals(district);
      if (allRegions && allDistricts) {
        return Scope.ALL;
      }
      if (allDistricts) {
        return REGION;
      }
      return allRegions ? DISTRICT_ANY_REGION : DISTRICT;
    }
  }

  /**
   * What the user submitted. Region and district are an ORG_UNIT_NO or "*"; dates are
   * {@code YYYY-MM-DD} or blank.
   */
  record Criteria(
      String report,
      String region,
      String district,
      String fromDate,
      String toDate,
      List<String> fileTypes) {}

  /**
   * Submit's validators, in the form's order: Region and District required, both dates valid,
   * From ≤ To, no file type twice, the district within the region, and a report chosen.
   *
   * @param districtInRegion whether the district falls within the region; only consulted when
   *                         both are a specific org unit
   */
  static List<String> validate(Criteria c, boolean districtInRegion) {
    List<String> e = new ArrayList<>();
    String region = blank(c.region());
    String district = blank(c.district());
    if (!isOrgUnit(region)) {
      e.add(REGION_REQUIRED);
    }
    if (!isOrgUnit(district)) {
      e.add(DISTRICT_REQUIRED);
    }
    LocalDate from = null;
    LocalDate to = null;
    boolean datesValid = true;
    if (!blank(c.fromDate()).isEmpty()) {
      from = date(c.fromDate());
      if (from == null) {
        e.add(FROM_INVALID);
        datesValid = false;
      }
    }
    if (!blank(c.toDate()).isEmpty()) {
      to = date(c.toDate());
      if (to == null) {
        e.add(TO_INVALID);
        datesValid = false;
      }
    }
    if (datesValid && from != null && to != null && from.isAfter(to)) {
      e.add(FROM_AFTER_TO);
    }
    if (hasDuplicates(c.fileTypes())) {
      e.add(DUPLICATE_FILE_TYPES);
    }
    if (isOrgUnit(region) && isOrgUnit(district) && !ALL.equals(region)
        && !ALL.equals(district) && !districtInRegion) {
      e.add(NOT_IN_REGION);
    }
    if (Report.of(c.report()) == null) {
      e.add(REPORT_REQUIRED);
    }
    return e;
  }

  /**
   * forceFormats: the file types with the blanks squeezed out, padded back to five with blanks,
   * so the first chosen type is always the first.
   */
  static List<String> compactFileTypes(List<String> fileTypes) {
    List<String> out = new ArrayList<>();
    if (fileTypes != null) {
      for (String t : fileTypes) {
        String v = blank(t).toUpperCase(Locale.ROOT);
        if (!v.isEmpty() && out.size() < FILE_TYPES) {
          out.add(v);
        }
      }
    }
    while (out.size() < FILE_TYPES) {
      out.add("");
    }
    return out;
  }

  /** Whether the first file type (after {@link #compactFileTypes}) means every type. */
  static boolean allFileTypes(List<String> compacted) {
    String first = compacted.get(0);
    return first.isEmpty() || ALL.equals(first);
  }

  /** The start of the report's entry-timestamp range: the From date, or 0001-01-01. */
  static LocalDateTime from(String fromDate) {
    LocalDate d = blank(fromDate).isEmpty() ? LocalDate.of(1, 1, 1) : date(fromDate);
    return d.atStartOfDay();
  }

  /**
   * The end of the range: 9999-12-31 when To is blank. FTAR403P takes the whole To day
   * ({@code 11:59:59 PM}); FTAR405P takes To at midnight, so a deletion later that day is not
   * listed. Both kept as they were.
   */
  static LocalDateTime to(Report report, String toDate) {
    if (blank(toDate).isEmpty()) {
      return LocalDate.of(9999, 12, 31).atStartOfDay();
    }
    LocalDate d = date(toDate);
    return report == Report.FTA403 ? d.atTime(LocalTime.of(23, 59, 59)) : d.atStartOfDay();
  }

  /**
   * FTAR405P's OUT_MESSAGE line under the header.
   *
   * @param regionName   the region's name, when one region was chosen
   * @param districtName the district's name, when one district was chosen
   */
  static String outMessage(Scope scope, String regionName, String districtName) {
    return switch (scope) {
      case ALL -> "All Regions and Districts";
      case REGION -> regionName == null ? null : regionName + " Only ";
      case DISTRICT_ANY_REGION, DISTRICT -> districtName == null ? null : districtName + " Only ";
    };
  }

  private static boolean hasDuplicates(List<String> fileTypes) {
    if (fileTypes == null) {
      return false;
    }
    List<String> seen = new ArrayList<>();
    for (String t : fileTypes) {
      String v = blank(t).toUpperCase(Locale.ROOT);
      if (v.isEmpty()) {
        continue;
      }
      if (seen.contains(v)) {
        return true;
      }
      seen.add(v);
    }
    return false;
  }

  private static boolean isOrgUnit(String v) {
    return ALL.equals(v) || v.matches("\\d+");
  }

  private static LocalDate date(String s) {
    String v = blank(s);
    if (!v.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return null;
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  static String blank(String s) {
    return s == null ? "" : s.trim();
  }
}
