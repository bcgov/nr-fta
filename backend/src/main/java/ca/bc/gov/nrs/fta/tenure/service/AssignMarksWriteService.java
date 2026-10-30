package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksDto;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest.BlockAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Assigning timber marks to cut blocks, legacy FTA908, which FTA902 offered only on a Fort St. John
 * authorization (harvest type F, identified by its HVA id rather than a CP id).
 *
 * <p>The writes call {@code FTA_908_HAULING_AUTH.mainline} rather than port it, for its issued
 * status, new mark, mark designate and orphaned mark checks. It compares the block's revision in
 * its UPDATE but never checks that a row changed, so a stale revision would report success. The
 * service therefore locks each block and compares the revision first.
 */
@Service
public class AssignMarksWriteService {

  private static final String PROCEDURE = "fta_908_hauling_auth.mainline";
  private static final String CURSOR_TYPE = "the.fta_908_hauling_auth.ref_hauling_auth";
  private static final String MODIFIED =
      "A block was changed by someone else. Reload the page and try again.";

  private static final String AUTHORIZATION_SQL =
      """
      SELECT ha.hva_skey,
             ha.forest_file_id,
             ha.harvesting_authority_id,
             ha.harvest_auth_status_code,
             ha.status_date,
             ha.expiry_date,
             ha.higher_level_plan_reference,
             cli.client_name AS licensee
        FROM the.harvesting_authority ha
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = ha.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.forest_client cli ON cli.client_number = ffc.client_number
       WHERE ha.forest_file_id = :forestFileId
         AND ha.harvesting_authority_id = :authorizationId
         AND ha.harvest_type_code = 'F'
      """;

  // FTA_908_HAULING_AUTH.GET_LIST
  private static final String BLOCKS_SQL =
      """
      SELECT cb.cb_skey, cb.cut_block_id, cb.timber_mark, cb.revision_count
        FROM the.cut_block cb
       WHERE cb.hva_skey = :hvaSkey
       ORDER BY cb.cut_block_id, cb.timber_mark
      """;

  // FTA_CODE_LISTS.GET_TIMBER_MARKS, legacy's fta.lookup.harvestTimberMarks
  private static final String MARKS_SQL =
      """
      SELECT DISTINCT xref.timber_mark
        FROM the.harvesting_hauling_xref xref
        JOIN the.harvesting_authority hva ON hva.hva_skey = xref.hva_skey
       WHERE hva.forest_file_id = :forestFileId
         AND xref.timber_mark IS NOT NULL
         AND hva.harvest_auth_status_code = 'HI'
         AND hva.harvest_type_code = 'F'
       ORDER BY xref.timber_mark
      """;

