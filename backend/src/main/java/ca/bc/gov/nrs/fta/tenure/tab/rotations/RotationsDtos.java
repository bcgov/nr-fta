package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import java.time.LocalDate;
import java.util.List;

/**
 * The rotation tabs' wire shapes (legacy FTA611, FTA612, FTA613). Numbers the user types
 * arrive as strings, so the server can answer with legacy's own messages ("… must be an
 * integer.") rather than a JSON parse error.
 */
public final class RotationsDtos {

  private RotationsDtos() {}

  // ---------------------------------------------------------------- FTA611 grazing

  /**
   * One year's {@code RANGE_PROVISION} on the grazing tab.
   *
   * @param exists          whether the year has a provision row yet
   * @param netAuthorized   legacy's "= Net Authorized": Non-Use + TTL AUMs − PLD
   * @param revisionCount   for the optimistic lock; null when {@code exists} is false
   */
  public record GrazingProvisionDto(
      boolean exists,
      Integer nonUseForageTonnes,
      String billableNonUseInd,
      Integer totalAuthorizedGrazableForage,
      Integer totalPrivateLandGrazableForage,
      Integer netAuthorized,
      Integer maxCattle,
      Integer maxHorses,
      Integer maxSheep,
      Integer maxOtherLivestock,
      Long revisionCount) {}

  /**
   * One {@code LIVESTOCK_ROTATION} row.
   *
   * @param livestockDesc     "CODE - description"
   * @param beginRotationDate MM-DD, as legacy shows and takes it
   * @param endRotationDate   MM-DD
   */
  public record GrazingRotationDto(
      long livestockRotationSkey,
      int calendarYear,
      String livestockCode,
      String livestockDesc,
      Integer livestockCount,
      String beginRotationDate,
      String endRotationDate,
      String rangeUnitId,
      String pastureId,
      Integer authorizedGrazableForage,
      Integer privateLandGrazableForage,
      Integer rotationLineNo,
      long revisionCount) {}

  /**
   * The grazing tab for one year.
   *
   * @param years        the Year dropdown: the term's years
   * @param calendarYear the year shown — the one asked for, else legacy's default
   * @param provision    null when the tab does not apply
   */
  public record GrazingRotationsDto(
      RotationsRules rules,
      List<Integer> years,
      Integer calendarYear,
      GrazingProvisionDto provision,
      List<GrazingRotationDto> rotations) {}

  /** Save Provision: the year's header fields. {@code revisionCount} null for a new row. */
  public record GrazingProvisionRequest(
      String nonUseForageTonnes,
      String billableNonUseInd,
      String maxCattle,
      String maxHorses,
      String maxSheep,
      String maxOtherLivestock,
      Long revisionCount) {}

  /**
   * A rotation to add or change. {@code authorizedGrazableForage} (TTL AUMs) may be blank:
   * legacy then calculates it from the head count and the days. {@code revisionCount} is the
   * row's, on a change.
   */
  public record GrazingRotationRequest(
      String livestockCode,
      String livestockCount,
      String beginRotationDate,
      String endRotationDate,
      String rangeUnitId,
      String pastureId,
      String authorizedGrazableForage,
      String privateLandGrazableForage,
      Long revisionCount) {}

  // ---------------------------------------------------------------- FTA612 hay cutting

  /**
   * One year's provision on the hay tab.
   *
   * @param authorizedForageTonnes {@code RANGE_TENURE.AUTHORIZED_HARVEST_TONNES} (0 when unset)
   * @param nonUse                 {@code RANGE_PROVISION.NONUSE_FORAGE_TONNES} (0 when unset)
   * @param plusAuthHarvest        the year's rotations' harvest, summed
   * @param equalAuthorized        legacy's "= Authorized" (the authorized forage tonnes)
   * @param revisionCount          the provision's; null when the year has none yet
   */
  public record HayProvisionDto(
      Integer authorizedForageTonnes,
      Integer nonUse,
      String billableInd,
      Integer plusAuthHarvest,
      Integer equalAuthorized,
      Long revisionCount) {}

  /** One {@code MEADOW_ROTATION} row. */
  public record HayRotationDto(
      long meadowRotationSkey,
      int calendarYear,
      String permitBlockId,
      String rangeUnitId,
      String meadowName,
      Integer authorizedHarvestableForage,
      LocalDate updateTimestamp,
      String updateUserid,
      long revisionCount) {}

  /** The hay tab for one year. */
  public record HayRotationsDto(
      RotationsRules rules,
      List<Integer> years,
      Integer calendarYear,
      HayProvisionDto provision,
      List<HayRotationDto> rotations) {}

  /**
   * The hay tab's one Save: the provision and every row, as legacy saves them in one
   * transaction (the year's harvest plus non-use must equal the authorized tonnes, so rows
   * cannot be saved one at a time).
   */
  public record HaySaveRequest(
      String nonUse,
      String billableInd,
      Long provisionRevisionCount,
      List<HayRowRequest> rows) {}

  /**
   * A row of the hay grid: an existing one ({@code meadowRotationSkey} and
   * {@code revisionCount} set; {@code delete} to remove it) or a new one (both null).
   */
  public record HayRowRequest(
      Long meadowRotationSkey,
      Long revisionCount,
      boolean delete,
      String permitBlockId,
      String rangeUnitId,
      String meadowName,
      String authorizedHarvest) {}

  /** What a hay save did, and legacy's confirmation for it. */
  public record HaySaveResult(int saved, int deleted, String message) {}

  // ---------------------------------------------------------------- FTA613 copy

  /**
   * The copy tab.
   *
   * @param kind             GRAZING or HAY: which rotations a copy writes; null when it
   *                         does not apply
   * @param pfuRevisionCount sent back with the copy (legacy guards the file's revision)
   */
  public record CopyRotationDto(
      RotationsRules rules,
      String kind,
      Integer termStartYear,
      Integer termEndYear,
      Long pfuRevisionCount) {}

  /**
   * A copy. Exactly one of {@code targetYears} (up to 9) and {@code everyOtherYearFrom} is
   * given. {@code overwrite} confirms replacing target years that already have rotations.
   */
  public record CopyRotationRequest(
      String sourceForestFileId,
      String sourceYear,
      String sourceRangeUnitId,
      List<String> targetYears,
      String everyOtherYearFrom,
      boolean overwrite,
      Long pfuRevisionCount) {}

  /**
   * The outcome: {@code copied} false means nothing was written and {@code message} asks
   * to confirm overwriting the years that already have rotations (legacy's "Press Save again
   * to overwrite").
   */
  public record CopyRotationResult(boolean copied, String message, List<Integer> targetYears) {}
}
