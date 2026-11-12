package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * FTA640's checks that need no database: {@code Fta640ArchivetenForm}'s required
 * fields, and {@code FTA_640_ARCHIVETEN.SAVE}'s refusal to archive a year that
 * has not ended.
 */
final class ArchiveTenuresRules {

  static final String REGION_REQUIRED = "Region Code is required.";
  static final String YEAR_REQUIRED = "Expiry Year is required.";
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
    }
    return e;
  }

  /** SAVE: only a year that is over ({@code TO_CHAR(SYSDATE,'YYYY') > P_EXPIRY_YEAR}). */
  static boolean yearIsOver(int expiryYear, int currentYear) {
    return expiryYear < currentYear;
  }
}
