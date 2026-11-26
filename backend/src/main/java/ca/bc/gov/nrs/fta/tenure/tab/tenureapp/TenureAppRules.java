package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Legacy FTA950's gate and checks for Issue Permit, shared by the list (to enable the action)
 * and the write (to enforce it). The popup's checks are Fta950xTenAppIssuePermitForm's, with
 * ApplicationResources.properties' texts.
 */
public final class TenureAppRules {

  /** FTA_950X_TEN_APP.GET's disable_issue_permit_ind: enabled for APP and ISS (since 2023). */
  private static final Set<String> ISSUABLE_STATES = Set.of("APP", "ISS");

  /** CP_EXTERNAL_DOCUMENT_SDW.CUTTING_PERMIT_DOCUMENT_URI: VARCHAR2(2000 BYTE). */
  static final int MAX_URI = 2000;

  /** web.xml max-upload-file-size: 4,194,304 bytes; a file this size or larger is refused. */
  static final long MAX_UPLOAD_BYTES = 4_194_304L;

  /** The popup's e-mail fields: maxlength 50 on Email. */
  static final int MAX_EMAIL = 50;

  /** Fta950xTenAppIssuePermitForm.EMAIL_PATTERN. */
  private static final Pattern EMAIL = Pattern.compile(
      "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$");

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
   * Add Email's check (legacy emailValidator): the first failure's message, or null.
   */
  public static String validateEmail(String email, String reconfirmEmail) {
    String e = email == null ? "" : email;
    String r = reconfirmEmail == null ? "" : reconfirmEmail;
    if (e.isEmpty()) {
      return "Email is mandatory.";
    }
    if (!EMAIL.matcher(e).matches()) {
      return "Email has an invalid email format.";
    }
    if (r.isEmpty()) {
      return "Reconfirm Email is mandatory.";
    }
    if (!EMAIL.matcher(r).matches()) {
      return "Reconfirm Email has an invalid email format.";
    }
    if (!e.equals(r)) {
      return "The Email must equal Reconfirm Email.";
    }
    return null;
  }

  /**
   * Upload and Issue's checks (legacy's Upload chain: document name, file name, empty file, file
   * size, recipients — the first failure only), plus each recipient's format, which legacy
   * checked as it was added.
   *
   * @return the message, or null when the request may proceed
   */
  public static String validateIssue(
      String documentName, String fileName, long fileSize, List<String> recipients) {
    if (documentName == null || documentName.isEmpty()) {
      return "Document Name is mandatory.";
    }
    if (fileName == null || fileName.isBlank()) {
      return "File Name is mandatory.";
    }
    if (fileSize == 0) {
      return "The uploaded file was empty.";
    }
    if (fileSize >= MAX_UPLOAD_BYTES) {
      return "Uploaded files cannot be larger than 4.0Mb.";
    }
    if (recipients == null || recipients.isEmpty()) {
      return "Recipients is mandatory.";
    }
    for (String r : recipients) {
      if (r == null || !EMAIL.matcher(r).matches()) {
        return "Email has an invalid email format.";
      }
    }
    return null;
  }

  /** Whether the stored document's link fits the permit document column. */
  static boolean linkFits(String link) {
    return link != null && link.getBytes(StandardCharsets.UTF_8).length <= MAX_URI;
  }
}
