package ca.bc.gov.nrs.fta.tenure.tab.aac;

import java.util.Set;

/**
 * What may be done on a tenure's AAC tab — the gating of legacy FTA930 (Allowable Annual
 * Cut). Computed once from the tenure, returned with the GET and enforced again by every
 * write. Role checks are not here: writes are FTA_ADMIN by the generic rule, and the page
 * disables its buttons for other roles.
 *
 * <p>Legacy gates ({@code FTA_930_AAC.mainline}):
 *
 * <ul>
 *   <li>GET refuses every file type but A01, A41, A02, A03, A04, A44, A10, A25, A26, A27, A28
 *       and A29 ("The File Type (…) associated with the queried File is invalid for this
 *       screen.") and disables both saves.
 *   <li>GET reads the file's {@code TIMBER_TENURE} row and fails without one, so nothing can be
 *       saved for a file that has none.
 *   <li>{@code p_enable_save}: Headquarters may always save (the district / region / BCTS
 *       ownership checks never apply to it). This app has no org levels and every FTA_ADMIN
 *       gets Headquarters' rules, so no ownership gate remains.
 *   <li>SAVE_AAC: Private/Schedule A (area type A) only for A02, A04, A44, A28 and A29.
 * </ul>
 *
 * @param validFileType    whether FTA930 serves this file type at all
 * @param edit             whether AAC history rows may be added, changed and deleted
 * @param editReason       why not, when {@code edit} is false
 * @param areas            whether the Schedule A / B areas may be saved
 * @param areasReason      why not, when {@code areas} is false
 * @param scheduleAAllowed whether area type A (Private/Schedule A) may be used
 */
public record AacRules(
    boolean validFileType,
    boolean edit,
    String editReason,
    boolean areas,
    String areasReason,
    boolean scheduleAAllowed) {

  /** The file types FTA930's GET accepts. */
  public static final Set<String> FILE_TYPES = Set.of(
      "A01", "A41", "A02", "A03", "A04", "A44", "A10", "A25", "A26", "A27", "A28", "A29");

  /** The file types that may carry Private/Schedule A AAC. */
  public static final Set<String> SCHEDULE_A_TYPES = Set.of("A02", "A04", "A44", "A28", "A29");

  /** The PL/SQL message for area type A on any other file type. */
  public static final String SCHEDULE_A_MESSAGE =
      "Private/Schedule A is only permitted for A02,A04,A44,A28, and A29.";

  /**
   * The rules for a file of {@code fileTypeCode}; {@code hasTimberTenure} is whether it has a
   * {@code TIMBER_TENURE} row.
   */
  public static AacRules of(String fileTypeCode, boolean hasTimberTenure) {
    boolean scheduleA = fileTypeCode != null && SCHEDULE_A_TYPES.contains(fileTypeCode);
    if (fileTypeCode == null || !FILE_TYPES.contains(fileTypeCode)) {
      String reason = "The File Type (" + (fileTypeCode == null ? "none" : fileTypeCode)
          + ") associated with the queried File is invalid for this screen.";
      return new AacRules(false, false, reason, false, reason, scheduleA);
    }
    if (!hasTimberTenure) {
      String reason = "This tenure has no timber tenure record, which the AAC screen needs."
          + " Ask for a data fix.";
      return new AacRules(true, false, reason, false, reason, scheduleA);
    }
    return new AacRules(true, true, null, true, null, scheduleA);
  }
}
