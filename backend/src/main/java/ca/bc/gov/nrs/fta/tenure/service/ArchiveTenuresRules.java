package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * FTA640's checks that need no database: {@code Fta640ArchivetenForm}'s required
 * fields, expiry year range and expired-code checks, {@code FTA_640_ARCHIVETEN.SAVE}'s
 * refusal to archive a year that has not ended, and {@code CHANGE}'s admin-org check.
 */
final class ArchiveTenuresRules {

  static final String REGION_REQUIRED = "Region Code is required.";
  static final String YEAR_REQUIRED = "Expiry Year is required.";
  static final int MIN_YEAR = 1000;
  static final int MAX_YEAR = 9999;
  static final String YEAR_RANGE = "Expiry Year field must be between 1000 and 9999.";
  static final String NOT_IN_ADMIN_ORG = "District/Region is not within your Admin Org.";
  static final String DISTRICT_NOT_IN_REGION = "District is not within the Region selected.";
  static final String NOT_EXPIRED =
      "System cannot archive as the tenure(s) have not expired yet. "
          + "Doing so will cause problems for billing.";
  static final String NOTHING_TO_ARCHIVE =
      "There are no active grazing or hay tenures to archive for these criteria.";

  private ArchiveTenuresRules() {}

  /** The Go and Archive buttons' required fields. */
  static List<String> validate(ArchiveTenuresRequest q) {
    List<String> e = new ArrayList<>();
    if (q == null || q.regionNo() == null) {
      e.add(REGION_REQUIRED);
    }
    if (q == null || q.expiryYear() == null) {
      e.add(YEAR_REQUIRED);
    } else if (q.expiryYear() < MIN_YEAR || q.expiryYear() > MAX_YEAR) {
      // IntegerLimitsValidator(1000, 9999).
      e.add(YEAR_RANGE);
    }
    return e;
  }

  /** CodeExpiryValidator's message ({@code sil.web.error.usr.code-expired}). */
  static String expired(String label, String code) {
    return label + " (" + code + ") is an expired code.";
  }

  /**
   * {@code CHANGE}'s {@code Sil_Check_User_Org_Authority}: the user must administer every
   * district whose tenures the archive would change. An unscoped administrator administers all.
   */
  static boolean administersAll(Collection<String> districtCodes, Predicate<String> administers) {
    return districtCodes.stream().allMatch(administers);
  }

  /** SAVE: only a year that is over ({@code TO_CHAR(SYSDATE,'YYYY') > P_EXPIRY_YEAR}). */
  static boolean yearIsOver(int expiryYear, int currentYear) {
    return expiryYear < currentYear;
  }
}
