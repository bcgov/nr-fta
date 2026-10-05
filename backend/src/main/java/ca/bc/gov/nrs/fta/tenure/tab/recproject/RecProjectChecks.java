package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The field checks of legacy FTA701, with their message texts — {@code Fta701MaintainProjectForm}'s
 * validators for Save and SaveFee (in the order legacy registers them), and the day checks of
 * {@code FTA_701_PROJECT_DETAILS.validate_fee}. Pure: no database.
 */
public final class RecProjectChecks {

  private RecProjectChecks() {}

  /** Project types that are trails: Right of Way is entered, and required (FeatureTypeCodes). */
  static final Set<String> TRAIL_TYPES = Set.of("RTR", "IFT", "TBL", "RTE");

  /** UTM zones legacy's UTMToBCAlbersConverter can place in BC. */
  static final int MIN_UTM_ZONE = 7;
  static final int MAX_UTM_ZONE = 11;

  static final int MAX_PROJECT_NAME = 100;
  static final int MAX_SITE_LOCATION = 500;
  static final int MAX_BORDEN = 200;
  static final int MAX_AIA_COMMENT = 2000;
  static final int MAX_FIELD_NOTE = 500;

  /** Blank to null, otherwise trimmed. */
  static String trim(String v) {
    if (v == null) {
      return null;
    }
    String t = v.trim();
    return t.isEmpty() ? null : t;
  }

  /** Project details (FTA701 "Save"). */
  public static List<String> project(RecProjectRequests.Save r, boolean trailProject) {
    List<String> e = new ArrayList<>();
    if (trim(r.projectName()) == null) {
      e.add(required("Project Name"));
    } else {
      length(e, r.projectName(), MAX_PROJECT_NAME, "Project Name");
    }
    if ("Y".equals(trim(r.recreationViewInd()))
        && (trim(r.utmEasting()) == null
            || trim(r.utmNorthing()) == null
            || trim(r.utmZone()) == null)) {
      e.add("UTM Easting, UTM Northing and UTM Zone are mandatory when Webmap is Yes.");
    }
    if (trim(r.resourceFeatureInd()) == null) {
      e.add(required("Resource Feature"));
    }
    if (trim(r.lowMobilityAccessInd()) == null) {
      e.add(required("Low Mobility Access"));
    }
    length(e, r.bordenNo(), MAX_BORDEN, "Borden #");
    length(e, r.aiaComment(), MAX_AIA_COMMENT, "AIA Comment");
    length(e, r.siteDescription(), MAX_FIELD_NOTE, "Field Note");
    length(e, r.siteLocation(), MAX_SITE_LOCATION, "Closest Community");
    integer(e, r.overflowCampsites(), "Overflow Campsites", 0, 99999);
    boolean zoneOk = integer(e, r.utmZone(), "UTM Zone", 0, 99999);
    boolean northingOk = integer(e, r.utmNorthing(), "UTM Northing", 0, 9999999999L);
    boolean eastingOk = integer(e, r.utmEasting(), "UTM Easting", 0, 9999999999L);
    if (trailProject) {
      String row = trim(r.rightOfWay());
      if (row == null) {
        e.add(required("Right of Way"));
      } else {
        BigDecimal v = number(row);
        if (v == null) {
          e.add("Right of Way must be numeric.");
        } else if (v.compareTo(BigDecimal.ZERO) < 0
            || v.compareTo(new BigDecimal("99999.9")) > 0) {
          e.add("Right of Way field must be between 0.0 and 99999.9.");
        } else if (v.stripTrailingZeros().scale() > 1) {
          e.add("Right Of Way cannot contain more than 1 places of decimal.");
        }
      }
    }
    // Save's site point: legacy converts the UTM point only for zones 7-11, and only when an
    // easting and northing are given (after the form's checks pass).
    if (zoneOk && northingOk && eastingOk && trim(r.utmEasting()) != null && trim(r.utmNorthing()) != null) {
      String zone = trim(r.utmZone());
      long z = zone == null ? -1 : Long.parseLong(zone);
      if (z < MIN_UTM_ZONE || z > MAX_UTM_ZONE) {
        e.add((zone == null ? "A blank" : zone) + " is not a supported UTM Zone.");
      }
    }
    yesNo(e, r.campHostInd(), "Camp Host/Operator");
    yesNo(e, r.lowMobilityAccessInd(), "Low Mobility Access");
    yesNo(e, r.recreationViewInd(), "Display to Website");
    yesNo(e, r.resourceFeatureInd(), "Resource Feature");
    yesNo(e, r.archImpactAssessInd(), "AIA Indicator");
    return e;
  }

