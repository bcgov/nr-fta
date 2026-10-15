package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Legacy FTA990's gate and header logic, kept apart from the SQL so it can be tested.
 *
 * <p><b>Gate</b> — FTA100 showed its "Harvest History" button for every file whose type starts
 * with B (the JSP's comment says "except B40", but nothing excludes B40: the condition is
 * {@code fileTypeFirstLetter == "B"} alone), disabled when the file has no hauling authority
 * ({@code p_haa_revision_count} blank). The button opened FTA990 on the file's timber mark.
 *
 * <p><b>Appraisal</b> — {@code FTA_990_HARV_HIST.GET} walks the timber mark's confirmed
 * ({@code CNF}) appraisal worksheets, newest first, each outer-joined to its stumpage rates
 * (newest first): the cruise volume is the newest worksheet's; the "current" rate, bonus bid,
 * levies and fixed/adjustable indicator come from the newest row that has a stumpage rate; the
 * "initial" rate is the oldest row's that has one; the original upset rate is initial less the
 * bonus bid. Legacy's cursor loop gets there by fetching rows in pairs, which skipped every
 * other row while it was still looking for the first rate — a quirk of a cursor whose rows were
 * multiplied by the mark's harvest-history rows, so not reproducible on purpose; this takes the
 * rows the loop was written to take.
 */
public final class HarvestHistoryRules {

  static final String NOT_B_TYPE =
      "Harvest history is kept only for B-type (timber sale) files.";
  static final String NO_HAULING_AUTHORITY =
      "This tenure has no hauling authority (timber mark), so it has no harvest history.";

  private HarvestHistoryRules() {}

  /** Why the tab does not apply to the file, or null when it does. */
  static String unavailableReason(String fileTypeCode, String timberMark) {
    if (fileTypeCode == null || !fileTypeCode.trim().startsWith("B")) {
      return NOT_B_TYPE;
    }
    if (timberMark == null || timberMark.isBlank()) {
      return NO_HAULING_AUTHORITY;
    }
    return null;
  }

  /**
   * One row of legacy's appraisal cursor: a confirmed appraisal worksheet, outer-joined to one of
   * its stumpage rates ({@code hasRate} false when it has none).
   */
  record AppraisalRow(
      boolean hasRate,
      BigDecimal totStumpageRate,
      BigDecimal netCruiseVolume,
      BigDecimal bonusBidAmount,
      BigDecimal silvicultureLevy,
      BigDecimal developmentLevy,
      String adjustQrterlyInd,
      String appraisalStsSt) {}

  /** The header values FTA990 derives from the appraisal rows. */
  record Appraisal(
      BigDecimal netCruiseVolume,
      BigDecimal initialRate,
      BigDecimal currentRate,
      BigDecimal originalUpsetRate,
      BigDecimal bonusBid,
      BigDecimal silvicultureLevy,
      BigDecimal developmentLevy,
      String adjustQuarterlyInd,
      String appraisalStatus) {}

  /**
   * The header from the appraisal rows, newest first (worksheet effective date, then rate
   * effective date, both descending). Values are rounded to cents, as legacy's
   * {@code TO_CHAR(…, 'FM…0.00')} did before it subtracted the bonus bid.
   */
  static Appraisal appraisal(List<AppraisalRow> rows) {
    if (rows.isEmpty()) {
      return new Appraisal(null, null, null, null, null, null, null, null, null);
    }
    AppraisalRow current = rows.stream().filter(AppraisalRow::hasRate).findFirst().orElse(null);
    AppraisalRow initial = rows.stream().filter(AppraisalRow::hasRate)
        .reduce((first, second) -> second).orElse(null);
    // With no rate at all legacy's loop ran to the end of the cursor, leaving the last row's
    // indicator and status.
    AppraisalRow source = current != null ? current : rows.get(rows.size() - 1);

    BigDecimal initialRate = initial == null ? null : cents(initial.totStumpageRate());
    BigDecimal bonusBid = current == null ? null : cents(current.bonusBidAmount());
    BigDecimal originalUpset = initialRate == null || bonusBid == null
        ? null : initialRate.subtract(bonusBid);
    return new Appraisal(
        cents(rows.get(0).netCruiseVolume()),
        initialRate,
        current == null ? null : cents(current.totStumpageRate()),
        originalUpset,
        bonusBid,
        current == null ? null : cents(current.silvicultureLevy()),
        current == null ? null : cents(current.developmentLevy()),
        source.adjustQrterlyInd(),
        source.appraisalStsSt());
  }

  /**
   * The oldest billing period, from {@code MIN(billing_year * 100 + billing_month)}, as
   * "yyyy-MM". Legacy took {@code MIN(billing_year || '-' || billing_month)} — a string minimum,
   * which ranks "2019-10" before "2019-9"; this compares the periods as numbers.
   */
  static String billingPeriod(Long yearMonth) {
    if (yearMonth == null) {
      return null;
    }
    return String.format("%04d-%02d", yearMonth / 100, yearMonth % 100);
  }

  private static BigDecimal cents(BigDecimal v) {
    return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
  }
}
