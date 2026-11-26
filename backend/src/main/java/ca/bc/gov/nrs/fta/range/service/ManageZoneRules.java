package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FTA631R's checks that need no database: {@code Fta631RangeZoneForm}'s required fields, the
 * maximum lengths of fta631RangeZone.jsp's inputs, and its {@code forceFormats} upper-casing.
 */
final class ManageZoneRules {

  static final int ZONE_MAX = 4;
  static final int DESCRIPTION_MAX = 120;
  static final int PHONE_MAX = 10;
  static final int EMAIL_MAX = 120;
  /** RANGE_ZONE.CONTACT_USER_ID is VARCHAR2(64). */
  static final int CONTACT_USER_ID_MAX = 64;
  /** RANGE_ZONE.CONTACT is VARCHAR2(100). */
  static final int CONTACT_MAX = 100;

  static final String DISTRICT_REQUIRED = "District is mandatory.";
  static final String ZONE_REQUIRED = "Zone is mandatory.";
  static final String CONTACT_REQUIRED = "Contact is mandatory.";
  static final String PHONE_REQUIRED = "Contact phone number is mandatory.";
  static final String EMAIL_REQUIRED = "Contact email is mandatory.";
  static final String DISTRICT_INVALID = "District is not valid.";
  static final String CONTACT_NOT_IDIR = "Contact must be an IDIR user id.";
  static final String NOT_IN_ADMIN_ORG = "The District selected is not within the Admin Org.";

  private ManageZoneRules() {}

  static String tooLong(String label, int max) {
    return label + " cannot be longer than " + max + " characters.";
  }

  static String contactNotFound(String userId) {
    return "Contact " + userId + " was not found in IDIR.";
  }

  /** Save's checks, in the form's order; empty when the zone can be saved. */
  static List<String> validate(ManageZoneAddRequest r) {
    List<String> e = new ArrayList<>();
    if (r == null) {
      return List.of(DISTRICT_REQUIRED, ZONE_REQUIRED, CONTACT_REQUIRED, PHONE_REQUIRED,
          EMAIL_REQUIRED);
    }
    if (blank(r.adminForestDistrictNo())) {
      e.add(DISTRICT_REQUIRED);
    } else if (!r.adminForestDistrictNo().trim().matches("\\d{1,10}")) {
      e.add(DISTRICT_INVALID);
    }
    required(e, r.rangeZoneCode(), ZONE_REQUIRED, "Zone", ZONE_MAX);
    if (!blank(r.zoneDescription()) && r.zoneDescription().trim().length() > DESCRIPTION_MAX) {
      e.add(tooLong("Zone description", DESCRIPTION_MAX));
    }
    if (blank(r.contactUserId())) {
      e.add(CONTACT_REQUIRED);
    } else {
      String id = contactUserId(r.contactUserId());
      if (id == null) {
        e.add(CONTACT_NOT_IDIR);
      } else if (id.length() > CONTACT_USER_ID_MAX) {
        e.add(tooLong("Contact", CONTACT_USER_ID_MAX));
      }
    }
    required(e, r.contactPhoneNumber(), PHONE_REQUIRED, "Contact phone number", PHONE_MAX);
    required(e, r.contactEmailAddress(), EMAIL_REQUIRED, "Contact email", EMAIL_MAX);
    return e;
  }

  private static void required(List<String> e, String v, String missing, String label, int max) {
    if (blank(v)) {
      e.add(missing);
    } else if (v.trim().length() > max) {
      e.add(tooLong(label, max));
    }
  }

  /** forceFormats: the zone code upper-cased. */
  static String zoneCode(String raw) {
    return raw.trim().toUpperCase(Locale.ROOT);
  }

  /**
   * The contact's user id as legacy stored it (WebADE's {@code IDIR\USERNAME}, upper-cased by
   * forceFormats): {@code jsmith} and {@code idir\jsmith} give {@code IDIR\JSMITH}. Null for an id
   * in another directory, or a blank one.
   */
  static String contactUserId(String raw) {
    String bare = bareIdir(raw);
    return bare == null ? null : "IDIR\\" + bare;
  }

  /** The upper-cased IDIR username without its {@code IDIR\} prefix, for the lookup. */
  static String bareIdir(String raw) {
    if (blank(raw)) {
      return null;
    }
    String id = raw.trim();
    int slash = id.indexOf('\\');
    if (slash >= 0) {
      if (!"IDIR".equalsIgnoreCase(id.substring(0, slash).trim())) {
        return null;
      }
      id = id.substring(slash + 1).trim();
    }
    return id.isEmpty() || id.contains("\\") ? null : id.toUpperCase(Locale.ROOT);
  }

  /** The contact's name, as legacy built it from WebADE and forceFormats upper-cased it. */
  static String contactName(String firstName, String lastName) {
    String name = ((firstName == null ? "" : firstName.trim()) + " "
        + (lastName == null ? "" : lastName.trim())).trim().toUpperCase(Locale.ROOT);
    if (name.isEmpty()) {
      return null;
    }
    return name.length() > CONTACT_MAX ? name.substring(0, CONTACT_MAX) : name;
  }

  static String trimToNull(String s) {
    return blank(s) ? null : s.trim();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
