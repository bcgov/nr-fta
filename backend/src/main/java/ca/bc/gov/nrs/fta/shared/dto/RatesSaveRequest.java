package ca.bc.gov.nrs.fta.shared.dto;

import java.util.List;

/**
 * Request body for saving standard Range Billing rates/fees ({@code PUT
 * /api/fta/admin/rates}). Ports the write side of the legacy PL/SQL package
 * {@code THE.FTA_699_RATEFEE} (procedure {@code save} / {@code
 * update_range_bill_rate}).
 *
 * <p>The FTA699 screen displays a set of rate types for a calendar year; the
 * underlying {@code THE.RANGE_BILL_RATE} structure stores several database rows
 * per year (keyed by Range Tenure Type, Range Rate Code and Revenue
 * Classification). Each {@link RateItem} is one such row as loaded by the read
 * endpoint, carrying the edited {@code rangeRate} back to the server.
 */
public record RatesSaveRequest(Integer calendarYear, List<RateItem> rates) {

  /**
   * A single editable {@code THE.RANGE_BILL_RATE} row. Fields mirror the columns
   * updated by the legacy {@code update_range_bill_rate} procedure body.
   */
  public record RateItem(
      Long rangeBillRateId,
      String rangeFileTypeCode,
      String rangeRateTypeCode,
      String revenueClassnCode,
      Double rangeRate,
      String rngTenrRateDesc) {}
}
