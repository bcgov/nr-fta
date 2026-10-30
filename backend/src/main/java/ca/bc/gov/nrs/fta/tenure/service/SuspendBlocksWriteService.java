package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.SuspendBlocksRequest;
import ca.bc.gov.nrs.fta.tenure.suspension.PartitionCodes;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.PermitBlock;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionDtos.PermitSuspension;
import ca.bc.gov.nrs.fta.tenure.suspension.SuspensionRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suspending several of a permit's cut blocks, legacy FTA912. The write calls
 * {@code FTA_912_SUSPEND_PERMIT.mainline} rather than port it: it also rescinds a postponed
 * permit's postponement and suspends the permit. The package trusts the blocks it is given, so
 * they are checked against this file and permit first.
 */
@Service
public class SuspendBlocksWriteService {

  private static final String PROCEDURE = "fta_912_suspend_permit.mainline";
  private static final String CURSOR_TYPE = "the.fta_912_suspend_permit.ref_blocks_to_suspend";

  private static final String PERMIT_SQL =
      """
      SELECT issue_date, expiry_date, extend_date
        FROM the.harvesting_authority
       WHERE forest_file_id = :forestFileId
         AND cutting_permit_id = :cuttingPermitId
      """;

  // FTA_912_SUSPEND_PERMIT.GET_BLOCK_LIST: its MINUS, written as a NOT EXISTS.
  private static final String BLOCKS_SQL =
      """
      SELECT cb.cb_skey,
             cb.cut_block_id,
             cb.block_status_st,
             bsc.description AS status_description,
             cboa.planned_harvest_date,
             cboa.planned_gross_block_area,
             cboa.planned_net_block_area,
             CASE WHEN EXISTS (
                    SELECT 1 FROM the.cut_block_suspension cbs WHERE cbs.cb_skey = cb.cb_skey)
                  THEN 'Y' ELSE 'N' END AS has_suspension
        FROM the.cut_block cb
        JOIN the.cut_block_open_admin cboa ON cboa.cb_skey = cb.cb_skey
        JOIN the.block_status_code bsc ON bsc.block_status_code = cb.block_status_st
       WHERE cb.forest_file_id = :forestFileId
         AND cb.cutting_permit_id = :cuttingPermitId
         AND cb.block_status_st IN ('HB','PE','PI','PP','HS')
         AND (cb.block_status_st = 'HS'
              OR NOT EXISTS (
                   SELECT 1 FROM the.cut_block_suspension cbs WHERE cbs.cb_skey = cb.cb_skey))
       ORDER BY cb.cut_block_id
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final HarvestLegacyCall legacy;
  private final PartitionCodes partitionCodes;

  public SuspendBlocksWriteService(
      NamedParameterJdbcTemplate jdbc, HarvestLegacyCall legacy, PartitionCodes partitionCodes) {
    this.jdbc = jdbc;
    this.legacy = legacy;
    this.partitionCodes = partitionCodes;
  }

  /** The permit's term, the blocks FTA912 lists and the partition codes on offer. */
  public PermitSuspension find(String cuttingPermitId, String forestFileId) {
    Map<String, String> key = key(cuttingPermitId, forestFileId);
    List<LocalDate[]> permits = jdbc.query(PERMIT_SQL, key, (rs, n) -> new LocalDate[] {
        rs.getObject("issue_date", LocalDate.class),
        rs.getObject("expiry_date", LocalDate.class),
        rs.getObject("extend_date", LocalDate.class)});
    if (permits.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cutting permit not found.");
    }
    LocalDate[] term = permits.get(0);
    return new PermitSuspension(
        forestFileId,
        cuttingPermitId,
        term[0],
        term[1],
        term[2],
        blocks(key),
        partitionCodes.current());
  }

  /**
   * Suspends the requested blocks, or every eligible block when {@code suspendAllBlocks} is set.
   *
   * @return the number of blocks suspended
   */
  @Transactional
  public int suspend(String cuttingPermitId, SuspendBlocksRequest q, String userId) {
    if (q.forestFileId() == null || q.forestFileId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forest file is required.");
    }
    PermitSuspension permit = find(cuttingPermitId, q.forestFileId());
    String problem = SuspensionRules.fieldsProblem(
        q.partitionCode(), q.suspOrderNumber(), q.suspStartDate(), q.suspEndDate());
    if (problem == null && !partitionCodes.isCurrent(q.partitionCode())) {
      problem = "Suspension Partition Code is not a current code.";
    }
    if (problem != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
    }

    Set<Long> selectable = permit.blocks().stream()
        .filter(PermitBlock::selectable)
        .map(PermitBlock::cbSkey)
        .collect(Collectors.toSet());
    if (q.suspendAllBlocks()) {
      if (selectable.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No changes made.");
      }
      mainline(cuttingPermitId, q, null, "Y", userId);
      return selectable.size();
    }

    List<Long> targets = q.cbSkeys() == null ? List.of() : q.cbSkeys();
    if (targets.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No changes made.");
    }
    if (!selectable.containsAll(targets)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "A selected block is not on this permit or can no longer be suspended."
              + " Reload the page and try again.");
    }
    for (Long cbSkey : targets) {
      mainline(cuttingPermitId, q, cbSkey.toString(), null, userId);
    }
    return targets.size();
  }

  private void mainline(
      String cuttingPermitId,
      SuspendBlocksRequest q,
      String cbSkey,
      String suspendAll,
      String userId) {
    legacy.run(PROCEDURE, CURSOR_TYPE,
        "SUSPEND",
        q.forestFileId(),
        cuttingPermitId,
        suspendAll,
        null, // p_cp_effective_date
        null, // p_cp_expiry_date
        null, // p_cp_extend_date
        q.partitionCode(),
        q.suspOrderNumber().trim(),
        q.suspStartDate().toString(),
        q.suspEndDate().toString(),
        cbSkey,
        null, // p_suspend_checkbox
        userId);
  }

  private List<PermitBlock> blocks(Map<String, String> key) {
    return jdbc.query(BLOCKS_SQL, key, (rs, n) -> {
      String status = rs.getString("block_status_st");
      return new PermitBlock(
          rs.getLong("cb_skey"),
          rs.getString("cut_block_id"),
          status,
          rs.getString("status_description"),
          rs.getObject("planned_harvest_date", LocalDate.class),
          number(rs.getBigDecimal("planned_gross_block_area")),
          number(rs.getBigDecimal("planned_net_block_area")),
          SuspensionRules.permitBlockEligible(status, "Y".equals(rs.getString("has_suspension"))));
    });
  }

  private static Double number(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  private static Map<String, String> key(String cuttingPermitId, String forestFileId) {
    return Map.of("forestFileId", forestFileId, "cuttingPermitId", cuttingPermitId);
  }
}
