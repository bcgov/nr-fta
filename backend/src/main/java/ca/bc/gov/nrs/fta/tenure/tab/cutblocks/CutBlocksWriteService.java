package ca.bc.gov.nrs.fta.tenure.tab.cutblocks;

import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockCreateRequest;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockCreated;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockDeleteRequest;
import ca.bc.gov.nrs.fta.tenure.tab.cutblocks.CutBlocksDtos.CutBlockRow;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Cut block tab's writes.
 *
 * <ul>
 *   <li><b>Add</b> — FTA903's Add New opened FTA904 in add mode; its save is
 *       {@code FTA_904_CUTBLKDETAIL.add_new_rec}: a {@code CUT_BLOCK} in status PP under the
 *       permit's primary timber mark, and its {@code CUT_BLOCK_OPEN_ADMIN} with the planned
 *       areas (truncated to 4 decimals) and start. Gated by {@link CutBlocksRules} (FTA903's
 *       {@code FTA903_ADD_NEW} per permit, FTA904's "no updates on a PE file") and checked by
 *       {@link CutBlocksFieldChecks} plus FTA904's {@code FTA_EDIT_CUTBLOCK_ID} (no duplicate
 *       File/CP/Block) and its expired-code check on the fire harvesting reason.
 *   <li><b>Delete</b> — FTA903's Delete, with its mandatory comment, through
 *       {@link CutBlocksDeleteLegacy}; refused for a row the tab shows Delete disabled on.
 * </ul>
 */
@Service
public class CutBlocksWriteService {

  /** {@code add_new_rec}: "Block status must be PP for new block". */
  static final String NEW_STATUS = "PP";

  /** CUT_BLOCK_KEY_EVENT's comment; legacy sets no limit, this keeps it a short reason. */
  static final int MAX_COMMENT = 200;

  /** {@code FTA_EDIT_CUTBLOCK_ID}: does the File/CP/Block combination exist. */
  private static final String COMBO_EXISTS_SQL =
      """
      SELECT COUNT(*) FROM the.cut_block
       WHERE forest_file_id = UPPER(TRIM(:forestFileId))
         AND ((cutting_permit_id = :cpId AND :cpId IS NOT NULL) OR :cpId IS NULL)
         AND cut_block_id = UPPER(TRIM(:blockId))
      """;

