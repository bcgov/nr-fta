package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Legacy FTA950's gate and checks for Issue Permit, shared by the list (to enable the action)
 * and the write (to enforce it).
 */
public final class TenureAppRules {

  /** FTA_950X_TEN_APP.GET's disable_issue_permit_ind: enabled for APP and ISS (since 2023). */
  private static final Set<String> ISSUABLE_STATES = Set.of("APP", "ISS");

  /** Generous cap on the recorded link (CP_EXTERNAL_DOCUMENT_SDW.CUTTING_PERMIT_DOCUMENT_URI). */
  static final int MAX_URI = 2000;

  private TenureAppRules() {}

  /** Whether Issue Permit is allowed for an application in this state. */
  public static boolean canIssue(String stateCode) {
    return stateCode != null && ISSUABLE_STATES.contains(stateCode.trim());
  }

  /** Why not, or null when it is allowed. */
  public static String issueBlockedReason(String stateCode) {
    return canIssue(stateCode)
        ? null
        : "A permit can be issued only for an application that is approved or issued.";
  }

  /**
   * The Issue Permit dialog's own checks (legacy's popup: "A PDF document must be selected and
   * a document name given before you can proceed."), on the permit document's link.
   */
  public static List<String> validateIssue(String documentUri) {
    List<String> e = new ArrayList<>();
    String uri = documentUri == null ? "" : documentUri.trim();
    if (uri.isEmpty()) {
      e.add("Permit document is mandatory.");
    } else if (uri.length() > MAX_URI) {
      e.add("Permit document link must not exceed " + MAX_URI + " characters.");
    }
    return e;
  }
}
