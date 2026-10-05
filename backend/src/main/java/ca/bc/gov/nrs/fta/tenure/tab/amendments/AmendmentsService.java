package ca.bc.gov.nrs.fta.tenure.tab.amendments;

import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsAmendmentRow;
import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsBlockDetailDto;
import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsBlockRow;
import ca.bc.gov.nrs.fta.tenure.tab.amendments.AmendmentsDtos.AmendmentsListDto;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * A tenure's cutting permit / cut block amendments — legacy FTA905, which is read-only.
 *
 * <p>The list ports {@code FTA_905_CP_AMEND.GET} with no CP (the whole file): each cut block of
 * the file's harvesting authorities that has {@code HARVEST_AMEND} rows, with the sums of their
 * net / gross area and cruise volume. The drill-down ports {@code FTA_905_BLK_AMEND} ("Amend
 * Details"): the block's header from {@code CUT_BLOCK} / {@code CUT_BLOCK_OPEN_ADMIN} and every
 * amendment, with its exhibit image ({@code CUT_BLOCK_AMEND_GEOM} →
 * {@code TENURE_APPLICATION_MAP_FEATURE}) when there is one. Both apply legacy's file-type gate
 * ({@link AmendmentsRules}). Legacy's comma joins with {@code (+)} are ANSI joins here.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class AmendmentsService {

  private static final String FILE_SQL =
      """
      SELECT pfu.file_type_code,
             (SELECT COUNT(*) FROM the.range_file_type_code r
               WHERE r.range_file_type_code = pfu.file_type_code)       AS range_count,
             (SELECT COUNT(*) FROM the.recreation_file_type_code rc
               WHERE rc.recreation_file_type_code = pfu.file_type_code) AS recreation_count
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private static final String LIST_SQL =
      """
      SELECT DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                    hva.cutting_permit_id)                  AS cutting_permit_id,
             DECODE(hva.harvest_type_code, 'F', 'Y', 'N')   AS fsj_ind,
             cb.timber_mark,
             cb.cut_block_id,
             cb.cb_skey,
             SUM(NVL(ha.est_net_blk_area, 0))               AS est_net_blk_area,
             SUM(NVL(ha.est_gross_blk_area, 0))             AS est_gross_blk_area,
             SUM(NVL(ha.cruise_volume, 0))                  AS cruise_volume
        FROM the.harvesting_authority hva
        JOIN the.cut_block cb     ON cb.hva_skey = hva.hva_skey
        JOIN the.harvest_amend ha ON ha.cb_skey = cb.cb_skey
       WHERE hva.forest_file_id = :forestFileId
       GROUP BY DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                       hva.cutting_permit_id),
                DECODE(hva.harvest_type_code, 'F', 'Y', 'N'),
                cb.timber_mark, cb.cut_block_id, cb.cb_skey
       ORDER BY cutting_permit_id, cb.cut_block_id, cb.cb_skey
      """;

  /** The block's header — scoped to the tenure through its harvesting authority. */
  private static final String BLOCK_SQL =
      """
      SELECT DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                    hva.cutting_permit_id)                  AS cutting_permit_id,
             DECODE(hva.harvest_type_code, 'F', 'Y', 'N')   AS fsj_ind,
             cb.timber_mark,
             cb.cut_block_id,
             cb.cb_skey,
             cb.block_status_st,
             CASE WHEN bsc.description IS NOT NULL
                  THEN cb.block_status_st || ' - ' || bsc.description
                  ELSE cb.block_status_st END               AS block_status,
             cb.block_status_date,
             NVL(cboa.planned_net_block_area, 0)            AS planned_net_block_area,
             NVL(cboa.planned_gross_block_area, 0)          AS planned_gross_block_area,
             NVL(cboa.disturbance_gross_area, 0)            AS disturbance_gross_area
        FROM the.cut_block cb
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
        JOIN the.harvesting_authority hva  ON hva.hva_skey = cb.hva_skey
        LEFT JOIN the.block_status_code bsc ON bsc.block_status_code = cb.block_status_st
       WHERE cb.cb_skey = :cbSkey
         AND hva.forest_file_id = :forestFileId
      """;

  private static final String AMENDMENTS_SQL =
      """
      SELECT ha.amendment_id,
             NVL(ha.est_net_blk_area, 0)   AS est_net_blk_area,
             NVL(ha.est_gross_blk_area, 0) AS est_gross_blk_area,
             NVL(ha.cruise_volume, 0)      AS cruise_volume,
             ha.amend_applic_date,
             ha.amend_status_st,
             ha.amend_status_date,
             ha.amend_reason_st,
             NVL2(tamf.map_feature_id, 'Y', 'N') AS image_ind,
             tamf.image_mime_type_code,
             tamf.tenure_app_id
        FROM the.harvest_amend ha
        LEFT JOIN (SELECT a.map_feature_id, a.tenure_app_id, a.amendment_id,
                          a.image_mime_type_code, geom.cb_skey
                     FROM the.cut_block_amend_geom geom
                     JOIN the.tenure_application_map_feature a
                       ON a.map_feature_id = geom.map_feature_id) tamf
               ON tamf.cb_skey = ha.cb_skey
              AND tamf.amendment_id = ha.amendment_id
       WHERE ha.cb_skey = :cbSkey
       ORDER BY ha.amendment_id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AmendmentsService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The file's amended cut blocks with their totals; none when the file type is not valid for
   * the screen (with legacy's reason).
   *
   * @throws ResponseStatusException 404 if the file does not exist
   */
  public AmendmentsListDto list(String forestFileId) {
    AmendmentsRules rules = rules(forestFileId);
    if (!rules.available()) {
      return new AmendmentsListDto(false, rules.unavailableReason(), List.of());
    }
    List<AmendmentsBlockRow> rows = jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, rowNum) -> new AmendmentsBlockRow(
            rs.getString("cutting_permit_id"),
            "Y".equals(rs.getString("fsj_ind")),
            rs.getString("timber_mark"),
            rs.getString("cut_block_id"),
            rs.getObject("cb_skey", Long.class),
            rs.getBigDecimal("est_net_blk_area"),
            rs.getBigDecimal("est_gross_blk_area"),
            rs.getBigDecimal("cruise_volume")));
    return new AmendmentsListDto(true, null, rows);
  }

  /**
   * One block's header and amendments.
   *
   * @throws ResponseStatusException 404 if the file or the block (on this file) does not exist,
   *     409 if the file type is not valid for the screen
   */
  public AmendmentsBlockDetailDto block(String forestFileId, long cbSkey) {
    AmendmentsRules rules = rules(forestFileId);
    if (!rules.available()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.unavailableReason());
    }
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("cbSkey", cbSkey, Types.NUMERIC);
    List<AmendmentsAmendmentRow> amendments = jdbc.query(
        AMENDMENTS_SQL,
        params,
        (rs, rowNum) -> new AmendmentsAmendmentRow(
            rs.getObject("amendment_id", Integer.class),
            rs.getBigDecimal("est_net_blk_area"),
            rs.getBigDecimal("est_gross_blk_area"),
            rs.getBigDecimal("cruise_volume"),
            rs.getObject("amend_applic_date", LocalDate.class),
            rs.getString("amend_status_st"),
            rs.getObject("amend_status_date", LocalDate.class),
            rs.getString("amend_reason_st"),
            "Y".equals(rs.getString("image_ind")),
            rs.getString("image_mime_type_code"),
            rs.getObject("tenure_app_id", Long.class)));
    try {
      return jdbc.queryForObject(
          BLOCK_SQL,
          params,
          (rs, rowNum) -> new AmendmentsBlockDetailDto(
              rs.getString("cutting_permit_id"),
              "Y".equals(rs.getString("fsj_ind")),
              rs.getString("timber_mark"),
              rs.getString("cut_block_id"),
              rs.getObject("cb_skey", Long.class),
              rs.getString("block_status_st"),
              rs.getString("block_status"),
              rs.getObject("block_status_date", LocalDate.class),
              rs.getBigDecimal("planned_net_block_area"),
              rs.getBigDecimal("planned_gross_block_area"),
              rs.getBigDecimal("disturbance_gross_area"),
              amendments));
    } catch (EmptyResultDataAccessException e) {
      // legacy: sil.error.usr.invalid.value "Invalid {0}." with "CutBlock ID"
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid CutBlock ID.");
    }
  }

  private AmendmentsRules rules(String forestFileId) {
    try {
      return jdbc.queryForObject(
          FILE_SQL,
          new MapSqlParameterSource("forestFileId", forestFileId),
          (rs, rowNum) -> AmendmentsRules.of(
              rs.getString("file_type_code"),
              rs.getInt("range_count") > 0,
              rs.getInt("recreation_count") > 0));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }
}