  private static final String FIRE_CODE_CURRENT_SQL =
      """
      SELECT COUNT(*) FROM the.fire_harvesting_reason_code
       WHERE fire_harvesting_reason_code = :code
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private static final String NEXT_CB_SQL = "SELECT the.cut_block_seq.NEXTVAL FROM dual";

  private static final String INSERT_CB_SQL =
      """
      INSERT INTO the.cut_block (
        cb_skey, hva_skey, cut_block_id, timber_mark, forest_file_id, cutting_permit_id,
        block_status_st, block_status_date, cut_block_description, sp_exempt_ind,
        is_waste_assessment_required, fire_harvesting_reason_code, is_under_partition_order,
        reported_fire_date,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :cbSkey, :hvaSkey, :blockId, :timberMark, :forestFileId, :cpId,
        :status, NVL(:statusDate, SYSDATE), :description, :spExempt,
        :waste, :fireCode, :underPartition,
        :reportedFireDate,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private static final String INSERT_CBOA_SQL =
      """
      INSERT INTO the.cut_block_open_admin (
        cut_block_open_admin_id, cb_skey, forest_file_id, timber_mark, cut_block_id,
        cutting_permit_id, planned_harvest_date, planned_gross_block_area,
        planned_net_block_area,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        the.cut_block_open_admin_seq.NEXTVAL, :cbSkey, :forestFileId, :timberMark, :blockId,
        :cpId, :plannedStart, TRUNC(:gross, 4),
        TRUNC(:net, 4),
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  private static final String BLOCK_SQL =
      """
      SELECT forest_file_id, cutting_permit_id, cut_block_id, hva_skey
        FROM the.cut_block WHERE cb_skey = :cbSkey
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final CutBlocksService read;
  private final CutBlocksDeleteLegacy legacy;

  public CutBlocksWriteService(
      NamedParameterJdbcTemplate jdbc, CutBlocksService read, CutBlocksDeleteLegacy legacy) {
    this.jdbc = jdbc;
    this.read = read;
    this.legacy = legacy;
  }

  /**
   * Adds a block.
   *
   * @throws ResponseStatusException 404 no tenure; 409 adding not allowed (or the block was
   *     just added); 400 invalid fields
   */
  @Transactional
  public CutBlockCreated add(String forestFileId, CutBlockCreateRequest raw, String userId) {
    CutBlocksService.Tenure t = read.tenure(forestFileId);
    List<CutBlocksService.Permit> permits = read.permits(forestFileId, t);
    CutBlocksRules rules = read.rules(t, permits);
    if (!rules.add()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.addReason());
    }
    if (raw.hvaSkey() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, CutBlocksRules.MSG_NO_CP);
    }
    CutBlocksService.Permit permit = permits.stream()
        .filter(p -> raw.hvaSkey().equals(p.option().hvaSkey()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "That cutting permit is not on this tenure."));
    if (!permit.option().eligible()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, permit.option().reason());
    }

    CutBlockCreateRequest q = new CutBlockCreateRequest(
        raw.hvaSkey(),
        upper(raw.cutBlockId()),
        raw.blockStatusDate(),
        trim(raw.description()),
        raw.plannedGrossArea(),
        raw.plannedNetArea(),
        raw.plannedStartDate(),
        upper(raw.spExempt()),
        raw.wasteAssessmentRequired() == null ? "Y" : upper(raw.wasteAssessmentRequired()),
        upper(raw.underPartitionOrder()),
        upper(raw.fireHarvestingReasonCode()),
        raw.reportedFireDate());

    List<String> e = new java.util.ArrayList<>(CutBlocksFieldChecks.problems(
        q, t.fileTypeCode(), permit.option().salvageTypeCode()));
    if (q.fireHarvestingReasonCode() != null
        && count(FIRE_CODE_CURRENT_SQL, Map.of("code", q.fireHarvestingReasonCode())) == 0) {
      e.add("Fire Harvesting Reason Code (" + q.fireHarvestingReasonCode()
          + ") is an expired code.");
    }
    String cpId = permit.cpId();
    if (e.isEmpty()) {
      MapSqlParameterSource combo = new MapSqlParameterSource()
          .addValue("forestFileId", forestFileId)
          .addValue("cpId", blankToNull(cpId), Types.VARCHAR)
          .addValue("blockId", q.cutBlockId());
      if (count(COMBO_EXISTS_SQL, combo) > 0) {
        e.add("The File, CP and Cut Block combination entered already exists.");
      }
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    Long cbSkey = jdbc.queryForObject(NEXT_CB_SQL, Map.of(), Long.class);
    String timberMark = permit.option().timberMark();
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("cbSkey", cbSkey)
        .addValue("hvaSkey", q.hvaSkey(), Types.NUMERIC)
        .addValue("blockId", q.cutBlockId())
        .addValue("timberMark", timberMark)
        .addValue("forestFileId", forestFileId.toUpperCase(Locale.ROOT))
        // FTA_904_CUTBLKDETAIL.mainline turns a null CP into one space, for every action.
        .addValue("cpId", cpId == null ? " " : cpId)
        .addValue("status", NEW_STATUS)
        .addValue("statusDate", q.blockStatusDate(), Types.DATE)
        .addValue("description", q.description(), Types.VARCHAR)
        .addValue("spExempt", q.spExempt())
        .addValue("waste", q.wasteAssessmentRequired(), Types.VARCHAR)
        .addValue("fireCode", q.fireHarvestingReasonCode(), Types.VARCHAR)
        .addValue("underPartition", q.underPartitionOrder(), Types.VARCHAR)
        .addValue("reportedFireDate", q.reportedFireDate(), Types.DATE)
        .addValue("plannedStart", q.plannedStartDate(), Types.DATE)
        .addValue("gross", q.plannedGrossArea(), Types.NUMERIC)
        .addValue("net", q.plannedNetArea(), Types.NUMERIC)
        .addValue("userId", userId);
    try {
      jdbc.update(INSERT_CB_SQL, p);
      jdbc.update(INSERT_CBOA_SQL, p);
    } catch (DuplicateKeyException ex) {
      // add_new_rec's DUP_VAL_ON_INDEX text.
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cut block already exists.");
    }
    return new CutBlockCreated(cbSkey, q.cutBlockId(), timberMark);
  }

  /**
   * Deletes a block of the tenure.
   *
   * @throws ResponseStatusException 404 no tenure, or the block is not on its list; 409
   *     delete not allowed for the block, or it changed; 400 no comment, or legacy refused
   */
  @Transactional
  public void delete(String forestFileId, long cbSkey, CutBlockDeleteRequest q, String userId) {
    CutBlocksService.Tenure t = read.tenure(forestFileId);
    List<CutBlocksService.Permit> permits = read.permits(forestFileId, t);
    if (!read.rules(t, permits).listable()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cut block not found.");
    }
    CutBlockRow row = read.blocks(forestFileId).stream()
        .filter(r -> r.cbSkey() == cbSkey)
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Cut block not found on this tenure."));
    if (!row.canDelete()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, row.deleteReason());
    }
    String comment = q == null ? null : trim(q.comment());
    if (comment == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deletion comment is mandatory.");
    }
    if (comment.length() > MAX_COMMENT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Deletion comment must not exceed " + MAX_COMMENT + " characters.");
    }
    long revision = q.revisionCount() != null ? q.revisionCount() : row.revisionCount();

    Map<String, Object> block = jdbc.queryForMap(BLOCK_SQL, Map.of("cbSkey", cbSkey));
    Object hva = block.get("hva_skey");
    CutBlocksDeleteLegacy.Result result = legacy.delete(
        (String) block.get("forest_file_id"),
        (String) block.get("cutting_permit_id"),
        (String) block.get("cut_block_id"),
        hva == null ? null : ((Number) hva).longValue(),
        cbSkey,
        comment,
        revision,
        userId);
    if (result.error() != null) {
      // Throwing rolls back whatever the procedure deleted before it failed.
      throw new ResponseStatusException(
          result.modified() ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST, result.error());
    }
  }

  private long count(String sql, Map<String, ?> params) {
    Long n = jdbc.queryForObject(sql, params, Long.class);
    return n == null ? 0 : n;
  }

  private long count(String sql, MapSqlParameterSource params) {
    Long n = jdbc.queryForObject(sql, params, Long.class);
    return n == null ? 0 : n;
  }

  private static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String upper(String s) {
    String t = trim(s);
    return t == null ? null : t.toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
