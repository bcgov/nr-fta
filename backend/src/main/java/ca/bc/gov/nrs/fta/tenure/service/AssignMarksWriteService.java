package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.AssignMarksRequest;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Write operations for assigning hauling timber marks to cut blocks.
 *
 * <p>Ports the {@code save} path of the legacy {@code FTA_908_HAULING_AUTH}
 * package to native statements against the {@code THE} Oracle schema via
 * {@link NamedParameterJdbcTemplate}. For each block assignment the legacy
 * {@code save} procedure: (1) ensures a {@code HAULING_AUTHORITY} row exists
 * for the mark (legacy {@code create_hauling_auth}), (2) ensures a
 * {@code HARVESTING_HAULING_XREF} row links the mark to the harvesting
 * authority, defaulting the first mark to primary (legacy
 * {@code create_hauling_xref}), then (3) stamps the mark onto the
 * {@code CUT_BLOCK} row with an optimistic {@code revision_count} guard.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment. The legacy
 * pre-write validations (HI status check, orphan-mark check, mark-designate
 * prefix check) should be reproduced here as the module is hardened.
 */
@Service
public class AssignMarksWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public AssignMarksWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Mirrors legacy create_hauling_auth: insert the mark only when absent.
  private static final String INSERT_HAULING_AUTH_SQL =
      """
      INSERT INTO the.hauling_authority (
        timber_mark, forest_file_id, marking_method_code,
        marking_instrument_code, entry_timestamp, entry_userid,
        update_timestamp, update_userid, revision_count
      )
      SELECT :timberMark, :forestFileId, 'S', 'H',
             SYSDATE, :userId, SYSDATE, :userId, 1
        FROM dual
       WHERE NOT EXISTS (
             SELECT 1 FROM the.hauling_authority
              WHERE timber_mark = :timberMark)
      """;

  // Mirrors legacy create_hauling_xref: link mark to hva, default first as primary.
  private static final String INSERT_HAULING_XREF_SQL =
      """
      INSERT INTO the.harvesting_hauling_xref (
        hva_skey, timber_mark, primary_mark_ind, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      )
      SELECT TO_NUMBER(:hvaSkey), :timberMark,
             CASE WHEN EXISTS (
                    SELECT 1 FROM the.harvesting_hauling_xref
                     WHERE primary_mark_ind = 'Y'
                       AND hva_skey = TO_NUMBER(:hvaSkey))
                  THEN 'N' ELSE 'Y' END,
             1, :userId, SYSDATE, :userId, SYSDATE
        FROM dual
       WHERE NOT EXISTS (
             SELECT 1 FROM the.harvesting_hauling_xref
              WHERE timber_mark = :timberMark
                AND hva_skey = TO_NUMBER(:hvaSkey))
      """;

  // Mirrors legacy save UPDATE of cut_block with optimistic revision guard.
  private static final String UPDATE_CUT_BLOCK_SQL =
      """
      UPDATE the.cut_block
         SET timber_mark = :timberMark,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE cb_skey = TO_NUMBER(:cbSkey)
         AND revision_count = :revisionCount
      """;

  /**
   * Assign the requested hauling timber marks to each cut block. Returns the
   * number of {@code CUT_BLOCK} rows updated.
   *
   * @param request the forest file, harvesting authority and per-block marks
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public int assignMarks(AssignMarksRequest request, String userId) {
    List<AssignMarksRequest.BlockAssignment> assignments = request.assignments();
    if (assignments == null || assignments.isEmpty()) {
      return 0;
    }

    int updated = 0;
    for (AssignMarksRequest.BlockAssignment a : assignments) {
      // Legacy: IF p_new_timber_mark IS NOT NULL THEN p_timber_mark := p_new_timber_mark.
      String effectiveMark =
          StringUtils.hasText(a.newTimberMark()) ? a.newTimberMark() : a.timberMark();

      MapSqlParameterSource markParams = new MapSqlParameterSource()
          .addValue("timberMark", effectiveMark)
          .addValue("forestFileId", request.forestFileId())
          .addValue("hvaSkey", request.hvaSkey())
          .addValue("userId", userId);
      jdbc.update(INSERT_HAULING_AUTH_SQL, markParams);
      jdbc.update(INSERT_HAULING_XREF_SQL, markParams);

      MapSqlParameterSource blockParams = new MapSqlParameterSource()
          .addValue("timberMark", effectiveMark)
          .addValue("cbSkey", a.cbSkey())
          .addValue("revisionCount", a.revisionCount())
          .addValue("userId", userId);
      updated += jdbc.update(UPDATE_CUT_BLOCK_SQL, blockParams);
    }
    return updated;
  }
}
