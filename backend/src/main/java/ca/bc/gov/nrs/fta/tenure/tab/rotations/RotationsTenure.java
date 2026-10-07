package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import java.util.ArrayList;
import java.util.List;

/**
 * What the rotation tabs need to know about the tenure itself — the parts of legacy's
 * {@code FTA_GET_FILE_HEADER} and the file-type functions the FTA611/612/613 packages call.
 *
 * @param fileTypeCode       {@code PROV_FOREST_USE.FILE_TYPE_CODE}
 * @param statusCode         {@code PROV_FOREST_USE.FILE_STATUS_ST}
 * @param pfuRevisionCount   {@code PROV_FOREST_USE.REVISION_COUNT} — FTA613's copy guards on it
 * @param termStartYear      the year of {@code TENURE_TERM.LEGAL_EFFECTIVE_DT}; null without a term
 * @param termEndYear        the year of {@code NVL(CURRENT_EXPIRY_DT, INITIAL_EXPIRY_DT)}
 * @param grazingType        {@code FTA_VALID_GRAZING_FILE_TYPE} (in {@code GRAZING_FILE_TYPE_CODE})
 * @param hayType            {@code FTA_VALID_HAY_FILE_TYPE} (in {@code HAY_FILE_TYPE_CODE})
 * @param rangeType          {@code FTA_VALID_RANGE_FILE_TYPE} (in {@code RANGE_FILE_TYPE_CODE})
 * @param applicationPending {@code FTA_APPLICATION_PENDING}: a new FIL/RNG/RP application is
 *                           in the Inbox
 */
public record RotationsTenure(
    String fileTypeCode,
    String statusCode,
    Long pfuRevisionCount,
    Integer termStartYear,
    Integer termEndYear,
    boolean grazingType,
    boolean hayType,
    boolean rangeType,
    boolean applicationPending) {

  /** Whether the tenure has a term (both ends), so it has rotation years. */
  public boolean hasTerm() {
    return termStartYear != null && termEndYear != null;
  }

  /** Whether {@code year} falls within the tenure term, by year, as every legacy check does. */
  public boolean inTerm(int year) {
    return hasTerm() && year >= termStartYear && year <= termEndYear;
  }

  /**
   * The years of the term — legacy's {@code FTA_CODE_LISTS.GET_RANGE_CALENDAR_YEAR}, the Year
   * dropdown of FTA611 and FTA612. Empty without a term.
   */
  public List<Integer> termYears() {
    List<Integer> years = new ArrayList<>();
    if (hasTerm()) {
      for (int y = termStartYear; y <= termEndYear; y++) {
        years.add(y);
      }
    }
    return years;
  }

  /** Whether the file is at status PE (legacy compares the first two characters). */
  public boolean pending() {
    return statusCode != null && statusCode.startsWith("PE");
  }
}
