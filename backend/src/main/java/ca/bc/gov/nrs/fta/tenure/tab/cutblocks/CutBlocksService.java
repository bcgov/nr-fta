package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockPermitOption;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockRow;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockSuspension;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksCodeOption;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlocksResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Cut block tab's read — legacy FTA903 Cut Block List.
 *
 * <ul>
 *   <li>The blocks: {@code FTA_903_CUTBLK_LST.GET}'s five-way UNION, ported to ANSI joins
 *       with no CP or block filter (every CP of the file; the tab filters by CP itself). The
 *       map-extent columns (and the joins to {@code CUT_BLOCK_GEOM} /
 *       {@code TENURE_APPLICATION_MAP_FEATURE} that only fed them) are dropped with the Map
 *       View button. The block status is resolved to "code - description".
 *   <li>The suspensions: {@code FTA_903_CB_SUSP_LIST.GET}, which the screen lists above them.
 *   <li>The permits a block may be added to: {@code FTA903_ADD_NEW}'s verdict per permit.
 * </ul>
 *
 * <p>Runs against the shared {@code THE} schema; there is no local database.
 */
@Service
public class CutBlocksService {

  /** The planned/actual areas, formatted as legacy formats them. */
  private static final String AREAS =
      """
             TO_CHAR(cboa.planned_gross_block_area, 'FM9999990.0000') AS planned_gross,
             TO_CHAR(cboa.planned_net_block_area, 'FM9999990.0000')   AS planned_net,
             TO_CHAR(cboa.disturbance_gross_area, 'FM9999990.0000')   AS actual_gross,
      """;

  private static final String BLOCK_KEYS =
      """
             cb.cb_skey,
             cb.forest_file_id    AS cb_forest_file_id,
             cb.cutting_permit_id AS cb_cutting_permit_id,
             cb.revision_count    AS cb_revision_count
      """;

  /** {@code DECODE(NVL(hva.retirement_date, SYSDATE), hva.retirement_date, 'Y', 'N')}. */
  private static final String RETIRED =
      "CASE WHEN hva.retirement_date IS NOT NULL THEN 'Y' ELSE 'N' END AS retired_permit_ind,";

