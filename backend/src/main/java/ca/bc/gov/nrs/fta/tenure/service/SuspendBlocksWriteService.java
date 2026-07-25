package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.SuspendBlocksRequest;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for suspending cut blocks on a cutting permit.
 *
 * <p>Ports the {@code ADD} / {@code SUSPEND_ALL} paths of the legacy {@code
 * THE.FTA_912_SUSPEND_PERMIT} package (driven by {@code MAINLINE} with {@code
 * P_ACTION = 'SUSPEND'}). For each suspended block it inserts a row into {@code
 * THE.CUT_BLOCK_SUSPENSION} (keyed off {@code CUT_BLOCK_SUSP_SEQ}, capturing the
 * block's pre-suspension status) and flips the block status to {@code 'HS'} on
 * {@code THE.CUT_BLOCK}, mirroring the package body.
 *
 * <p>The legacy package also performs date validations and rescinds any active
 * cutting-permit postponement (updating {@code CUTTING_PERMIT_POSTPONEMENT} /
 * {@code HARVESTING_AUTHORITY}); that business logic should be reproduced here
 * as the module is hardened. Runs against the shared {@code THE} Oracle schema —
 * there is no local database, so it is exercised only in a deployed environment.
 */
@Service
public class SuspendBlocksWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public SuspendBlocksWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Mirrors the INSERT in FTA_912_SUSPEND_PERMIT.ADD / SUSPEND_ALL: a new
  // suspension row keyed off CUT_BLOCK_SUSP_SEQ, capturing the block's current
  // (pre-suspension) status.
  private static final String INSERT_SUSPENSION_SQL =
      """
      INSERT INTO the.cut_block_suspension (
        cut_block_suspension_id, cb_skey, under_partition_code,
        susp_order_number, susp_start_date, susp_end_date,
        pre_susp_blk_status, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        the.cut_block_susp_seq.nextval, :cbSkey, :partitionCode,
        :suspOrderNumber, :suspStartDate, :suspEndDate,
        (SELECT block_status_st FROM the.cut_block WHERE cb_skey = :cbSkey),
        0, :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  // Mirrors the UPDATE in FTA_912_SUSPEND_PERMIT.ADD / SUSPEND_ALL: set the
  // block status to 'HS' (suspended) and bump its revision.
  private static final String UPDATE_BLOCK_STATUS_SQL =
      """
      UPDATE the.cut_block
         SET block_status_st = 'HS',
             block_status_date = SYSDATE,
             update_timestamp = SYSDATE,
             update_userid = :userId,
             revision_count = revision_count + 1
       WHERE cb_skey = :cbSkey
      """;

  // Resolves the eligible blocks on a permit when suspending all — mirrors the
  // ADD/SUSPEND_ALL cursor selecting blocks in ('HB','PE','PI','PP') that are
  // not already suspended.
  private static final String ELIGIBLE_BLOCKS_SQL =
      """
      SELECT cb.cb_skey
        FROM the.cut_block cb
       WHERE cb.forest_file_id = :forestFileId
         AND cb.cutting_permit_id = :cuttingPermitId
         AND cb.block_status_st IN ('HB','PE','PI','PP')
         AND NOT EXISTS (
               SELECT 1 FROM the.cut_block_suspension cbs
                WHERE cbs.cb_skey = cb.cb_skey)
      """;

  /**
   * Suspend the requested cut blocks on a cutting permit. When {@code
   * suspendAllBlocks} is set, every eligible block on the permit is suspended;
   * otherwise the blocks named by {@link SuspendBlocksRequest#cbSkeys()} are
   * suspended.
   *
   * @param cuttingPermitId the cutting permit id (from the request path)
   * @param request the suspension details
   * @param userId the authenticated user id (audit columns)
   * @return the number of blocks suspended
   */
  @Transactional
  public int suspend(String cuttingPermitId, SuspendBlocksRequest request, String userId) {
    List<String> targets;
    if (request.suspendAllBlocks()) {
      MapSqlParameterSource lookup = new MapSqlParameterSource()
          .addValue("forestFileId", request.forestFileId())
          .addValue("cuttingPermitId", cuttingPermitId);
      targets = jdbc.queryForList(ELIGIBLE_BLOCKS_SQL, lookup, String.class);
    } else {
      targets = request.cbSkeys() != null ? request.cbSkeys() : List.of();
    }

    int suspended = 0;
    for (String cbSkey : targets) {
      MapSqlParameterSource params = new MapSqlParameterSource()
          .addValue("cbSkey", cbSkey)
          .addValue("partitionCode", request.partitionCode())
          .addValue("suspOrderNumber", request.suspOrderNumber())
          .addValue("suspStartDate", request.suspStartDate())
          .addValue("suspEndDate", request.suspEndDate())
          .addValue("userId", userId);
      jdbc.update(INSERT_SUSPENSION_SQL, params);
      jdbc.update(UPDATE_BLOCK_STATUS_SQL, params);
      suspended++;
    }
    return suspended;
  }
}
