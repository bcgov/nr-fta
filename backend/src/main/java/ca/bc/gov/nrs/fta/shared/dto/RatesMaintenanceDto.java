package ca.bc.gov.nrs.fta.shared.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One calendar year of standard Range Billing rates and fees, as FTA699 shows it.
 *
 * <p>Mirrors the OUT parameters of {@code THE.FTA_699_RATEFEE.get}: the 12 values the screen
 * shows (each stored on two or three {@code RANGE_BILL_RATE} rows, 24 per year), and the
 * update_timestamp and update_userid of the year's latest rows. A year with no rows yet shows
 * 0.00 everywhere and no timestamp; saving it inserts the 24 rows.
 *
 * @param calendarYear    the year shown
 * @param updateTimestamp the rows' update_timestamp ({@code yyyy-MM-dd HH:mm:ss}), sent back on
 *                        save as the optimistic lock; null for a year not yet entered
 * @param updateUserid    who last saved the year; null for a year not yet entered
 * @param corrupt         the stored rows are not the year's 24 (legacy {@code
 *                        fta699.corrupt_rates}); rates are then empty and Save is off
 * @param rates           the 12 values, in the screen's order
 */
public record RatesMaintenanceDto(
    Integer calendarYear,
    String updateTimestamp,
    String updateUserid,
    boolean corrupt,
    List<Rate> rates) {

  /**
   * One of the 12 values.
   *
   * @param code  the rate type code legacy keyed it by (GGR, GUF, ... NHI)
   * @param label the label fta699Ratefee.jsp gave it
   * @param rate  the value, or null when the year is corrupt
   */
  public record Rate(String code, String label, BigDecimal rate) {}
}
