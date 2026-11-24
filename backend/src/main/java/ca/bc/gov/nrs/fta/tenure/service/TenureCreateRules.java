package ca.bc.gov.nrs.fta.tenure.service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The file-type rules of legacy's new-tenure Save that need no database: FTA100's field rules
 * ({@code Fta100TenureForm}, {@code Fta100TenureAction}) and the decisions
 * {@code FTA_100_TENURE.ADD_NEW_REC} makes before it writes. {@link TenureWriteService} applies
 * them and writes the rows.
 */
final class TenureCreateRules {

  private TenureCreateRules() {}

  /** The file types whose id the user enters (ADD_NEW_REC generates every other one). */
  static final Set<String> ENTERED_ID_TYPES = Set.of("A02", "A06", "A30", "B40");

  /** The licence to cut types: ADD_NEW_REC writes their harvesting authority. */
  private static final Set<String> LICENCE_TO_CUT_TYPES = Set.of("B05", "B06", "B07");

  /** Legacy's maximum lengths (the Details tab's inputs). */
  static final int MAX_ZONE = 4;
  static final int MAX_LOCATION = 50;

  private static final Pattern A06_ID = Pattern.compile("T\\d{4}");
  private static final Pattern A02_ID = Pattern.compile("TFL\\d{2}");

  /** The user types the id: A02, A06, A30 and B40. */
  static boolean entersId(String type) {
    return ENTERED_ID_TYPES.contains(type);
  }

  /** B10: the user may type an F id, or leave it blank for the next one. */
  static boolean optionalId(String type) {
    return "B10".equals(type);
  }

  /** FTA100 shows Admin Org for file types starting U, P or A. */
  static boolean showsAdminOrg(String type) {
    return type.startsWith("U") || type.startsWith("P") || type.startsWith("A");
  }

  /** FTA100 hides District for file types starting A, and P01. */
  static boolean showsDistrict(String type) {
    return !type.startsWith("A") && !"P01".equals(type);
  }

  /**
   * adminOrgDistrictForNew: A04, A44 and A11 files need a district as Admin Org, every other one
   * a region. A18 is excused only for a non-Headquarters user, so here it needs a region too.
   */
  static String requiredAdminOrgLevel(String type) {
    return Set.of("A04", "A44", "A11").contains(type) ? "D" : "R";
  }

  /** GET_DEFAULTS: the management unit type a new file of this type starts with. */
  static String defaultMgmtUnitType(String type) {
    return switch (type) {
      case "A04", "A44" -> "F";
      case "A02" -> "T";
      case "A28" -> "C";
      case "A29" -> "M";
      default -> null;
    };
  }

  static boolean licenceToCut(String type) {
    return LICENCE_TO_CUT_TYPES.contains(type);
  }

  /** GET_DEFAULTS' p_show_zone_ind: B05, B06, B07 and B10 show (and need) a Zone. */
  static boolean needsZone(String type) {
    return licenceToCut(type) || "B10".equals(type);
  }

  /** locationRequired: a B05 needs a Geographic Location. */
  static boolean needsLocation(String type) {
    return "B05".equals(type);
  }

  /** quotaTypeRequired: B05 and B06 need a Quota Type (B07 may have one). */
  static boolean needsQuotaType(String type) {
    return "B05".equals(type) || "B06".equals(type);
  }

  /** The Purpose list FTA100 offers: forestry licences to cut for B07, occupant for B05/B06. */
  static String purposeList(String type) {
    return "B07".equals(type) ? "forLicenceToCut" : "occLicenceToCut";
  }

  /**
   * The status a new file starts in. FTA100 defaults every new file to PP; ADD_NEW_REC accepts
   * that only for A and U files (PP or PL), licences to cut and recreation files, and wants PA
   * for every other one.
   */
  static String initialStatus(String type, boolean recreation) {
    boolean ppAllowed = type.startsWith("A") || type.startsWith("U")
        || licenceToCut(type) || recreation;
    return ppAllowed ? "PP" : "PA";
  }

  /**
   * ADD_NEW_REC's admin org: the District for U, B, S, E, H and C files, the Admin Org for every
   * other one. Where FTA100 hides Admin Org, it holds the user's own org: Headquarters.
   */
  static String storedAdminOrg(String type, String adminOrg, String district, String hqOrg) {
    if ("UBSEHC".indexOf(type.charAt(0)) >= 0) {
      return district;
    }
    return showsAdminOrg(type) ? adminOrg : hqOrg;
  }

  /** The new file's TENURE_TERM row: A and U files, and B08. */
  static boolean createsTenureTerm(String type) {
    return type.startsWith("A") || type.startsWith("U") || "B08".equals(type);
  }

  /**
   * A licence to cut's timber mark: the file id for B05 and B06 (and a cash sale), the file id
   * without its first character for any other B07. New files are never cash sales here.
   */
  static String timberMark(String type, String forestFileId) {
    return "B07".equals(type) ? forestFileId.substring(1) : forestFileId;
  }

  /** A salvage-sale (SS) licence to cut gets salvage type SSS. */
  static String salvageType(String purpose) {
    return "SS".equals(purpose) ? "SSS" : null;
  }

  /**
   * Whether a licence to cut gets its hauling authority: not a B05 or B06 for DT, nor a
   * BCTS-funded B07 for DT (never BCTS-funded here).
   */
  static boolean createsHaulingAuthority(String type, String purpose) {
    return !("DT".equals(purpose) && ("B05".equals(type) || "B06".equals(type)));
  }