  static final String LIST_SQL =
      """
      SELECT u.*,
             u.block_status_st
               || CASE WHEN bsc.description IS NOT NULL THEN ' - ' || bsc.description END
               AS block_status
        FROM (
      -- 1. blocks owned by the file
      SELECT DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                    cb.cutting_permit_id)   AS cutting_permit_id,
             cb.timber_mark,
             hva.salvage_type_code,
             hva.harvest_auth_status_code   AS mark_status,
             cb.cut_block_id,
             cb.block_status_st,
             cboa.disturbance_start_date    AS start_date,
             cboa.disturbance_end_date      AS end_date,
      """ + AREAS + """
             CAST(NULL AS VARCHAR2(10))     AS authorized_file_id,
             CAST(NULL AS VARCHAR2(10))     AS authorized_cp_id,
      """ + RETIRED + BLOCK_KEYS + """
        FROM the.harvesting_authority hva
        JOIN the.prov_forest_use pfu ON pfu.forest_file_id = hva.forest_file_id
        LEFT JOIN the.licence_to_cut ltc ON ltc.forest_file_id = pfu.forest_file_id
        JOIN the.cut_block cb
          ON cb.hva_skey = hva.hva_skey AND cb.forest_file_id = hva.forest_file_id
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
       WHERE hva.forest_file_id = :forestFileId
         AND pfu.file_type_code NOT IN ('B08', 'B09', 'B14')
         AND NOT (pfu.file_type_code = 'B07' AND pfu.bcts_org_unit IS NOT NULL
                  AND ltc.licence_to_cut_cd = 'DT')
      UNION
      -- 2. blocks of an A06 the file's permits manage
      SELECT DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                    hva.cutting_permit_id),
             cb.timber_mark,
             hva.salvage_type_code,
             hva.harvest_auth_status_code,
             cb.cut_block_id,
             cb.block_status_st,
             cboa.disturbance_start_date,
             cboa.disturbance_end_date,
             TO_CHAR(cboa.planned_gross_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.planned_net_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.disturbance_gross_area, 'FM9999990.0000'),
             cb.forest_file_id,
             cb.cutting_permit_id,
             CASE WHEN hva.retirement_date IS NOT NULL THEN 'Y' ELSE 'N' END,
             cb.cb_skey, cb.forest_file_id, cb.cutting_permit_id, cb.revision_count
        FROM the.harvesting_authority hva
        JOIN the.cut_block cb
          ON cb.hva_skey = hva.hva_skey
         AND hva.cutting_permit_id <> cb.cutting_permit_id
         AND hva.forest_file_id <> cb.forest_file_id
        JOIN the.prov_forest_use pfu
          ON pfu.forest_file_id = cb.forest_file_id AND pfu.file_type_code = 'A06'
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
       WHERE hva.forest_file_id = :forestFileId
      UNION
      -- 3. private mark blocks
      SELECT hva.cutting_permit_id,
             cb.timber_mark,
             NULL,
             pmc.private_mark_status_code,
             cb.cut_block_id,
             cb.block_status_st,
             cboa.disturbance_start_date,
             cboa.disturbance_end_date,
             TO_CHAR(cboa.planned_gross_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.planned_net_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.disturbance_gross_area, 'FM9999990.0000'),
             cb.forest_file_id,
             NULL,
             NULL,
             cb.cb_skey, cb.forest_file_id, cb.cutting_permit_id, cb.revision_count
        FROM the.harvesting_authority hva
        JOIN the.cut_block cb ON cb.hva_skey = hva.hva_skey
        JOIN the.hauling_authority ha ON ha.forest_file_id = cb.forest_file_id
        JOIN the.prov_forest_use pfu
          ON pfu.forest_file_id = ha.forest_file_id
         AND pfu.file_type_code IN ('B08', 'B09', 'B14')
        JOIN the.private_mark_certificate pmc ON pmc.forest_file_id = pfu.forest_file_id
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
       WHERE hva.forest_file_id = :forestFileId
      UNION
      -- 4. an A06's blocks under another (major) file's permits
      SELECT cb.cutting_permit_id,
             cb.timber_mark,
             hva.salvage_type_code,
             hva.harvest_auth_status_code,
             cb.cut_block_id,
             cb.block_status_st,
             cboa.disturbance_start_date,
             cboa.disturbance_end_date,
             TO_CHAR(cboa.planned_gross_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.planned_net_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.disturbance_gross_area, 'FM9999990.0000'),
             hva.forest_file_id,
             DECODE(hva.cutting_permit_id, NULL, hva.harvesting_authority_id,
                    hva.cutting_permit_id),
             CASE WHEN hva.retirement_date IS NOT NULL THEN 'Y' ELSE 'N' END,
             cb.cb_skey, cb.forest_file_id, cb.cutting_permit_id, cb.revision_count
        FROM the.prov_forest_use pfu
        JOIN the.cut_block cb ON cb.forest_file_id = pfu.forest_file_id
        JOIN the.harvesting_authority hva
          ON hva.hva_skey = cb.hva_skey AND hva.forest_file_id <> pfu.forest_file_id
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
       WHERE pfu.forest_file_id = :forestFileId
         AND pfu.file_type_code = 'A06'
      UNION
      -- 5. a BCTS B07 of purpose DT, whose blocks have no mark
      SELECT CAST(NULL AS VARCHAR2(10)),
             'ORG',
             hva.salvage_type_code,
             NULL,
             cb.cut_block_id,
             cb.block_status_st,
             cboa.disturbance_start_date,
             cboa.disturbance_end_date,
             TO_CHAR(cboa.planned_gross_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.planned_net_block_area, 'FM9999990.0000'),
             TO_CHAR(cboa.disturbance_gross_area, 'FM9999990.0000'),
             NULL,
             NULL,
             CASE WHEN hva.retirement_date IS NOT NULL THEN 'Y' ELSE 'N' END,
             cb.cb_skey, cb.forest_file_id, cb.cutting_permit_id, cb.revision_count
        FROM the.prov_forest_use pfu
        JOIN the.harvesting_authority hva ON hva.forest_file_id = pfu.forest_file_id
        JOIN the.licence_to_cut ltc
          ON ltc.forest_file_id = hva.forest_file_id AND ltc.licence_to_cut_cd = 'DT'
        JOIN the.cut_block cb ON cb.forest_file_id = pfu.forest_file_id
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
       WHERE pfu.forest_file_id = :forestFileId
         AND pfu.file_type_code = 'B07'
         AND pfu.bcts_org_unit IS NOT NULL
             ) u
        LEFT JOIN the.block_status_code bsc ON bsc.block_status_code = u.block_status_st
       ORDER BY u.cutting_permit_id NULLS FIRST, u.cut_block_id, u.cb_skey
      """;

