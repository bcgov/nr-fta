package ca.bc.gov.nrs.fta.tenure.tab.harvesthistory;

import ca.bc.gov.nrs.fta.tenure.tab.MissingTable;
import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryDtos.HarvestHistoryDto;
import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryDtos.HarvestHistoryRow;
import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryRules.Appraisal;
import ca.bc.gov.nrs.fta.tenure.tab.harvesthistory.HarvestHistoryRules.AppraisalRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A tenure's harvest history — legacy FTA990, which FTA100's "Harvest History" button opened on
 * the file's timber mark. Read-only: the billing history comes from the revenue systems
 * (HBS, ECAS…) through {@code HARVEST_HISTORY}, {@code APP_WORKSHEET} and
 * {@code APP_STMPG_RATE}.
 *
 * <p>Ports {@code FTA_990_HARV_HIST.GET}:
 *
 * <ul>
 *   <li>the file's timber mark, as FTA100's GET found it: its harvesting authority's
 *       {@code HARVESTING_HAULING_XREF} mark, which must have a {@code HAULING_AUTHORITY} row;
 *   <li>the appraisal header ({@code cur_hh}), whose row walk is {@link HarvestHistoryRules};
 *       legacy's cursor also joined {@code HARVEST_HISTORY} — only to require some history, so
 *       that is an EXISTS here (it no longer repeats each row per history row);
 *   <li>the history: billed volume and dollars per year, billing type and species, plus a
 *       total row per year (legacy's {@code UNION} into {@code FTA990_HARV_HIST_RESULTS}, read
 *       back by year with the total last);
 *   <li>the oldest billing period and the overall billed volume and dollars.
 * </ul>
 *
 * <p>The sums keep legacy's arithmetic: the history rows add the five amounts without
 * {@code NVL} (a row missing one counts for nothing in that sum), the header's "Billed $" with
 * it. The detail rows inner-join {@code CODE_LIST_TABLE} (legacy's {@code (+)} was voided by its
 * {@code column_name} filter), so a billing type without a description is left out of the rows
 * but still counted in the year's total, as in legacy. {@code P_BILLING_YRMNTH} was never set
 * by the package nor shown, so it is not ported.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class HarvestHistoryService {

  /** The file's type and timber mark (FTA_100_TENURE's cur_get_hva, haa present). */
  private static final String FILE_SQL =
      """
      SELECT pfu.file_type_code,
             (SELECT MAX(xref.timber_mark) KEEP (DENSE_RANK LAST ORDER BY hva.hva_skey)
                FROM the.harvesting_authority hva
                JOIN the.harvesting_hauling_xref xref ON xref.hva_skey = hva.hva_skey
                JOIN the.hauling_authority haa        ON haa.timber_mark = xref.timber_mark
               WHERE hva.forest_file_id = pfu.forest_file_id) AS timber_mark
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  /** FTA_990_HARV_HIST's cur_hh: confirmed appraisals, newest first, with their rates. */
  private static final String APPRAISAL_SQL =
      """
      SELECT asr.timber_mark AS asr_timber_mark,
             asr.tot_stumpage_rate,
             aw.net_cruise_volume,
             asr.bonus_bid_amount,
             asr.silviculture_levy,
             asr.development_levy,
             aw.adjust_qrterly_ind,
             aw.appraisal_sts_st
        FROM the.app_worksheet aw
        LEFT JOIN the.app_stmpg_rate asr
               ON asr.timber_mark = aw.timber_mark
              AND asr.app_effective_date = aw.app_effective_date
       WHERE aw.timber_mark = :timberMark
         AND aw.appraisal_sts_st = 'CNF'
         AND EXISTS (SELECT 1
                       FROM the.harvest_history hh
                       JOIN the.hauling_authority tm ON tm.timber_mark = hh.timber_mark
                      WHERE hh.timber_mark = :timberMark)
       ORDER BY aw.app_effective_date DESC, asr.stmpg_rte_efctv_dt DESC
      """;

  /** The history rows and each year's total (legacy's UNION, then ORDER BY year, mark). */
  private static final String HISTORY_SQL =
      """
      SELECT hh.billing_year,
             clt.expanded_result                 AS billing_type,
             hh.hdbs_tree_species,
             SUM(hh.volume_billed)               AS volume_billed,
             SUM(hh.royalty_amount + hh.reserve_stmpg_amt + hh.bonus_stumpage_amt
                 + hh.silv_levy_amount + hh.dev_levy_amount) AS total_billed,
             SUM(hh.royalty_amount + hh.reserve_stmpg_amt
                 + hh.silv_levy_amount + hh.dev_levy_amount) AS upset_billed,
             SUM(hh.bonus_stumpage_amt)          AS bonus_billed,
             0                                   AS subtotal
        FROM the.harvest_history hh
        JOIN the.hauling_authority tm ON tm.timber_mark = hh.timber_mark
        JOIN the.code_list_table clt
          ON clt.column_name = 'BILLING_TYPE_CODE'
         AND clt.code_argument = hh.billing_type_code
       WHERE hh.timber_mark = :timberMark
       GROUP BY hh.billing_year, clt.expanded_result, hh.hdbs_tree_species
      UNION ALL
      SELECT hh.billing_year,
             NULL,
             NULL,
             SUM(hh.volume_billed),
             SUM(hh.royalty_amount + hh.reserve_stmpg_amt + hh.bonus_stumpage_amt
                 + hh.silv_levy_amount + hh.dev_levy_amount),
             SUM(hh.royalty_amount + hh.reserve_stmpg_amt
                 + hh.silv_levy_amount + hh.dev_levy_amount),
             SUM(hh.bonus_stumpage_amt),
             1
        FROM the.harvest_history hh
        JOIN the.hauling_authority tm ON tm.timber_mark = hh.timber_mark
       WHERE hh.timber_mark = :timberMark
       GROUP BY hh.billing_year
       ORDER BY 1, 8, 2, 3
      """;

  /** The header's oldest billing period and overall billed volume / dollars. */
  private static final String TOTALS_SQL =
      """
      SELECT MIN(hh.billing_year * 100 + TO_NUMBER(hh.billing_month)) AS oldest_period,
             SUM(hh.volume_billed)                                    AS billed_volume,
             SUM(NVL(hh.royalty_amount, 0) + NVL(hh.reserve_stmpg_amt, 0)
                 + NVL(hh.bonus_stumpage_amt, 0) + NVL(hh.silv_levy_amount, 0)
                 + NVL(hh.dev_levy_amount, 0))                        AS total_billed
        FROM the.harvest_history hh
        JOIN the.hauling_authority tm ON tm.timber_mark = hh.timber_mark
       WHERE hh.timber_mark = :timberMark
      """;

  private record Totals(Long oldestPeriod, BigDecimal billedVolume, BigDecimal totalBilled) {}

  private final NamedParameterJdbcTemplate jdbc;

  public HarvestHistoryService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The file's harvest history; unavailable (with the reason) for a file that is not B-type or
   * has no hauling authority.
   *
   * @throws ResponseStatusException 404 if the file does not exist
   */
  public HarvestHistoryDto get(String forestFileId) {
    String[] file;
    try {
      file = jdbc.queryForObject(
          FILE_SQL,
          new MapSqlParameterSource("forestFileId", forestFileId),
          (rs, rowNum) -> new String[] {rs.getString("file_type_code"),
              rs.getString("timber_mark")});
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
    String timberMark = file[1];
    String reason = HarvestHistoryRules.unavailableReason(file[0], timberMark);
    if (reason != null) {
      return HarvestHistoryDto.unavailable(reason, timberMark);
    }
    return history(timberMark);
  }

  /**
   * One timber mark's harvest history — what FTA902's "Harvest History" button opened, on the
   * permit's mark whatever the file type.
   */
  public HarvestHistoryDto forTimberMark(String timberMark) {
    return history(timberMark.trim().toUpperCase(java.util.Locale.ROOT));
  }

  private HarvestHistoryDto history(String timberMark) {
    MapSqlParameterSource params = new MapSqlParameterSource("timberMark", timberMark);
    List<String> notices = new ArrayList<>();
    List<AppraisalRow> appraisalRows = MissingTable.orEmpty(
        () -> jdbc.query(APPRAISAL_SQL, params, (rs, rowNum) -> new AppraisalRow(
            rs.getString("asr_timber_mark") != null,
            rs.getBigDecimal("tot_stumpage_rate"),
            rs.getBigDecimal("net_cruise_volume"),
            rs.getBigDecimal("bonus_bid_amount"),
            rs.getBigDecimal("silviculture_levy"),
            rs.getBigDecimal("development_levy"),
            rs.getString("adjust_qrterly_ind"),
            rs.getString("appraisal_sts_st"))),
        "The appraisal (rates and cruise)", notices);
    List<HarvestHistoryRow> rows = MissingTable.orEmpty(
        () -> jdbc.query(HISTORY_SQL, params, (rs, rowNum) -> {
          boolean subtotal = rs.getInt("subtotal") == 1;
          return new HarvestHistoryRow(
              rs.getObject("billing_year", Integer.class),
              subtotal ? null : rs.getString("billing_type"),
              subtotal ? null : rs.getString("hdbs_tree_species"),
              rs.getBigDecimal("volume_billed"),
              rs.getBigDecimal("total_billed"),
              rs.getBigDecimal("upset_billed"),
              rs.getBigDecimal("bonus_billed"),
              subtotal);
        }),
        "The harvest history", notices);
    List<Totals> totals = MissingTable.orEmpty(
        () -> jdbc.query(TOTALS_SQL, params, (rs, rowNum) -> new Totals(
            rs.getObject("oldest_period", Long.class),
            rs.getBigDecimal("billed_volume"),
            rs.getBigDecimal("total_billed"))),
        "The billed totals", notices);
    Totals t = totals.isEmpty() ? new Totals(null, null, null) : totals.get(0);

    Appraisal a = HarvestHistoryRules.appraisal(appraisalRows);
    return new HarvestHistoryDto(
        true,
        null,
        timberMark,
        HarvestHistoryRules.billingPeriod(t.oldestPeriod()),
        a.netCruiseVolume(),
        t.billedVolume(),
        t.totalBilled(),
        a.initialRate(),
        a.currentRate(),
        a.originalUpsetRate(),
        a.bonusBid(),
        a.silvicultureLevy(),
        a.developmentLevy(),
        a.adjustQuarterlyInd(),
        a.appraisalStatus(),
        rows,
        notices);
  }
}
