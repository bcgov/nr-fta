package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.TenureDetailDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenure detail business logic (FTA100).
 *
 * <p>Ports the GET path of the legacy Oracle package
 * {@code THE.FTA_100_TENURE.mainline} to a native query against the shared
 * {@code THE} schema, keyed by forest file id. The file-level "common tenure"
 * columns (type, status, org unit, licensee, award/expiry dates, tenure term)
 * come from {@code PROV_FOREST_USE}/{@code TENURE_TERM}/{@code ORG_UNIT} exactly
 * as the package's main SELECT does. The AAC summary mirrors
 * {@code THE.FTA_930_AAC} (timber_tenure areas + most-recent allocation period
 * total) and the sale-info summary mirrors {@code THE.FTA_940_SALE_INFO}
 * ({@code HARVEST_SALE}/{@code TENURE_DEPOSIT}).
 *
 * <p>NOTE: the SQL is derived from the package bodies/specs; the legacy code
 * resolves the licensee, management-unit description and "notes attached" flag
 * via helper functions ({@code FTA_GET_MAIN_LICENSEE},
 * {@code FTA_GET_MGMT_UNIT_DESC}) — here they are inlined as the A-type client
 * join, a type/id concatenation, and a correlated count. The query runs against
 * the BC Gov shared Oracle ({@code THE}); there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class TenureDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public TenureDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String DETAIL_SQL =
      """
      SELECT pfu.forest_file_id            AS forest_file_id,
             pfu.file_type_code            AS file_type_code,
             pfu.file_status_st            AS file_status_code,
             fsc.description               AS file_status_desc,
             pfu.file_status_date          AS file_status_date,
             org.org_unit_code             AS org_unit_code,
             ffc.client_number             AS client_number,
             ffc.forest_file_client_locn   AS client_locn_code,
             cli.client_name               AS licensee,
             pfu.mgmt_unit_type            AS mgmt_unit_type,
             pfu.mgmt_unit_id              AS mgmt_unit_id,
             pfu.mgmt_unit_type || '-' || pfu.mgmt_unit_id AS management_unit,
             tt.legal_effective_dt         AS award_date,
             tt.current_expiry_dt          AS expiry_date,
             tt.initial_expiry_dt          AS initial_expiry_date,
             tt.tenure_term                AS tenure_term_months,
             tt.tenure_extend_cnt          AS extension_count,
             (SELECT DECODE(COUNT(*), 0, 'N', 'Y')
                FROM the.forest_file_client sec
               WHERE sec.forest_file_id = pfu.forest_file_id
                 AND sec.forest_file_client_type_code = 'B') AS sec_licensee_ind,
             (SELECT DECODE(COUNT(*), 0, ' ', 'Note(s) Attached')
                FROM the.provforest_note pn
               WHERE pn.forest_file_id = pfu.forest_file_id) AS notes_label,
             ttn.schedule_a_area           AS schedule_a_area,
             ttn.schedule_b_area           AS schedule_b_area,
             (SELECT SUM(aaa.allocation_amount)
                FROM the.aac_allocation_period aap
                JOIN the.aac_allocation_amount aaa
                  ON aaa.aac_allocation_period_id = aap.aac_allocation_period_id
               WHERE aap.forest_file_id = pfu.forest_file_id
                 AND aap.aac_allocation_period_id = (
                     SELECT aac_allocation_period_id FROM (
                       SELECT aac_allocation_period_id,
                              ROW_NUMBER() OVER (ORDER BY effective_date DESC) rn
                         FROM the.aac_allocation_period
                        WHERE forest_file_id = pfu.forest_file_id)
                      WHERE rn = 1)) AS allowable_annual_cut,
             hs.sale_method_code           AS sale_method_code,
             hs.sale_type_cd               AS sale_type_code,
             hs.payment_method_cd          AS payment_method_code,
             hs.cash_sale_est_vol          AS cash_sale_est_vol,
             hs.cash_sale_tot_dol          AS cash_sale_tot_dol,
             hs.fta_bonus_bid              AS fta_bonus_bid,
             hs.fta_bonus_offer            AS fta_bonus_offer,
             td.scrty_deposit_code         AS scrty_deposit_code,
             td.scrty_deposit_amt          AS scrty_deposit_amt
        FROM the.prov_forest_use pfu
        JOIN the.org_unit org              ON org.org_unit_no = pfu.forest_region
        LEFT JOIN the.tenure_term tt        ON tt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.timber_tenure ttn     ON ttn.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.file_status_code fsc  ON fsc.file_status_st = pfu.file_status_st
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = pfu.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli            ON cli.client_number = ffc.client_number
        LEFT JOIN the.harvest_sale hs       ON hs.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.tenure_deposit td     ON td.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private static final RowMapper<TenureDetailDto> ROW_MAPPER = (rs, rowNum) -> new TenureDetailDto(
      rs.getString("forest_file_id"),
      rs.getString("file_type_code"),
      rs.getString("file_status_code"),
      rs.getString("file_status_desc"),
      rs.getObject("file_status_date", LocalDate.class),
      rs.getString("org_unit_code"),
      rs.getString("client_number"),
      rs.getString("client_locn_code"),
      rs.getString("licensee"),
      rs.getString("mgmt_unit_type"),
      rs.getString("mgmt_unit_id"),
      rs.getString("management_unit"),
      rs.getObject("award_date", LocalDate.class),
      rs.getObject("expiry_date", LocalDate.class),
      rs.getObject("initial_expiry_date", LocalDate.class),
      rs.getObject("tenure_term_months", Integer.class),
      rs.getObject("extension_count", Integer.class),
      rs.getString("sec_licensee_ind"),
      rs.getString("notes_label"),
      rs.getBigDecimal("schedule_a_area"),
      rs.getBigDecimal("schedule_b_area"),
      rs.getBigDecimal("allowable_annual_cut"),
      rs.getString("sale_method_code"),
      rs.getString("sale_type_code"),
      rs.getString("payment_method_code"),
      rs.getBigDecimal("cash_sale_est_vol"),
      rs.getBigDecimal("cash_sale_tot_dol"),
      rs.getBigDecimal("fta_bonus_bid"),
      rs.getBigDecimal("fta_bonus_offer"),
      rs.getString("scrty_deposit_code"),
      rs.getBigDecimal("scrty_deposit_amt"));

  /**
   * Load one tenure's detail — mirrors {@code FTA_100_TENURE.mainline} GET.
   *
   * @param forestFileId the forest file id (exact match)
   * @return the detail record, or {@code null} when no such file exists
   */
  public TenureDetailDto findByForestFileId(String forestFileId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("forestFileId", forestFileId);
    List<TenureDetailDto> rows = jdbc.query(DETAIL_SQL, params, ROW_MAPPER);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
