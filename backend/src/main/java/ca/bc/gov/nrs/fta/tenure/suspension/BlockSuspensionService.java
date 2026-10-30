package ca.bc.gov.nrs.fta.tenure.suspension;

import ca.bc.gov.nrs.fta.tenure.service.HarvestLegacyCall;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.BlockSuspension;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.Suspension;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionCode;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionCreateRequest;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionDeleteRequest;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionUpdateAction;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.SuspensionUpdateRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * One block's suspension, legacy FTA914. The writes call {@code FTA_914_SUSPEND_BLOCK.mainline}
 * rather than port it: a new suspension also rescinds a postponed permit's postponement, and
 * ending or deleting one can reach every block on the same order.
 *
 * <p>The package takes no revision for a suspension, so the service locks the row and compares
 * the caller's revision first. It also leaves CUT_BLOCK's update user unset when it deletes a
 * suspension, as legacy did.
 */
@Service
public class BlockSuspensionService {

  private static final String PROCEDURE = "fta_914_suspend_block.mainline";

  private static final String BLOCK_SQL =
      """
      SELECT cb.cb_skey,
             cb.forest_file_id,
             cb.cutting_permit_id,
             cb.cut_block_id,
             cb.block_status_st,
             bsc.description AS status_description,
             hva.issue_date,
             hva.expiry_date,
             hva.extend_date
        FROM the.cut_block cb
        JOIN the.block_status_code bsc ON bsc.block_status_code = cb.block_status_st
        LEFT JOIN the.harvesting_authority hva ON hva.hva_skey = cb.hva_skey
       WHERE cb.cb_skey = :cbSkey
      """;

  private static final String SUSPENSION_SQL =
      """
      SELECT cbs.cut_block_suspension_id,
             cbs.under_partition_code,
             cbs.susp_order_number,
             cbs.susp_start_date,
             cbs.susp_end_date,
             cbs.pre_susp_blk_status,
             bsc.description AS pre_status_description,
             cbs.revision_count
        FROM the.cut_block_suspension cbs
        LEFT JOIN the.block_status_code bsc ON bsc.block_status_code = cbs.pre_susp_blk_status
       WHERE cbs.cb_skey = :cbSkey
       ORDER BY cbs.cut_block_suspension_id DESC
      """;

  private static final String LOCK_SQL =
      """
      SELECT revision_count, TO_CHAR(susp_order_number) AS order_number
        FROM the.cut_block_suspension
       WHERE cut_block_suspension_id = :suspensionId
         AND cb_skey = :cbSkey
         FOR UPDATE
      """;

