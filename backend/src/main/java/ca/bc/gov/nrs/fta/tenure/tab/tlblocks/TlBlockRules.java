package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

/**
 * What may be done on a tenure's TL blocks tab — the gating of legacy FTA980 (TL Block
 * Summary). Computed once from the tenure and returned with the list, and enforced again by
 * every write, so the page and the endpoints cannot disagree. Role checks are not here: writes
 * are FTA_ADMIN by the generic rule, and the page disables its buttons for other roles.
 *
 * <p>Legacy gates:
 *
 * <ul>
 *   <li>The screen is for Timber Licences only: {@code Fta980TlblockAction.handleGet} shows
 *       nothing for any file type but A06 ("The File Type (…) associated with the queried File
 *       is invalid for this screen.").
 *   <li>Retire / Un-Retire ({@code FTA_980_TLBLOCK.mainline}'s {@code p_enable_retire}): only
 *       once the tenure is issued (status H…), and only for an org level with FILE authority on
 *       A06 in the file's own region. This app has no org levels; every FTA_ADMIN gets
 *       Headquarters' rules, so only the status gate remains.
 *   <li>Legacy's screen offers nothing else: its package's {@code SAVE} and {@code REMOVE} have
 *       no button, so blocks are not added, changed or deleted.
 * </ul>
 *
 * @param timberLicence whether the file is a Timber Licence (A06), so the tab applies at all
 * @param retire        whether blocks may be retired and un-retired
 * @param retireReason  why not, when {@code retire} is false
 */
public record TlBlockRules(
    boolean timberLicence,
    boolean retire,
    String retireReason) {

  /** The only file type legacy FTA980 serves. */
  public static final String TIMBER_LICENCE = "A06";

  /** The rules for a file of {@code fileTypeCode} at status {@code statusCode}. */
  public static TlBlockRules of(String fileTypeCode, String statusCode) {
    if (!TIMBER_LICENCE.equals(fileTypeCode)) {
      String reason = notTimberLicence(fileTypeCode);
      return new TlBlockRules(false, false, reason);
    }
    boolean issued = statusCode != null && statusCode.startsWith("H");
    return new TlBlockRules(
        true,
        issued,
        issued ? null : "TL blocks can be retired only once the tenure is issued (status H…).");
  }

  /** Why the tab does not apply to a file of this type. */
  public static String notTimberLicence(String fileTypeCode) {
    return "TL blocks apply only to Timber Licences (file type A06)"
        + (fileTypeCode == null ? "." : "; this file is type " + fileTypeCode + ".");
  }
}