  /** A fee's form checks (FTA701 "SaveFee"). */
  public static List<String> feeForm(RecProjectRequests.Fee f) {
    List<String> e = new ArrayList<>();
    if (trim(f.feeCode()) == null) {
      e.add(required("Fee Type"));
    }
    if (f.startDate() != null && f.endDate() != null && f.startDate().isAfter(f.endDate())) {
      e.add("Start Date must be less than or equal to End Date.");
    }
    String amount = trim(f.amount());
    if (amount == null) {
      e.add(required("Amount"));
    } else {
      BigDecimal v = number(amount);
      if (v == null) {
        e.add("Amount must be numeric.");
      } else if (v.compareTo(BigDecimal.ZERO) < 0
          || v.compareTo(new BigDecimal("999.99")) > 0) {
        e.add("Amount field must be between 0.0 and 999.99.");
      }
    }
    if (f.startDate() == null) {
      e.add(required("Start Date"));
    }
    if (f.endDate() == null) {
      e.add(required("End Date"));
    }
    return e;
  }

  /** The days a fee applies to. */
  static Set<DayOfWeek> days(RecProjectRequests.Fee f) {
    Set<DayOfWeek> d = EnumSet.noneOf(DayOfWeek.class);
    if (f.monday()) {
      d.add(DayOfWeek.MONDAY);
    }
    if (f.tuesday()) {
      d.add(DayOfWeek.TUESDAY);
    }
    if (f.wednesday()) {
      d.add(DayOfWeek.WEDNESDAY);
    }
    if (f.thursday()) {
      d.add(DayOfWeek.THURSDAY);
    }
    if (f.friday()) {
      d.add(DayOfWeek.FRIDAY);
    }
    if (f.saturday()) {
      d.add(DayOfWeek.SATURDAY);
    }
    if (f.sunday()) {
      d.add(DayOfWeek.SUNDAY);
    }
    return d;
  }

  /**
   * {@code validate_fee}'s day checks, once the form passes: at least one day, and every day
   * chosen falls within the date range.
   */
  public static List<String> feeDays(RecProjectRequests.Fee f) {
    List<String> e = new ArrayList<>();
    Set<DayOfWeek> chosen = days(f);
    if (chosen.isEmpty()) {
      e.add("You must specify at least one day of the week");
      return e;
    }
    Set<DayOfWeek> inRange = EnumSet.noneOf(DayOfWeek.class);
    // Legacy enumerates up to 1024 days from the start; a week covers every day anyway.
    for (LocalDate d = f.startDate(); !d.isAfter(f.endDate()) && inRange.size() < 7;
        d = d.plusDays(1)) {
      inRange.add(d.getDayOfWeek());
    }
    List<String> missing = new ArrayList<>();
    for (DayOfWeek day : chosen) {
      if (!inRange.contains(day)) {
        missing.add(dayName(day));
      }
    }
    if (!missing.isEmpty()) {
      e.add("The day(s) of " + String.join(" ", missing)
          + " are invalid for the date range provided");
    }
    return e;
  }

  /** Whether two fees with overlapping dates share a day — legacy's "Overlapping Fees exist". */
  static boolean sharesDay(Set<DayOfWeek> a, Set<DayOfWeek> b) {
    for (DayOfWeek d : a) {
      if (b.contains(d)) {
        return true;
      }
    }
    return false;
  }

  static String dayName(DayOfWeek d) {
    String n = d.name();
    return n.charAt(0) + n.substring(1).toLowerCase();
  }

  /** The value as a number, or null when it is not one. */
  static BigDecimal number(String v) {
    String t = trim(v);
    if (t == null) {
      return null;
    }
    try {
      return new BigDecimal(t);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String required(String label) {
    return label + " is required.";
  }

  private static void length(List<String> e, String v, int max, String label) {
    if (v != null && v.trim().length() > max) {
      e.add(label + " must not exceed " + max + " characters.");
    }
  }

  private static void yesNo(List<String> e, String v, String label) {
    String t = trim(v);
    if (t != null && !"Y".equals(t) && !"N".equals(t)) {
      e.add(label + " can only contain the following value(s): Y, N.");
    }
  }

  /** IntegerFieldValidator + IntegerLimitsValidator (optional field); true if usable. */
  private static boolean integer(List<String> e, String v, String label, long min, long max) {
    String t = trim(v);
    if (t == null) {
      return true;
    }
    long n;
    try {
      n = Long.parseLong(t);
    } catch (NumberFormatException ex) {
      e.add(label + " must be an integer.");
      return false;
    }
    if (n < min || n > max) {
      e.add(label + " field must be between " + min + " and " + max + ".");
      return false;
    }
    return true;
  }
}
