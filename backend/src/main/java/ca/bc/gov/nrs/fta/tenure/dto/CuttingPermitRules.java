package ca.bc.gov.nrs.fta.tenure.dto;

import java.time.LocalDate;

/**
 * What FTA902 allows on a permit — computed once and served both to the screen (which controls
 * are enabled) and to the writes (which refuse what it refuses).
 *
 * <p>Legacy's {@code SET_AUTHORITY_IND} also asks whether the user's org unit owns the file and
 * whether its org level may update the file type ({@code FTA_FILE_LEVEL_AUTHORITY}). This app
 * has no org levels and harvest roles act in any district, so only the role and status parts of
 * that procedure apply.
 *
 * @param canSave               Save; also status, which legacy locks exactly when Save is off
 * @param termsLocked           issue date, term, expiry and extension count are read-only
 * @param canEditExtend         extended date and reason
 * @param canEditArea           no spatial yet and still PP/PL/PI
 * @param canEditPurpose        the licence-to-cut purpose, an A18's only
 * @param canEditMarking        compliance method and instrument; an FSJ permit has several marks
 * @param canDelete             Delete (an EE permit, by a senior user)
 * @param canRetire             Retire (an HC/HRS permit with no application in progress)
 * @param canManageConditions   add and delete permit issuance conditions
 * @param canPostpone           add a postponement
 * @param canChangePostponements update and delete a postponement
 * @param canRescindAnytime     rescind outside a postponement's term (legacy's Super User)
 * @param canAssignMarks        legacy offers Assign Marks to Blocks on FSJ permits only
 * @param canSuspendBlocks      Cut Block Suspensions
 */
public record CuttingPermitRules(
    boolean canSave,
    boolean termsLocked,
    boolean canEditExtend,
    boolean canEditArea,
    boolean canEditPurpose,
    boolean canEditMarking,
    boolean canDelete,
    boolean canRetire,
    boolean canManageConditions,
    boolean canPostpone,
    boolean canChangePostponements,
    boolean canRescindAnytime,
    boolean canAssignMarks,
    boolean canSuspendBlocks) {

  /** Before this legacy refuses a postponement ({@code awardEarlierThanHPAllowedDt}). */
  static final LocalDate POSTPONE_RULE_START = LocalDate.of(2003, 11, 4);

  public static final String MSG_ROLE_HP =
      "Status cannot be changed from %s to %s without Forest Senior Admin Role or BCTS Role.";
  public static final String MSG_HP_FROM =
      "A Cutting Permit status can only be changed to HP if it is currently in HI status or has"
          + " not been previously deferred.";
  public static final String MSG_HP_TO =
      "A Cutting Permit status can only be changed to HI if it is currently in a status of HP.";
  public static final String MSG_HP_ISSUE =
      "Harvesting Postponed can only be applied to Cutting Permit applications issued on or"
          + " after Nov 4, 2003.";
  public static final String MSG_HP_RESCINDED =
      "A Cutting Permit status can only be changed to HP if a previous postponement has not been"
          + " rescinded.";
  public static final String MSG_HP_NOT_EXPIRED =
      "The Cutting Permit can not be placed into the HI status until the postponement period has"
          + " expired.";
  public static final String MSG_HP_NOT_CREATED =
      "You must use the 'Add Postponement' button to create a new postponement.";
  public static final String MSG_RESCIND_TERM =
      "A postponement can only be rescinded during its term.";

  /** What the rules read: the permit, the caller, and the data only an editor's rules need. */
  public record Facts(
      String fileTypeCode,
      String statusCode,
      boolean fsj,
      LocalDate issueDate,
      LocalDate retirementDate,
      boolean applicationInProgress,
      boolean spatialExists,
      boolean latestPostponementRescinded,
      boolean editor,
      boolean senior,
      boolean admin) {}

  /** The rules for a permit. A reader without {@code editor} gets every control off. */
  public static CuttingPermitRules of(Facts f) {
    String status = f.statusCode() == null ? "" : f.statusCode();
    // Oil and gas (A11) stays read-only; legacy refuses non-A files on this screen outright.
    boolean timberTenure = f.fileTypeCode() != null
        && f.fileTypeCode().startsWith("A")
        && !"A11".equals(f.fileTypeCode());
    boolean editable = f.editor() && timberTenure;
    boolean canSave = editable && !"EE".equals(status) && !"PE".equals(status);
    boolean managing = editable && f.senior();
    return new CuttingPermitRules(
        canSave,
        termsLocked(status, f.senior()),
        canSave && status.startsWith("H") && !"HP".equals(status),
        canSave && !f.spatialExists() && ("PP".equals(status) || "PL".equals(status)
            || "PI".equals(status)),
        canSave && "A18".equals(f.fileTypeCode()),
        canSave && !f.fsj(),
        managing && "EE".equals(status),
        canSave && ("HC".equals(status) || "HRS".equals(status))
            && f.retirementDate() == null && !f.applicationInProgress(),
        canSave && f.senior(),
        canSave && f.senior() && postponeProblem(
            status, f.issueDate(), f.latestPostponementRescinded()) == null,
        managing && ("HP".equals(status) || f.admin()),
        managing && f.admin(),
        canSave && f.fsj(),
        managing);
  }

  /** {@code setProtectedAndHideParams}: issued terms belong to senior users; HP's to no one. */
  static boolean termsLocked(String status, boolean senior) {
    return "HP".equals(status) || (status.startsWith("H") && !"HN".equals(status) && !senior);
  }

  /** {@code hpStatusCheck} for a new postponement: null when one may be added, else why not. */
  public static String postponeProblem(String status, LocalDate issueDate, boolean rescinded) {
    if (!"HP".equals(status) && !"HI".equals(status)) {
      return MSG_HP_FROM;
    }
    if (issueDate == null || issueDate.isBefore(POSTPONE_RULE_START)) {
      return MSG_HP_ISSUE;
    }
    return rescinded ? MSG_HP_RESCINDED : null;
  }

  /**
   * {@code hpStatusCheck} for a status change on Save: null when allowed. Only a change into or
   * out of HP is its concern; {@code Fta_Edit_Status_Change} judges the rest. Saving a status of
   * HP directly would leave a postponed permit with no postponement, so it is refused, as
   * legacy's {@code ppNotCreated} message intends.
   */
  public static String statusChangeProblem(
      String previous, String next, boolean senior, LocalDate latestExpiry, LocalDate today) {
    if (next.equals(previous) || !("HP".equals(next) || "HP".equals(previous))) {
      return null;
    }
    if (!senior) {
      return MSG_ROLE_HP.formatted(previous, next);
    }
    if ("HP".equals(next)) {
      return MSG_HP_NOT_CREATED;
    }
    if (!"HI".equals(next)) {
      return MSG_HP_TO;
    }
    return latestExpiry != null && !latestExpiry.isBefore(today) ? MSG_HP_NOT_EXPIRED : null;
  }

  /** {@code getEnableRescind}: within [start, expiry), unless the caller may always rescind. */
  public static String rescindProblem(
      boolean anytime, LocalDate start, LocalDate expiry, LocalDate today) {
    if (anytime) {
      return null;
    }
    boolean within = start != null && expiry != null
        && !today.isBefore(start) && today.isBefore(expiry);
    return within ? null : MSG_RESCIND_TERM;
  }
}
