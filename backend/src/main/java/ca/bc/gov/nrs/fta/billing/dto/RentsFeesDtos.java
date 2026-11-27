package ca.bc.gov.nrs.fta.billing.dto;

import java.util.List;

/** FTA670 Annual Rents and Fees Preparation — {@code /api/fta/admin/rents-fees}. */
public final class RentsFeesDtos {

  private RentsFeesDtos() {}

  /**
   * One row of {@code FTA_670_RENTFEE.GET}'s cursor ({@code rec_RentFee}). The numbers are the
   * package's own formatted text ({@code FM999,999,990.00}). {@code updateTimestamp} is
   * {@code BILLABLE_TENURE.UPDATE_TIMESTAMP} as the package formats it, the optimistic lock
   * Prepare hands back.
   */
  public record RentFeeRowDto(
      String forestFileId,
      String calendarYear,
      /** The Opt indicator ({@code INVOICE_PREP_IND}): "", "R" or "D". */
      String invoicePrepInd,
      String fileTypeCode,
      String orgCode,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      /** YYYY-MM-DD. */
      String anniversaryDate,
      String unitsBillable,
      /** "M3" or "HA". */
      String unitType,
      String unitRate,
      String amount,
      String updateTimestamp) {}

  /**
   * Go's result: the billing period legacy showed above the list (next month; the start moved
   * back to the earliest anniversary listed) and the rows, in the package's order.
   */
  public record RentFeeListDto(String billFrom, String billTo, List<RentFeeRowDto> rows) {}

  /** One row of the list as Prepare sends it back: what the user saw, and the Opt chosen. */
  public record RentFeeChange(
      String forestFileId,
      String calendarYear,
      String invoicePrepInd,
      String invoicePrepIndNew,
      String updateTimestamp) {}

  /**
   * Prepare: the criteria of the last Go, and every row of its list in the order shown (row
   * numbers in messages count from 1 in this order).
   */
  public record RentFeePrepareRequest(Long orgUnitNo, Boolean rollup, List<RentFeeChange> rows) {}

  /** How many rows were saved, and legacy's confirmation ("Prepare successful." or not). */
  public record RentFeePrepareResult(int saved, String message) {}
}