  /** {@code FTA_903_CB_SUSP_LIST.GET} with no CP or block filter. */
  private static final String SUSPENSIONS_SQL =
      """
      SELECT cb.cutting_permit_id,
             cb.cut_block_id,
             cbs.susp_order_number,
             cbs.under_partition_code || ' - ' || upc.description AS under_partition,
             cbs.susp_start_date,
             cbs.susp_end_date
        FROM the.cut_block cb
        JOIN the.cut_block_suspension cbs ON cbs.cb_skey = cb.cb_skey
        JOIN the.under_partition_code upc
          ON upc.under_partition_code = cbs.under_partition_code
       WHERE cb.forest_file_id = :forestFileId
         AND cb.cutting_permit_id IS NOT NULL
       ORDER BY cb.cutting_permit_id, cb.cut_block_id, cbs.susp_start_date
      """;

  /** The tenure, as the tab's rules need it ({@code FTA_GET_FILE_HEADER}'s part). */
  private static final String TENURE_SQL =
      """
      SELECT pfu.file_type_code,
             pfu.file_status_st,
             ltc.licence_to_cut_cd,
             (SELECT COUNT(*) FROM the.range_file_type_code r
               WHERE r.range_file_type_code = pfu.file_type_code)
             + (SELECT COUNT(*) FROM the.recreation_file_type_code r
               WHERE r.recreation_file_type_code = pfu.file_type_code) AS range_rec
        FROM the.prov_forest_use pfu
        LEFT JOIN the.licence_to_cut ltc ON ltc.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :forestFileId
      """;

  /** The file's harvesting authorities, with the primary mark FTA904 puts a new block under. */
  private static final String PERMITS_SQL =
      """
      SELECT hva.hva_skey,
             hva.cutting_permit_id,
             hva.harvesting_authority_id,
             hva.harvest_auth_status_code,
             hva.salvage_type_code,
             (SELECT MAX(x.timber_mark) FROM the.harvesting_hauling_xref x
               WHERE x.hva_skey = hva.hva_skey AND x.primary_mark_ind = 'Y') AS primary_mark
        FROM the.harvesting_authority hva
       WHERE hva.forest_file_id = :forestFileId
       ORDER BY hva.cutting_permit_id NULLS FIRST, hva.hva_skey
      """;

