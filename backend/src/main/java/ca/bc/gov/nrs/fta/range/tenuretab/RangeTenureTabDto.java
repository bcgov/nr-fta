package ca.bc.gov.nrs.fta.range.tenuretab;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/fta/range-tenures/{id}/tenure-tab} — FTA100R's form for the file, as the
 * package's GET returned it, with what the user may do on it.
 *
 * @param values         the screen's values by API key (see {@link RangeTenureRules#FIELDS}),
 *                       plus {@code adminOrgUnitNo}, {@code mgmtUnitName},
 *                       {@code replacementCount}, {@code replacementLabel},
 *                       {@code prevFileStatusSt} and {@code prevReplacementTermYears}; years are
 *                       four-digit strings, blank when unset
 * @param revisions      the revision counts the save is locked on, sent back unchanged
 * @param descriptions   "CODE - description" for the status and type; the zone's description
 *                       ({@code zoneDescription}) and contact ({@code zoneContact})
 * @param editable       whether the user may save this file at all (canEdit)
 * @param editableFields the API keys the user may change, when editable
 * @param replaceable    an E01/H01: the Historical and Replacement sections apply
 * @param showTenureTerm SET_SHOW_SECTION_IND: false on a PA file, whose term is not checked
 * @param retirable      Retire is enabled (SET_RETIRED_IND, and editable)
 * @param cancelReplacement Cancel Replacement is enabled
 * @param statuses       the Status dropdown: current range tenure statuses
 * @param fileTypes      the Type dropdown: current range types of the file's family
 * @param zones          the Zone dropdown: the district's range zones
 * @param conditionCodes the special condition codes, with each one's title and text
 */
public record RangeTenureTabDto(
    String forestFileId,
    Map<String, String> values,
    Map<String, String> revisions,
    Map<String, String> descriptions,
    boolean editable,
    List<String> editableFields,
    boolean replaceable,
    boolean showTenureTerm,
    boolean retirable,
    boolean cancelReplacement,
    List<CodeOptionDto> statuses,
    List<CodeOptionDto> fileTypes,
    List<Zone> zones,
    List<ConditionCode> conditionCodes) {

  /** A range zone of a district: FTA631's code, description and contact (GetZoneContact). */
  public record Zone(String code, String description, String contact) {}

  /** A special condition code: its title (DESCRIPTION) and standard text (NOTE). */
  public record ConditionCode(String code, String title, String note) {}
}
