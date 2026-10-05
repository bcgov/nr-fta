package ca.bc.gov.nrs.fta.tenure.tab.assocfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy FTA910's (Associated Files) gate and form checks — the parts that need no database.
 * Computed once and shared by the GET (to enable the Add button and say why not) and the POST
 * (to enforce them).
 *
 * <p>From {@code Fta910AssocFileForm}'s "Save" validators, in their order, with their message
 * texts:
 * <ul>
 *   <li>{@code noSaveWhenStatusPE} — no save while the file is PE (the gate);</li>
 *   <li>{@code assocFileRequired}, {@code file_source_code} — {@code sil.error.usr.required};</li>
 *   <li>{@code assocToSelf} — {@code fta.web.usr.database.record.assoc_to_self};</li>
 *   <li>{@code sourceAssociationType} — a type only with source F;</li>
 *   <li>{@code associationTypeEndDate} — an end date only with type AAC, inside this tenure's
 *       term (legal effective date to current, else initial, expiry date).</li>
 * </ul>
 * Legacy deletes have no gate beyond the role.
 *
 * @param canAdd    whether an association may be added
 * @param addReason why not, when {@code canAdd} is false
 */
public record AssocFilesRules(boolean canAdd, String addReason) {

  /** The FTAS source: the associated file is an FTA forest file. */
  public static final String SOURCE_FTAS = "F";

  /** The one association type that takes an end date. */
  public static final String TYPE_AAC = "AAC";

  /** Legacy's input width for the associated file id. */
  public static final int MAX_ASSOCIATED_FILE_ID = 25;

  /** {@code fta.web.error.user.noSaveWhenStatusPE}. */
  static final String STATUS_PE = "No updates can be performed on this file when the status is PE.";

  /** {@code fta.web.usr.database.record.assoc_to_self}. */
  static final String ASSOC_TO_SELF = "File may not be associated with itself.";

  /** {@code fta.web.error.usr.fta910.sourceAssociation}. */
  static final String SOURCE_ASSOCIATION =
      "Source must be Forest Tenure System for an Association Type to be present.";

  /** {@code fta.web.error.usr.fta910.associationTypeEndDate}. */
  static final String TYPE_END_DATE =
      "Association Type must be AAC for an Association End Date to be present.";

  /** {@code fta.web.error.usr.fta910.associationEndDateRange}. */
  public static final String END_DATE_RANGE =
      "Association End date cannot be earlier than the issue date or later than the expiry date"
          + " of either tenure.";

  /** The gate for a file with this status ({@code PROV_FOREST_USE.FILE_STATUS_ST}). */
  public static AssocFilesRules forStatus(String fileStatusCode) {
    if (fileStatusCode != null && fileStatusCode.trim().toUpperCase().startsWith("PE")) {
      return new AssocFilesRules(false, STATUS_PE);
    }
    return new AssocFilesRules(true, null);
  }

  /**
   * The form's checks (empty when valid). Arguments are already trimmed and upper-cased, blank
   * as null.
   *
   * @param forestFileId this tenure
   * @param awardDate    this tenure's legal effective date, or null
   * @param expiryDate   its current (else initial) expiry date, or null
   */
  public static List<String> validate(
      String forestFileId,
      String associatedFileId,
      String fileSourceCode,
      String typeCode,
      LocalDate endDate,
      LocalDate awardDate,
      LocalDate expiryDate) {
    List<String> e = new ArrayList<>();
    if (associatedFileId == null) {
      e.add("Associated File is mandatory.");
    } else if (associatedFileId.length() > MAX_ASSOCIATED_FILE_ID) {
      e.add("Associated File must not exceed " + MAX_ASSOCIATED_FILE_ID + " characters.");
    }
    if (fileSourceCode == null) {
      e.add("Source is mandatory.");
    }
    if (associatedFileId != null && associatedFileId.equals(forestFileId)) {
      e.add(ASSOC_TO_SELF);
    }
    if (typeCode != null && !SOURCE_FTAS.equals(fileSourceCode)) {
      e.add(SOURCE_ASSOCIATION);
    }
    if (endDate != null) {
      if (!TYPE_AAC.equals(typeCode)) {
        e.add(TYPE_END_DATE);
      } else if ((awardDate != null && endDate.isBefore(awardDate))
          || (expiryDate != null && endDate.isAfter(expiryDate))) {
        e.add(END_DATE_RANGE);
      }
    }
    return e;
  }
}