  private static final String FIRE_REASONS_SQL =
      """
      SELECT fire_harvesting_reason_code AS code,
             fire_harvesting_reason_code || ' - ' || description AS description
        FROM the.fire_harvesting_reason_code
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY fire_harvesting_reason_code
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public CutBlocksService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The tenure facts the rules and writes need. */
  record Tenure(
      String fileTypeCode, String fileStatusCode, String licenceToCutCode,
      boolean rangeOrRecreation) {}

  /** @throws ResponseStatusException 404 when the tenure does not exist */
  Tenure tenure(String forestFileId) {
    try {
      return jdbc.queryForObject(TENURE_SQL, Map.of("forestFileId", forestFileId),
          (rs, i) -> new Tenure(
              rs.getString("file_type_code"),
              rs.getString("file_status_st"),
              rs.getString("licence_to_cut_cd"),
              rs.getInt("range_rec") > 0));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }

  /** A permit with its verdict, plus the true CP id an add writes. */
  record Permit(CutBlockPermitOption option, String cpId) {}

  List<Permit> permits(String forestFileId, Tenure t) {
    return jdbc.query(PERMITS_SQL, Map.of("forestFileId", forestFileId), (rs, i) -> {
      String cp = rs.getString("cutting_permit_id");
      String hvaId = rs.getString("harvesting_authority_id");
      String status = rs.getString("harvest_auth_status_code");
      String salvage = rs.getString("salvage_type_code");
      String mark = rs.getString("primary_mark");
      String problem = CutBlocksRules.permitProblem(
          t.fileTypeCode(), t.licenceToCutCode(), status, salvage, mark != null);
      return new Permit(
          new CutBlockPermitOption(
              rs.getLong("hva_skey"),
              cp != null && !cp.isBlank() ? cp.trim() : hvaId,
              mark, status, salvage, problem == null, problem),
          cp);
    });
  }

  CutBlocksRules rules(Tenure t, List<Permit> permits) {
    return CutBlocksRules.of(
        t.fileTypeCode(), t.fileStatusCode(), t.rangeOrRecreation(),
        permits.stream().map(Permit::option).toList());
  }

  /** {@code GET /api/fta/tenures/{forestFileId}/cut-blocks}. */
  public CutBlocksResponse tab(String forestFileId) {
    Tenure t = tenure(forestFileId);
    List<Permit> permits = permits(forestFileId, t);
    CutBlocksRules rules = rules(t, permits);
    if (!rules.listable()) {
      // Legacy warns and runs no GET for these file types.
      return new CutBlocksResponse(rules, List.of(), List.of(), List.of());
    }
    return new CutBlocksResponse(
        rules,
        blocks(forestFileId),
        jdbc.query(SUSPENSIONS_SQL, Map.of("forestFileId", forestFileId), (rs, i) ->
            new CutBlockSuspension(
                trim(rs.getString("cutting_permit_id")),
                rs.getString("cut_block_id"),
                rs.getString("susp_order_number"),
                rs.getString("under_partition"),
                rs.getObject("susp_start_date", LocalDate.class),
                rs.getObject("susp_end_date", LocalDate.class))),
        permits.stream().map(Permit::option).toList());
  }

  List<CutBlockRow> blocks(String forestFileId) {
    return jdbc.query(LIST_SQL, Map.of("forestFileId", forestFileId), (rs, i) -> {
      String blockStatus = rs.getString("block_status_st");
      String problem =
          CutBlocksRules.deleteProblem(blockStatus, rs.getString("retired_permit_ind"));
      return new CutBlockRow(
          rs.getLong("cb_skey"),
          trim(rs.getString("cutting_permit_id")),
          rs.getString("timber_mark"),
          rs.getString("salvage_type_code"),
          rs.getString("mark_status"),
          rs.getString("cut_block_id"),
          blockStatus,
          rs.getString("block_status"),
          rs.getObject("start_date", LocalDate.class),
          rs.getObject("end_date", LocalDate.class),
          rs.getString("planned_gross"),
          rs.getString("planned_net"),
          rs.getString("actual_gross"),
          rs.getString("authorized_file_id"),
          trim(rs.getString("authorized_cp_id")),
          rs.getString("cb_forest_file_id"),
          trim(rs.getString("cb_cutting_permit_id")),
          rs.getLong("cb_revision_count"),
          problem == null,
          problem);
    });
  }

  /** {@code GET /api/fta/tenure-lookups/fire-harvesting-reasons} — FTA904's list. */
  public List<CutBlocksCodeOption> fireHarvestingReasons() {
    return jdbc.query(FIRE_REASONS_SQL, Map.of(),
        (rs, i) -> new CutBlocksCodeOption(rs.getString("code"), rs.getString("description")));
  }

  private static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
