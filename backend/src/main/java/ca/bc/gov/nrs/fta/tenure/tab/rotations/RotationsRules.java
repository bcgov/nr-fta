package ca.bc.gov.nrs.fta.tenure.tab.rotations;

/**
 * What may be done on one of the rotation tabs — the gating of legacy FTA611 (Grazing
 * Rotations) and FTA612 (Hay Cutting Rotations).
 * Computed once from the tenure, returned with the tab's GET, and enforced again by every
 * write, so the page and the endpoints cannot disagree. Role checks are not here: writes are
 * FTA_ADMIN by the generic rule, and the page disables its buttons for other roles.
 *
 * <p>Legacy's {@code FTA_CHECK_AUTHORITY_FF_ID} (the user's org unit must administer the file)
 * is an org-level rule; this app has no org levels, so every FTA_ADMIN passes it.
 *
 * @param applies whether the tab applies to the file at all (its file type)
 * @param edit    whether its writes are allowed
 * @param reason  why not, when {@code applies} or {@code edit} is false
 */
public record RotationsRules(boolean applies, boolean edit, String reason) {

  static final String STATUS_PE = "No updates can be performed on this file when the status is PE.";

  static final String INBOX = "You may not update while application is in the Inbox.";

  static final String NO_TERM = "The tenure has no term, so it has no rotation years.";

  /**
   * FTA611. {@code FTA_611_GRAZE_ROTATN.mainline} GET: only a grazing file type
   * ({@code FTA_VALID_GRAZING_FILE_TYPE}); saving is off at status PE or while a new
   * application is in the Inbox ({@code FTA_APPLICATION_PENDING}) — the form's
   * {@code noSaveWhenStatusPE} says the PE half in its own words. Without a term there is no
   * year to save to ({@code save_provision}'s tenure-term check).
   */
  public static RotationsRules grazing(RotationsTenure t) {
    if (!t.grazingType()) {
      return new RotationsRules(false, false,
          "Grazing rotations apply only to grazing licences and permits" + typeSuffix(t) + ".");
    }
    String reason = t.pending()
        ? STATUS_PE
        : t.applicationPending()
            ? INBOX
            : !t.hasTerm() ? NO_TERM : null;
    return new RotationsRules(true, reason == null, reason);
  }

  /**
   * FTA612. {@code FTA_612_HAYCUT_ROTAT.mainline} GET: only a hay file type
   * ({@code FTA_VALID_HAY_FILE_TYPE}); {@code Fta612HaycutRotatAction.handleGet} also turns
   * saving off unless the file type starts with H, and the form refuses a PE file. Unlike
   * FTA611, legacy FTA612 does not check for an application in the Inbox.
   */
  public static RotationsRules hayCutting(RotationsTenure t) {
    if (!t.hayType()) {
      return new RotationsRules(false, false,
          "Hay cutting rotations apply only to hay cutting licences and permits"
              + typeSuffix(t) + ".");
    }
    String reason = t.fileTypeCode() == null || !t.fileTypeCode().startsWith("H")
        ? "Hay cutting rotations can be changed only on a hay cutting tenure (file type H…)."
        : t.pending()
            ? STATUS_PE
            : !t.hasTerm() ? NO_TERM : null;
    return new RotationsRules(true, reason == null, reason);
  }

  private static String typeSuffix(RotationsTenure t) {
    return t.fileTypeCode() == null ? "" : "; this file is type " + t.fileTypeCode();
  }
}
