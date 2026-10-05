package ca.bc.gov.nrs.fta.tenure.tab.amendments;

import java.util.Set;

/**
 * Whether the tenure may be shown on legacy FTA905 (CP / Cut Block Amendments).
 *
 * <p>Ported from {@code FTA_905_CP_AMEND.MAINLINE} / {@code FTA_905_BLK_AMEND.MAINLINE}: a file
 * of type B40, B02, B01, S01, S02 or C01, or of a range or recreation file type, is rejected
 * with {@code fta.web.error.invalid.fileType} — "The File Type ({0}) associated with the
 * queried File is invalid for this screen." The screen is read-only; it has no writes.
 *
 * @param available          whether the file's amendments can be listed
 * @param unavailableReason  legacy's message when not
 */
public record AmendmentsRules(boolean available, String unavailableReason) {

  /** The file types FTA905 rejects outright. */
  static final Set<String> INVALID_FILE_TYPES = Set.of("B40", "B02", "B01", "S01", "S02", "C01");

  /**
   * @param fileTypeCode  the file's {@code FILE_TYPE_CODE}
   * @param rangeType     whether it is a {@code RANGE_FILE_TYPE_CODE}
   * @param recreationType whether it is a {@code RECREATION_FILE_TYPE_CODE}
   */
  public static AmendmentsRules of(String fileTypeCode, boolean rangeType, boolean recreationType) {
    String code = fileTypeCode == null ? "" : fileTypeCode.trim();
    if (INVALID_FILE_TYPES.contains(code) || rangeType || recreationType) {
      return new AmendmentsRules(false,
          "The File Type (" + code + ") associated with the queried File is invalid for this"
              + " screen.");
    }
    return new AmendmentsRules(true, null);
  }
}
