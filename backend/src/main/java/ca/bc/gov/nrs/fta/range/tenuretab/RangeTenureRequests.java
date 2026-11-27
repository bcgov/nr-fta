package ca.bc.gov.nrs.fta.range.tenuretab;

import java.util.List;
import java.util.Map;

/** The Tenure tab's request and response bodies. */
public final class RangeTenureRequests {

  private RangeTenureRequests() {}

  /**
   * {@code PUT /api/fta/range-tenures/{id}/tenure-tab}.
   *
   * @param revisions the GET's {@code revisions}, unchanged — the optimistic lock
   * @param values    the edited values by API key; keys the user may not change are ignored.
   *                  {@code districtOverrideReason} when the change needs one
   */
  public record Save(Map<String, String> revisions, Map<String, String> values) {}

  /** Retire and Cancel Replacement: the GET's {@code revisions}, unchanged. */
  public record Action(Map<String, String> revisions) {}

  /** A successful write: legacy's confirmation plus the package's warnings, if any. */
  public record Result(String message, List<String> warnings) {}

  /**
   * Save Condition: a new special condition (no {@code skey}) or a change to one.
   *
   * @param revisionCount the condition's revision count, for a change
   */
  public record Condition(String code, String title, String description,
      Integer revisionCount) {}

  /**
   * {@code POST /api/fta/range-tenures} — FTA100R's Add New, after FTA010 picked a range type.
   *
   * @param fileStatusDate yyyy-mm-dd; today when blank (setDefaults)
   */
  public record Create(String fileTypeCode, String forestDistrictNo, String fileStatusDate,
      String mgmtUnitType, String mgmtUnitId, String districtAdminZone, String fileName) {}

  /** The new file's id. */
  public record Created(String forestFileId, List<String> warnings) {}
}