  /**
   * An A02's designate. FTA100 made the user type it, and accepted only the file id's digits
   * followed by a slash (designateNNslash), so it is derived.
   */
  static String a02Designate(String forestFileId) {
    String digits = forestFileId.length() == 4 ? forestFileId.substring(3, 4)
        : forestFileId.length() == 5 ? forestFileId.substring(3, 5) : "";
    return digits + "/";
  }

  /** A06 and A02 id formats; null when fine. */
  static String fileIdFormatError(String type, String id) {
    if ("A06".equals(type) && !A06_ID.matcher(id).matches()) {
      return "File must start with letter T and be followed by 4 numbers.";
    }
    if ("A02".equals(type) && !A02_ID.matcher(id).matches()) {
      return "File must start with TFL and be followed by 2 numbers for file type A02.";
    }
    return null;
  }

  /** a30FileFormat's shape check: T, two letters of a district, two numbers. */
  static String a30FormatError(String id) {
    if (!id.startsWith("T")) {
      return "File must begin with T.";
    }
    if (id.length() != 5 || !id.substring(3).chars().allMatch(Character::isDigit)) {
      return "File must be in the format TAANN where AA is the last 2 characters of a district"
          + " within the Admin Org and NN are 2 numbers.";
    }
    return null;
  }

  /** The district an A30 id names: D plus its 2nd and 3rd characters. */
  static String a30District(String id) {
    return "D" + id.substring(1, 3);
  }

  /** tMgmtTypeForFileTypeA02: an A02 in a TFL is that TFL. */
  static boolean a02MgmtUnitMatches(String forestFileId, String mgmtUnitType, String mgmtUnitId) {
    if (!"T".equals(mgmtUnitType)) {
      return true;
    }
    String last2 = forestFileId.length() >= 2
        ? forestFileId.substring(forestFileId.length() - 2) : forestFileId;
    return last2.equals(mgmtUnitId);
  }

  /** Initial Area (ha): 0 to 9,999,999.9, one decimal; null when fine. */
  static String initialAreaError(String value) {
    try {
      BigDecimal v = new BigDecimal(value);
      if (v.signum() < 0 || v.compareTo(new BigDecimal("9999999.9")) > 0 || v.scale() > 1) {
        return "Initial Area(ha) must be from 0 to 9999999.9 with at most 1 decimal place.";
      }
      return null;
    } catch (NumberFormatException e) {
      return "Initial Area(ha) must be a number.";
    }
  }

  /** HARVEST_SALE's codes for a new file (create_harvest_sale). */
  record HarvestSale(String saleMethod, String saleType, String salvageInd, String plannedCategory) {}

  static HarvestSale harvestSale(String type, String purpose) {
    String salvage = "A31".equals(type) ? "Y" : "N";
    // A18, A01 and A41 take the Salvage answer, which FTA100 defaults to N.

    String method;
    if (Set.of("A03", "A20", "A23", "A24", "A26", "A27", "B20", "B23").contains(type)) {
      method = "T";
    } else if (Set.of("A01", "A02", "A04").contains(type)) {
      method = null;
    } else if ("A29".equals(type)) {
      method = "N";
    } else {
      method = "D";
    }

    String saleType = null;
    if (Set.of("A03", "A11", "A41", "A44", "B05", "B06").contains(type)) {
      saleType = "SL";
    } else if ("B02".equals(type)) {
      saleType = "SP";
    } else if ("B07".equals(type) && "SS".equals(purpose)) {
      salvage = "Y";
    }

    String category = null;
    if (Set.of("A03", "A11", "A26", "A27", "A28", "A29", "A41", "A44", "B05", "B06")
        .contains(type)) {
      category = "N";
    } else if (Set.of("A20", "A21", "A22", "B20", "B21", "B22").contains(type)) {
      category = "A";
    } else if (Set.of("A23", "A24", "B23").contains(type)) {
      category = "2";
    }
    return new HarvestSale(method, saleType, salvage, category);
  }

  /** ADD_NEW_REC gives a designate to new woodlots and timber licences in PP or PL. */
  static boolean assignsWoodlotOrTlDesignate(String type, String status) {
    return ("PP".equals(status) || "PL".equals(status))
        && Set.of("A04", "A44", "A06", "A28", "A29").contains(type);
  }

  /**
   * create_mark_designate's next designate after {@code current}: the last three letters count
   * up A–Z (prefix W for woodlots, T for A06), skipping any designate holding I or O. Null when
   * the counter has reached its maximum.
   */
  static String nextLetterDesignate(String prefix, String current) {
    String d = current;
    do {
      if ((prefix + "ZZZ").equals(d)) {
        return null;
      }
      char[] c = d.toCharArray();
      if (c.length != 4) {
        throw new IllegalStateException("Mark designate in control table is corrupt.");
      }
      if (c[3] == 'Z') {
        c[3] = 'A';
        if (c[2] == 'Z') {
          c[2] = 'A';
          if (c[1] == 'Z') {
            return null;
          }
          c[1]++;
        } else {
          c[2]++;
        }
      } else {
        c[3]++;
      }
      d = prefix + c[1] + c[2] + c[3];
    } while (d.indexOf('I') >= 0 || d.indexOf('O') >= 0);
    return d;
  }
}
