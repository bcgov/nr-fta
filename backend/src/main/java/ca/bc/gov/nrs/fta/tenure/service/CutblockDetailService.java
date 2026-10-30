package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.tenure.dto.CutBlockSuspensionDto;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockDetailDto;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksCodeOption;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cut Block detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_904_CUTBLKDETAIL} (FTA904
 * Cut Block Detail) to a native query against the shared {@code THE} schema.
 * A block's label repeats across tenures and permits, so a lookup first
 * resolves the file, permit and label to one {@code CB_SKEY} and refuses an
 * ambiguous one; the detail then reads that key alone. The harvesting authority
 * and map feature are resolved to single rows first, so neither can multiply
 * the block, and a block with no {@code CUT_BLOCK_OPEN_ADMIN} row still reads.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}); there is no
 * local database, so it is exercised only in a deployed environment.
 */
@Service
public class CutblockDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public CutblockDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String RESOLVE_SQL =
      """
      SELECT cb.cb_skey
        FROM the.cut_block cb
        LEFT JOIN the.harvesting_authority hva ON hva.hva_skey = cb.hva_skey
       WHERE cb.cut_block_id = :blockId
         AND cb.forest_file_id = :forestFileId
         AND (:cuttingPermitId IS NULL
              OR cb.cutting_permit_id = :cuttingPermitId
              OR hva.harvesting_authority_id = :cuttingPermitId)
      """;

  private static final String DETAIL_SQL =
      """
      SELECT cb.cb_skey                    AS cb_skey,
             cb.eff_hva_skey               AS hva_skey,
             cb.revision_count             AS cb_revision_count,
             cboa.revision_count           AS cboa_revision_count,
             pfu.file_type_code            AS file_type_code,
             pfu.file_status_st            AS file_status_code,
             cb.forest_file_id             AS forest_file_id,
             NVL(cb.cutting_permit_id, hva.harvesting_authority_id) AS cutting_permit_id,
             cb.cut_block_id               AS cut_block_id,
             cb.timber_mark                AS timber_mark,
             ou.org_unit_code || ' - ' || ou.org_unit_name AS forest_district,
             hva.harvest_auth_status_code  AS mark_status_code,
             hva.harvest_auth_status_code
               || DECODE(hva.harvest_auth_status_code, NULL, NULL, ' - ')
               || sts.description          AS mark_status,
             hva.issue_date                AS mark_issue_date,
             NVL(hva.extend_date, hva.expiry_date) AS mark_expiry_date,
             hva.retirement_date           AS retirement_date,
             TO_CHAR(TRUNC(hva.tenure_term / 12), '999') || ' yr '
               || TO_CHAR(MOD(hva.tenure_term, 12)) || ' mo' AS mark_term,
             cb.block_status_st            AS block_status,
             bsc.description               AS block_status_description,
             cb.block_status_date          AS block_status_date,
             cb.cut_block_description      AS cut_block_description,
             cb.sp_exempt_ind              AS sp_exempt_ind,
             cboa.planned_gross_block_area AS planned_gross_block_area,
             cboa.planned_net_block_area   AS planned_net_block_area,
             cboa.disturbance_gross_area   AS disturbance_gross_area,
             cboa.disturbance_start_date   AS disturbance_start_date,
             cboa.disturbance_end_date     AS disturbance_end_date,
             cboa.planned_harvest_date     AS planned_harvest_date,
             LPAD(o.mapsheet_grid, 3) || o.mapsheet_letter || ' '
               || LPAD(o.mapsheet_square, 3, 0) || ' '
               || DECODE(o.mapsheet_quad, NULL, NULL, o.mapsheet_quad || '.' || o.mapsheet_sub_quad) || ' '
               || LPAD(o.opening_number, 4) AS opening,
             cboa.opening_id               AS opening_id,
             cb.eff_map_feature_id         AS map_feature_id,
             SUBSTR(NVL(tamf.reference_name, ta.description), 1, 50) AS reference_name,
             hva.salvage_type_code         AS salvage_type_code,
             cb.cut_regulation_code        AS cut_regulation_code,
             cb.reforest_declare_type_code AS reforest_declare_type_code,
             hva.harvest_type_code         AS harvest_type_code,
             ta.decision_date              AS decision_date,
             ta.issuance_date              AS issuance_date,
             cb.fire_harvesting_reason_code AS fire_harvesting_reason_code,
             cb.is_under_partition_order   AS under_partition_order,
             cb.reported_fire_date         AS reported_fire_date,
             cb.is_waste_assessment_required AS waste_assessment_required,
             (SELECT MAX(waa.waste_assess_area_sts_code || ' - ' || waasc.description)
                     KEEP (DENSE_RANK LAST ORDER BY waa.update_timestamp)
                FROM the.waste_assessment_area waa
                JOIN the.waste_assess_area_sts_code waasc
                  ON waasc.waste_assess_area_sts_code = waa.waste_assess_area_sts_code
               WHERE waa.forest_file_id = cb.forest_file_id
                 AND waa.timber_mark = cb.timber_mark
                 AND waa.cut_block_id = cb.cut_block_id) AS waste_description,
             (SELECT SUM(cboa2.disturbance_gross_area)
                FROM the.cut_block cb2
                JOIN the.cut_block_open_admin cboa2 ON cboa2.cb_skey = cb2.cb_skey
                JOIN the.harvesting_hauling_xref x2
                  ON x2.timber_mark = cb2.timber_mark AND x2.hva_skey = cb.eff_hva_skey
               WHERE (cb2.hva_skey = cb.eff_hva_skey OR cb2.hva_skey IS NULL)
                 AND cb2.block_status_st != 'EE') AS total_logged_area
        FROM (SELECT b.*,
                     NVL(b.hva_skey,
                         (SELECT MIN(xref.hva_skey)
                            FROM the.harvesting_hauling_xref xref
                           WHERE xref.timber_mark = b.timber_mark)) AS eff_hva_skey,
                     (SELECT MAX(g.map_feature_id)
                        FROM the.cut_block_geom g
                       WHERE g.cb_skey = b.cb_skey) AS eff_map_feature_id
                FROM the.cut_block b
               WHERE b.cb_skey = :cbSkey) cb
        JOIN the.prov_forest_use pfu ON pfu.forest_file_id = cb.forest_file_id
        LEFT JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
        LEFT JOIN the.harvesting_authority hva ON hva.hva_skey = cb.eff_hva_skey
        LEFT JOIN the.harvest_auth_status_code sts
               ON sts.harvest_auth_status_code = hva.harvest_auth_status_code
        LEFT JOIN the.block_status_code bsc ON bsc.block_status_code = cb.block_status_st
        LEFT JOIN the.opening o ON o.opening_id = cboa.opening_id
        LEFT JOIN the.tenure_application_map_feature tamf
               ON tamf.map_feature_id = cb.eff_map_feature_id
        LEFT JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = hva.forest_district
      """;

  private static final String SUSPENSIONS_SQL =
      """
      SELECT cbs.cut_block_suspension_id,
             cbs.under_partition_code || ' - ' || upc.description AS under_partition,
             cbs.susp_order_number,
             cbs.susp_start_date,
             cbs.susp_end_date
        FROM the.cut_block_suspension cbs
        LEFT JOIN the.under_partition_code upc
               ON upc.under_partition_code = cbs.under_partition_code
       WHERE cbs.cb_skey = :cbSkey
       ORDER BY cbs.susp_start_date DESC
      """;

  private static final String CUT_REGULATION_SQL =
      """
      SELECT cut_regulation_code AS code,
             cut_regulation_code || ' - ' || description AS description
        FROM the.cut_regulation_code
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY cut_regulation_code
      """;

  private static final String REFOREST_DECLARE_SQL =
      """
      SELECT reforest_declare_type_code AS code,
             reforest_declare_type_code || ' - ' || description AS description
        FROM the.reforest_declare_type_code
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY reforest_declare_type_code
      """;

  /**
   * The one block with this label on the file — and permit, when given.
   *
   * @param blockId         the cut block label (path key)
   * @param forestFileId    the block's forest file
   * @param cuttingPermitId the block's cutting permit or FSJ authority id, or null
   * @return the block, or empty if none
   * @throws ResponseStatusException 409 when the label repeats across the file's permits and no
   *     permit was given
   */
  public Optional<CutblockDetailDto> find(
      String blockId, String forestFileId, String cuttingPermitId, Authentication authentication) {
    List<Long> keys = jdbc.queryForList(
        RESOLVE_SQL,
        new MapSqlParameterSource()
            .addValue("blockId", blockId)
            .addValue("forestFileId", forestFileId)
            .addValue("cuttingPermitId", blankToNull(cuttingPermitId), Types.VARCHAR),
        Long.class);
    if (keys.size() > 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Cut block " + blockId + " exists on more than one permit of this file."
              + " Open it from its permit.");
    }
    return keys.isEmpty() ? Optional.empty() : find(keys.get(0), authentication);
  }

  /** The block with this key, with what the caller may do with it. */
  public Optional<CutblockDetailDto> find(long cbSkey, Authentication authentication) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("cbSkey", cbSkey, Types.NUMERIC);

    List<CutBlockSuspensionDto> suspensions = jdbc.query(SUSPENSIONS_SQL, params, (rs, rowNum) ->
        new CutBlockSuspensionDto(
            rs.getObject("cut_block_suspension_id", Long.class),
            rs.getString("under_partition"),
            rs.getString("susp_order_number"),
            rs.getObject("susp_start_date", LocalDate.class),
            rs.getObject("susp_end_date", LocalDate.class)));

    List<CutblockDetailDto> rows = jdbc.query(DETAIL_SQL, params, (rs, rowNum) -> {
      CutBlockRules.Facts facts = new CutBlockRules.Facts(
          rs.getString("file_type_code"),
          rs.getString("file_status_code"),
          rs.getString("mark_status_code"),
          rs.getString("block_status"),
          rs.getObject("cboa_revision_count") != null,
          rs.getObject("map_feature_id") != null,
          rs.getObject("opening_id") != null,
          !suspensions.isEmpty(),
          rs.getObject("retirement_date") != null);
      return new CutblockDetailDto(
          rs.getObject("cb_skey", Long.class),
          rs.getObject("hva_skey", Long.class),
          rs.getLong("cb_revision_count"),
          rs.getObject("cboa_revision_count", Long.class),
          rs.getString("file_type_code"),
          rs.getString("forest_file_id"),
          rs.getString("cutting_permit_id"),
          rs.getString("cut_block_id"),
          rs.getString("timber_mark"),
          rs.getString("forest_district"),
          rs.getString("mark_status_code"),
          rs.getString("mark_status"),
          rs.getObject("mark_issue_date", LocalDate.class),
          rs.getObject("mark_expiry_date", LocalDate.class),
          rs.getString("mark_term"),
          rs.getString("block_status"),
          rs.getString("block_status_description"),
          rs.getObject("block_status_date", LocalDate.class),
          rs.getString("cut_block_description"),
          rs.getString("sp_exempt_ind"),
          getDouble(rs, "planned_gross_block_area"),
          getDouble(rs, "planned_net_block_area"),
          getDouble(rs, "disturbance_gross_area"),
          rs.getObject("disturbance_start_date", LocalDate.class),
          rs.getObject("disturbance_end_date", LocalDate.class),
          rs.getObject("planned_harvest_date", LocalDate.class),
          rs.getString("opening"),
          rs.getObject("opening_id", Long.class),
          rs.getString("reference_name"),
          rs.getString("salvage_type_code"),
          rs.getString("cut_regulation_code"),
          rs.getString("reforest_declare_type_code"),
          rs.getString("harvest_type_code"),
          rs.getObject("decision_date", LocalDate.class),
          rs.getObject("issuance_date", LocalDate.class),
          rs.getString("fire_harvesting_reason_code"),
          rs.getString("under_partition_order"),
          rs.getObject("reported_fire_date", LocalDate.class),
          rs.getString("waste_assessment_required"),
          rs.getString("waste_description"),
          getDouble(rs, "total_logged_area"),
          suspensions,
          CutBlockRules.of(
              facts, HarvestAccess.canEdit(authentication), HarvestAccess.isSenior(authentication)));
    });

    return rows.stream().findFirst();
  }

  /** The Cut Management list on the edit dialog. */
  public List<CutBlocksCodeOption> cutRegulationCodes() {
    return codes(CUT_REGULATION_SQL);
  }

  /** The Reforest Declare Type list on the edit dialog. */
  public List<CutBlocksCodeOption> reforestDeclareTypes() {
    return codes(REFOREST_DECLARE_SQL);
  }

  private List<CutBlocksCodeOption> codes(String sql) {
    return jdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) ->
        new CutBlocksCodeOption(rs.getString("code"), rs.getString("description")));
  }

  private static Double getDouble(ResultSet rs, String column) throws SQLException {
    BigDecimal value = rs.getBigDecimal(column);
    return value == null ? null : value.doubleValue();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