  private static final String LOCK_SQL =
      """
      SELECT cut_block_id, revision_count
        FROM the.cut_block
       WHERE cb_skey = :cbSkey
         AND hva_skey = :hvaSkey
         FOR UPDATE
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final HarvestLegacyCall legacy;

  public AssignMarksWriteService(NamedParameterJdbcTemplate jdbc, HarvestLegacyCall legacy) {
    this.jdbc = jdbc;
    this.legacy = legacy;
  }

  /** The authorization, its blocks and the marks on offer. */
  public AssignMarksDto find(String authorizationId, String forestFileId) {
    AssignMarksDto authorization = jdbc.query(
            AUTHORIZATION_SQL,
            Map.of("forestFileId", forestFileId, "authorizationId", authorizationId),
            (rs, n) -> new AssignMarksDto(
                rs.getLong("hva_skey"),
                rs.getString("forest_file_id"),
                rs.getString("harvesting_authority_id"),
                rs.getString("harvest_auth_status_code"),
                rs.getObject("status_date", LocalDate.class),
                rs.getObject("expiry_date", LocalDate.class),
                rs.getString("licensee"),
                rs.getString("higher_level_plan_reference"),
                List.of(),
                List.of()))
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No Fort St. John authorization matches this file and id."));
    return new AssignMarksDto(
        authorization.hvaSkey(),
        authorization.forestFileId(),
        authorization.authorizationId(),
        authorization.statusCode(),
        authorization.approvalDate(),
        authorization.expiryDate(),
        authorization.licensee(),
        authorization.higherLevelPlanReference(),
        blocks(authorization.hvaSkey()),
        marks(forestFileId));
  }

  /**
   * Assigns the requested marks, all or none.
   *
   * @param senior whether the caller may specify a new timber mark
   * @return the number of blocks updated
   */
  @Transactional
  public int assignMarks(
      String authorizationId, AssignMarksRequest q, boolean senior, String userId) {
    if (q.forestFileId() == null || q.forestFileId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forest file is required.");
    }
    AssignMarksDto authorization = find(authorizationId, q.forestFileId());
    if (q.hvaSkey() == null || q.hvaSkey() != authorization.hvaSkey()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "The request does not match this authorization.");
    }
    String problem = AssignMarksRules.problem(q, senior);
    if (problem != null) {
      throw new ResponseStatusException(
          problem.startsWith("Specify New") ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST,
          problem);
    }

    if (q.assignToAllMark() != null && !q.assignToAllMark().isBlank()) {
      offeredCheck(authorization, q.assignToAllMark());
      call("Assign Mark to All", authorization, null, null, q.assignToAllMark(), null, null,
          userId);
      return authorization.blocks().size();
    }

    for (BlockAssignment a : q.assignments()) {
      String existing = a.timberMark() == null || a.timberMark().isBlank() ? null : a.timberMark();
      if (existing != null) {
        offeredCheck(authorization, existing);
      }
      String created = existing != null
          ? null : a.newTimberMark().trim().toUpperCase(Locale.ROOT);
      String cutBlockId = lockedBlock(a, authorization.hvaSkey());
      call("SAVE", authorization, a.cbSkey().toString(), cutBlockId, existing, created,
          a.revisionCount().toString(), userId);
    }
    return q.assignments().size();
  }

  private void offeredCheck(AssignMarksDto authorization, String mark) {
    if (!authorization.marks().contains(mark)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Timber Mark " + mark + " is not available on this file.");
    }
  }

  private String lockedBlock(BlockAssignment a, long hvaSkey) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        LOCK_SQL, Map.of("cbSkey", a.cbSkey(), "hvaSkey", hvaSkey));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A block is not on this authorization.");
    }
    if (((Number) rows.get(0).get("revision_count")).longValue() != a.revisionCount()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return (String) rows.get(0).get("cut_block_id");
  }

  private void call(
      String action,
      AssignMarksDto authorization,
      String cbSkey,
      String cutBlockId,
      String timberMark,
      String newTimberMark,
      String revisionCount,
      String userId) {
    legacy.run(PROCEDURE, CURSOR_TYPE,
        action,
        authorization.forestFileId(),
        null, // p_cutting_permit_id: a Fort St. John authorization has none
        Long.toString(authorization.hvaSkey()),
        cbSkey,
        authorization.authorizationId(),
        null, // p_approval_date
        null, // p_expiry_date
        null, // p_licensee
        cutBlockId,
        timberMark,
        newTimberMark,
        null, // p_higher_level_plan_reference
        userId,
        revisionCount);
  }

  private List<AssignMarksDto.Block> blocks(long hvaSkey) {
    return jdbc.query(BLOCKS_SQL, Map.of("hvaSkey", hvaSkey), (rs, n) -> new AssignMarksDto.Block(
        rs.getLong("cb_skey"),
        rs.getString("cut_block_id"),
        rs.getString("timber_mark"),
        rs.getLong("revision_count")));
  }

  private List<String> marks(String forestFileId) {
    return jdbc.queryForList(MARKS_SQL, Map.of("forestFileId", forestFileId), String.class);
  }
}