  private static final String POST_STATUS_SQL =
      """
      SELECT block_status_code AS code, description
        FROM the.block_status_code
       WHERE effective_date <= SYSDATE
         AND expiry_date > SYSDATE
         AND block_status_code <> 'HS'
       ORDER BY block_status_code
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final HarvestLegacyCall legacy;
  private final PartitionCodes partitionCodes;

  public BlockSuspensionService(
      NamedParameterJdbcTemplate jdbc, HarvestLegacyCall legacy, PartitionCodes partitionCodes) {
    this.jdbc = jdbc;
    this.legacy = legacy;
    this.partitionCodes = partitionCodes;
  }

  /** The block, its suspension if it has one, and the lists FTA914's form offers. */
  public BlockSuspension find(long cbSkey) {
    Block block = block(cbSkey);
    List<Suspension> suspensions = jdbc.query(
        SUSPENSION_SQL, Map.of("cbSkey", cbSkey), (rs, n) -> suspension(rs, block));
    Suspension suspension = suspensions.isEmpty() ? null : suspensions.get(0);
    boolean has = suspension != null;
    return new BlockSuspension(
        block.cbSkey(),
        block.forestFileId(),
        block.cuttingPermitId(),
        block.cutBlockId(),
        block.status(),
        block.statusDescription(),
        block.issueDate(),
        block.expiryDate(),
        block.extendDate(),
        suspension,
        SuspensionRules.canSuspendBlock(block.status(), has),
        SuspensionRules.canEditSuspension(block.status(), has),
        partitionCodes.current(),
        postStatusCodes());
  }

  /** Suspend Block: {@code mainline} SAVE with no suspension id reaches ADD. */
  @Transactional
  public void suspend(long cbSkey, SuspensionCreateRequest q, String userId) {
    Block block = block(cbSkey);
    if (!SuspensionRules.canSuspendBlock(block.status(), hasSuspension(cbSkey))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This block cannot be suspended in its current status.");
    }
    fieldsCheck(q.partitionCode(), q.orderNumber(), q.startDate(), q.endDate());
    mainline("SAVE", block, null, q.partitionCode(), q.orderNumber(), null, false,
        q.startDate(), q.endDate(), null, block.status(), userId);
  }

  /** Save Suspension, or End Suspension when the request says so. */
  @Transactional
  public void update(long cbSkey, long suspensionId, SuspensionUpdateRequest q, String userId) {
    Block block = block(cbSkey);
    editableCheck(block, cbSkey);
    String currentOrder = lockedOrder(cbSkey, suspensionId, q.revisionCount());
    fieldsCheck(q.partitionCode(), q.orderNumber(), q.startDate(), q.endDate());
    if (q.action() == SuspensionUpdateAction.END) {
      String problem = SuspensionRules.endProblem(
          q.postStatusCode(), q.updateAllBlocks(), q.orderNumber(), currentOrder);
      if (problem != null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
      }
      if (postStatusCodes().stream().noneMatch(c -> c.code().equals(q.postStatusCode()))) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Post Suspension Status is not a current status.");
      }
    }
    String action = q.action() == SuspensionUpdateAction.END ? "END_SUSPENSION" : "SAVE";
    mainline(action, block, suspensionId, q.partitionCode(), q.orderNumber(), currentOrder,
        q.updateAllBlocks(), q.startDate(), q.endDate(), q.postStatusCode(), null, userId);
  }

  /** Delete Suspension: restores the status the block had before it. */
  @Transactional
  public void delete(long cbSkey, long suspensionId, SuspensionDeleteRequest q, String userId) {
    Block block = block(cbSkey);
    editableCheck(block, cbSkey);
    Long revision = q == null ? null : q.revisionCount();
    String currentOrder = lockedOrder(cbSkey, suspensionId, revision);
    mainline("DELETE_SUSPENSION", block, suspensionId, null, currentOrder, currentOrder,
        q.updateAllBlocks(), null, null, null, null, userId);
  }

  private void fieldsCheck(String partition, String order, LocalDate start, LocalDate end) {
    String problem = SuspensionRules.fieldsProblem(partition, order, start, end);
    if (problem == null && !partitionCodes.isCurrent(partition)) {
      problem = "Suspension Partition Code is not a current code.";
    }
    if (problem != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
    }
  }

  private void editableCheck(Block block, long cbSkey) {
    if (!SuspensionRules.canEditSuspension(block.status(), hasSuspension(cbSkey))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "A suspension can only be changed while the block is suspended.");
    }
  }

  /** Locks the suspension for this transaction and returns its current order number. */
  private String lockedOrder(long cbSkey, long suspensionId, Long revision) {
    if (revision == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision count is required.");
    }
    List<Map<String, Object>> rows = jdbc.queryForList(
        LOCK_SQL, Map.of("suspensionId", suspensionId, "cbSkey", cbSkey));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Suspension not found on this block.");
    }
    if (((Number) rows.get(0).get("revision_count")).longValue() != revision) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "The record was changed by someone else. Reload the page and try again.");
    }
    return (String) rows.get(0).get("order_number");
  }

  private void mainline(
      String action,
      Block block,
      Long suspensionId,
      String partitionCode,
      String orderNumber,
      String previousOrderNumber,
      boolean updateAllBlocks,
      LocalDate start,
      LocalDate end,
      String postStatus,
      String preStatus,
      String userId) {
    legacy.run(PROCEDURE, null,
        action,
        Long.toString(block.cbSkey()),
        block.forestFileId(),
        block.cuttingPermitId(),
        block.cutBlockId(),
        suspensionId == null ? null : suspensionId.toString(),
        partitionCode,
        orderNumber == null ? null : orderNumber.trim(),
        previousOrderNumber,
        updateAllBlocks ? "Y" : null,
        start == null ? null : start.toString(),
        end == null ? null : end.toString(),
        postStatus,
        preStatus,
        null, // p_months_remaining
        null, // p_cp_issue_date
        null, // p_cp_expiry_date
        null, // p_cp_extension_date
        userId);
  }

  private boolean hasSuspension(long cbSkey) {
    return !jdbc.queryForList(SUSPENSION_SQL, Map.of("cbSkey", cbSkey)).isEmpty();
  }

  private List<SuspensionCode> postStatusCodes() {
    return jdbc.query(POST_STATUS_SQL, (rs, n) ->
        new SuspensionCode(rs.getString("code"), rs.getString("description")));
  }

  private Block block(long cbSkey) {
    return jdbc.query(BLOCK_SQL, Map.of("cbSkey", cbSkey), (rs, n) -> new Block(
            rs.getLong("cb_skey"),
            rs.getString("forest_file_id"),
            rs.getString("cutting_permit_id"),
            rs.getString("cut_block_id"),
            rs.getString("block_status_st"),
            rs.getString("status_description"),
            rs.getObject("issue_date", LocalDate.class),
            rs.getObject("expiry_date", LocalDate.class),
            rs.getObject("extend_date", LocalDate.class)))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Cut block not found."));
  }

  private static Suspension suspension(ResultSet rs, Block block) throws SQLException {
    LocalDate start = rs.getObject("susp_start_date", LocalDate.class);
    return new Suspension(
        rs.getLong("cut_block_suspension_id"),
        rs.getString("under_partition_code"),
        rs.getString("susp_order_number"),
        start,
        rs.getObject("susp_end_date", LocalDate.class),
        rs.getString("pre_susp_blk_status"),
        rs.getString("pre_status_description"),
        SuspensionRules.monthsRemaining(start, block.expiryDate(), block.extendDate()),
        rs.getLong("revision_count"));
  }

  private record Block(
      long cbSkey,
      String forestFileId,
      String cuttingPermitId,
      String cutBlockId,
      String status,
      String statusDescription,
      LocalDate issueDate,
      LocalDate expiryDate,
      LocalDate extendDate) {}
}
