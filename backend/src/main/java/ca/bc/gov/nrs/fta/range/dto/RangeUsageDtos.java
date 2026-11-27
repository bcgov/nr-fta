package ca.bc.gov.nrs.fta.range.dto;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;

/** Request and response bodies of the FTA613R Range Usage editors. */
public final class RangeUsageDtos {

  private RangeUsageDtos() {}

  /**
   * Save one year's usage (legacy Save: add a year, or update the year picked with Select).
   * The amounts are text, as legacy's form took them, so a value that is not a whole number gets
   * legacy's "must be an integer" message.
   *
   * @param revisionCount the row's revision as read, or null for a year that has no usage yet
   */
  public record SaveRequest(
      String authorizedUse,
      String tempIncrease,
      String rangeIncreaseReasonCode,
      String nonUseAgreementNo,
      String nonUseBillable,
      String nonUseNonbillable,
      String rangeNonUseReasonCode,
      Integer revisionCount) {}

  /**
   * Copy a year's usage to other years (legacy Copy, COPY_UPDATE): either every remaining year
   * of the term, or the years From to To.
   */
  public record CopyRequest(boolean allRemainingYears, String copyFromYear, String copyToYear) {}

  /**
   * What the editors need: the term's years ({@code FTA_CODE_LISTS.GET_RANGE_CALENDAR_YEAR}), the
   * current reason codes, and whether an application for the file is in the Inbox (legacy then
   * warned and disabled Save and Copy).
   */
  public record Options(
      List<Integer> years,
      List<CodeOptionDto> increaseReasons,
      List<CodeOptionDto> nonUseReasons,
      boolean applicationPending) {}

  /** What a save or copy wrote, for the confirmation. */
  public record Result(String message) {}
}
