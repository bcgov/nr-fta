package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import java.time.LocalDate;
import java.util.List;

/**
 * The rotation reads' wire shapes (legacy FTA611, FTA612).
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
}
