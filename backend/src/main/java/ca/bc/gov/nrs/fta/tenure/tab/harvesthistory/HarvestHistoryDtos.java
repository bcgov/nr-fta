package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import java.math.BigDecimal;
import java.util.List;

/** Response shapes of the tenure "Harvest history" tab (legacy FTA990, read-only). */
public final class HarvestHistoryDtos {

  private HarvestHistoryDtos() {}

  /**
   * One row of FTA990's history: the volume and dollars billed in a calendar year for a billing
   * type and species, or — {@code subtotal} — the year's total over every billing type and
   * species, which legacy listed after the year's rows in bold as "{year} Total:".
   *
   * @param billingYear  calendar year billed
   * @param billingType  the billing type's description ({@code CODE_LIST_TABLE}); null on a
   *                     subtotal
   * @param species      HDBS tree species; null on a subtotal
   * @param volumeBilled m3 billed in the year
   * @param totalBilled  total $ billed: royalty + reserve stumpage + bonus + silviculture and
   *                     development levies (= upset + bonus)
   * @param upsetBilled  upset $ billed: the total less bonus stumpage
   * @param bonusBilled  bonus stumpage $ billed
   * @param subtotal     whether this is the year's total row
   */
  public record HarvestHistoryRow(
      Integer billingYear,
      String billingType,
      String species,
      BigDecimal volumeBilled,
      BigDecimal totalBilled,
      BigDecimal upsetBilled,
      BigDecimal bonusBilled,
      boolean subtotal) {}

  /**
   * {@code GET /api/fta/tenures/{forestFileId}/harvest-history} — FTA990's header and history
   * for the tenure's timber mark ({@code FTA_990_HARV_HIST.GET}).
   *
   * @param available             false when the tab does not apply (not a B-type file, or the
   *                              file has no hauling authority / timber mark)
   * @param unavailableReason     why not, when not
   * @param timberMark            the tenure's timber mark
   * @param oldestBillingAccessed earliest billing period in the history, "yyyy-MM"
   * @param netCruiseVolume       "Cruised net (m3)": the latest confirmed appraisal's cruise
   * @param billedVolume          "Billed (m3)": every period's billed volume
   * @param totalBilled           "Billed $": every period's total
   * @param initialRate           "Initial Rate ($)": the earliest confirmed stumpage rate
   * @param currentRate           "Current Rate ($)": the latest confirmed stumpage rate
   * @param originalUpsetRate     "Original Upset Rate ($)": initial rate less the bonus bid
   * @param bonusBid              "Bonus Bid ($)", of the current rate
   * @param silvicultureLevy      "Silviculture Levy ($)", of the current rate
   * @param developmentLevy       "Development Levy ($)", of the current rate
   * @param adjustQuarterlyInd    "Fixed/Adjustable Rate": the appraisal's
   *                              {@code ADJUST_QRTERLY_IND}, as legacy showed it
   * @param appraisalStatus       "Appraisal Status" ({@code APPRAISAL_STS_ST}, always CNF)
   * @param rows                  the history, by year, each year's total last
   * @param unavailable           notices for parts this database could not read (a missing
   *                              table — the history comes from the revenue systems)
   */
  public record HarvestHistoryDto(
      boolean available,
      String unavailableReason,
      String timberMark,
      String oldestBillingAccessed,
      BigDecimal netCruiseVolume,
      BigDecimal billedVolume,
      BigDecimal totalBilled,
      BigDecimal initialRate,
      BigDecimal currentRate,
      BigDecimal originalUpsetRate,
      BigDecimal bonusBid,
      BigDecimal silvicultureLevy,
      BigDecimal developmentLevy,
      String adjustQuarterlyInd,
      String appraisalStatus,
      List<HarvestHistoryRow> rows,
      List<String> unavailable) {

    /** The tab does not apply to this file. */
    static HarvestHistoryDto unavailable(String reason, String timberMark) {
      return new HarvestHistoryDto(false, reason, timberMark, null, null, null, null, null, null,
          null, null, null, null, null, null, List.of(), List.of());
    }
  }
}
