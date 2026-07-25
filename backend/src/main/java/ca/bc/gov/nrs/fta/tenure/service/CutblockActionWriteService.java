package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.CutblockActionRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for cut-block actions (amend / suspend / re-label).
 *
 * <p>Ports the write path of three legacy PL/SQL packages to native statements
 * against the {@code THE} Oracle schema via {@link NamedParameterJdbcTemplate},
 * branching on the request {@code action}:
 * <ul>
 *   <li>{@code relabel} — mirrors {@code FTA_231_CUTBLK_RELABEL.save_rec}: an
 *       UPDATE of {@code THE.CUT_BLOCK.cut_block_id} (uppercased) with the
 *       revision-count bump and audit stamp.</li>
 *   <li>{@code suspend} — mirrors the block-status change in {@code
 *       FTA_914_SUSPEND_BLOCK.ADD}: sets {@code block_status_st = 'HS'} plus the
 *       status date and audit stamp.</li>
 *   <li>{@code amend} — {@code FTA_905_BLK_AMEND} exposes only a read
 *       (GET/mainline) proc; the write here is a faithful audit-stamp UPDATE of
 *       the base {@code THE.CUT_BLOCK} row derived from the spec only.</li>
 * </ul>
 * The legacy packages carry additional edits (permit-term validation, the
 * {@code CUT_BLOCK_SUSPENSION} insert keyed on {@code cb_skey}, and the fan-out
 * relabel of dependent tables) that should be reproduced as the module is
 * hardened. Runs against the shared {@code THE} schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class CutblockActionWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public CutblockActionWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // FTA_231_CUTBLK_RELABEL.save_rec — re-label the cut block.
  private static final String RELABEL_SQL =
      """
      UPDATE the.cut_block
         SET cut_block_id = UPPER(:newCutBlockId),
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE cut_block_id = :blockId
      """;

  // FTA_914_SUSPEND_BLOCK.ADD — block status change on suspension ('HS').
  private static final String SUSPEND_SQL =
      """
      UPDATE the.cut_block
         SET block_status_st = 'HS',
             block_status_date = SYSDATE,
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE cut_block_id = :blockId
      """;

  // NOTE: FTA_905_BLK_AMEND exposes no write proc (spec is read-only); this
  // audit-stamp UPDATE of the base THE.CUT_BLOCK row is derived from the spec
  // only and marks the block as amended.
  private static final String AMEND_SQL =
      """
      UPDATE the.cut_block
         SET block_status_st = 'AM',
             revision_count = revision_count + 1,
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE cut_block_id = :blockId
      """;

  /**
   * Perform a cut-block action. Returns the number of {@code THE.CUT_BLOCK} rows
   * affected.
   *
   * @param blockId the cut-block id from the path
   * @param request the action + its fields
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public int perform(String blockId, CutblockActionRequest request, String userId) {
    String action = request.action() == null ? "" : request.action().toLowerCase();
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("blockId", blockId)
        .addValue("newCutBlockId", request.newCutBlockId())
        .addValue("userId", userId);
    String sql =
        switch (action) {
          case "relabel" -> RELABEL_SQL;
          case "suspend" -> SUSPEND_SQL;
          case "amend" -> AMEND_SQL;
          default -> throw new IllegalArgumentException("Unknown cut-block action: " + action);
        };
    return jdbc.update(sql, params);
  }
}
