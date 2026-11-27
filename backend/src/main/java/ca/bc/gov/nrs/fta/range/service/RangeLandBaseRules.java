package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.SaveRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The checks legacy {@code Fta615RangeLandBaseForm} ran before FTA615R's Save and Remove, with
 * its messages, in its order; and its upper-casing of the PID and ID on Save.
 *
 * <p>Legacy required the Legal Description only with a PID, and the Land Purpose not at all (its
 * required check was disabled at the users' request).
 */
public final class RangeLandBaseRules {

  static final BigDecimal MAX_AREA = new BigDecimal("999999.9");
  static final int MAX_DESCRIPTION = 255;
  static final int MAX_PID = 9;
  static final int MAX_ID = 50;
  static final int MAX_COMMENT = 4000;

  static final String DESCRIPTION_WITH_PID =
      "If Land Base PID is provided, Legal Description must be provided.";
  static final String PID_INTEGER = "Land Base PID must be an integer.";
  static final String PID_LENGTH = "Land Base PID must have 9 characters.";
  static final String OWNERSHIP_REQUIRED = "Land Ownership Type is mandatory.";
  static final String LEASE_TOGETHER =
      "Lease start and end dates must both be entered or both left blank.";
  static final String LEASE_ORDER =
      "Lease Start Date must be less than or equal to Lease End Date.";
  static final String REMOVE_COMMENT_REQUIRED = "A comment is required to remove the land record.";

  static final String SAVE_SUCCESS = "Save successful.";
  static final String REMOVE_SUCCESS = "Land Base record has been de-activated.";

  /** The values a save writes, parsed and normalized. */
  public record Parsed(
      String description,
      String pid,
      String landBaseId,
      String ownershipTypeCode,
      String purposeCode,
      BigDecimal fencedPasture,
      BigDecimal unfencedPasture,
      BigDecimal forageProduction,
      String comment,
      LocalDate leaseStartDate,
      LocalDate leaseEndDate) {}

  /** Current codes, and the codes the record already holds (which it may keep). */
  public record Codes(
      Set<String> ownershipTypes,
      Set<String> purposes,
      String storedOwnershipType,
      String storedPurpose) {}

  private RangeLandBaseRules() {}

  /** Legacy's "Save" validators. */
  public static List<String> validateSave(SaveRequest r, Codes codes) {
    List<String> e = new ArrayList<>();
    String pid = trimToNull(r.rangeLandBasePid());
    String description = trimToNull(r.description());
    if (pid != null && description == null) {
      e.add(DESCRIPTION_WITH_PID);
    }
    if (pid != null && !pid.chars().allMatch(Character::isDigit)) {
      e.add(PID_INTEGER);
    }
    if (pid != null && pid.length() != MAX_PID) {
      e.add(PID_LENGTH);
    }
    if (trimToNull(r.rangeLandOwnershipTypeCode()) == null) {
      e.add(OWNERSHIP_REQUIRED);
    }
    area(e, r.fencedPasture(), "Fenced pasture");
    area(e, r.unfencedPasture(), "Unfenced pasture");
    area(e, r.forageProduction(), "Hay field");
    expired(e, "Ownership Type", r.rangeLandOwnershipTypeCode(), codes.ownershipTypes(),
        codes.storedOwnershipType());
    expired(e, "Land Purpose", r.rangeLandPurposeCode(), codes.purposes(),
        codes.storedPurpose());
    String start = trimToNull(r.leaseStartDate());
    String end = trimToNull(r.leaseEndDate());
    if ((start == null) != (end == null)) {
      e.add(LEASE_TOGETHER);
    } else if (start != null) {
      LocalDate s = date(e, start, "Lease Start Date");
      LocalDate f = date(e, end, "Lease End Date");
      if (s != null && f != null && s.isAfter(f)) {
        e.add(LEASE_ORDER);
      }
    }
    length(e, description, MAX_DESCRIPTION, "Legal Description");
    length(e, trimToNull(r.rangeLandBaseId()), MAX_ID, "ID #");
    length(e, trimToNull(r.landBaseComment()), MAX_COMMENT, "Comment");
    return e;
  }

  /** Legacy's "RemoveLand" validator. */
  public static List<String> validateRemove(String comment) {
    return trimToNull(comment) == null ? List.of(REMOVE_COMMENT_REQUIRED) : List.of();
  }

  /** The request as saved, once {@link #validateSave} has passed. */
  public static Parsed parse(SaveRequest r) {
    return new Parsed(
        trimToNull(r.description()),
        upper(r.rangeLandBasePid()),
        upper(r.rangeLandBaseId()),
        trimToNull(r.rangeLandOwnershipTypeCode()),
        trimToNull(r.rangeLandPurposeCode()),
        decimal(r.fencedPasture()),
        decimal(r.unfencedPasture()),
        decimal(r.forageProduction()),
        trimToNull(r.landBaseComment()),
        trimToNull(r.leaseStartDate()) == null ? null : LocalDate.parse(r.leaseStartDate().trim()),
        trimToNull(r.leaseEndDate()) == null ? null : LocalDate.parse(r.leaseEndDate().trim()));
  }

  /** FloatRangeValidator 0..999999.9, then DecimalPlacesValidator 1. */
  private static void area(List<String> e, String value, String label) {
    String v = trimToNull(value);
    if (v == null) {
      return;
    }
    BigDecimal n = decimal(v);
    if (n == null) {
      e.add(label + " must be numeric.");
      return;
    }
    if (n.signum() < 0 || n.compareTo(MAX_AREA) > 0) {
      e.add(label + " field must be between 0 and 999999.9.");
    }
    if (n.stripTrailingZeros().scale() > 1) {
      e.add("Only one decimal-place is permitted for " + label);
    }
  }

  /** CodeExpiryValidator: a code must be current, unless the record already holds it. */
  private static void expired(List<String> e, String label, String code, Set<String> current,
      String stored) {
    String c = trimToNull(code);
    if (c == null || Objects.equals(c, stored) || current.contains(c)) {
      return;
    }
    e.add(label + " (" + c + ") is an expired code.");
  }

  private static LocalDate date(List<String> e, String value, String label) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ex) {
      e.add(label + " must be a date (yyyy-mm-dd).");
      return null;
    }
  }

  /** The screen's maxlength. */
  private static void length(List<String> e, String value, int max, String label) {
    if (value != null && value.length() > max) {
      e.add(label + " must be at most " + max + " characters.");
    }
  }

  private static BigDecimal decimal(String s) {
    String v = trimToNull(s);
    if (v == null) {
      return null;
    }
    try {
      return new BigDecimal(v);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String upper(String s) {
    String v = trimToNull(s);
    return v == null ? null : v.toUpperCase(Locale.ROOT);
  }

  static String trimToNull(String s) {
    return s == null || s.trim().isEmpty() ? null : s.trim();
  }
}
