package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlock;
import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlocksResponse;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A Timber Licence's blocks — legacy FTA980 (TL Block Summary), {@code THE.FTA_980_TLBLOCK}.
 *
 * <ul>
 *   <li>{@link #get}: {@code GET} — the blocks of {@code TL_BLOCK_AREA} ordered as legacy did
 *       ({@code LPAD(tl_block_id, 10)}), with {@code getsum}'s totals;
 *   <li>{@link #retire} / {@link #unretire}: {@code RETIRE} / {@code UNRETIRE} — set or clear
 *       the block's retirement date; totals are untouched, as in legacy.
 * </ul>
 *
 * <p>Legacy's screen offers only Retire and Un-Retire (fta980Tlblock.jsp); its package's
 * {@code SAVE} and {@code REMOVE} have no button, so blocks are not added, changed or deleted
 * here. Every write is gated by {@link TlBlockRules} and guarded by the block's revision count
 * (409 when it changed).
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class TlBlockService {

  private static final String TENURE_SQL =
      """
      SELECT forest_file_id, file_type_code, file_status_st
        FROM the.prov_forest_use
       WHERE forest_file_id = :forestFileId
      """;

  private static final String LIST_SQL =
      """
      SELECT tl_block_id, tl_block_gross_ha, tl_block_elimin_ha, tl_block_net_ha,
             retirement_date, revision_count
        FROM the.tl_block_area
       WHERE forest_file_id = :forestFileId
       ORDER BY LPAD(tl_block_id, 10, ' ')
      """;

  private static final String BLOCK_EXISTS_SQL =
      """
      SELECT COUNT(*) FROM the.tl_block_area
       WHERE forest_file_id = :forestFileId AND tl_block_id = :blockId
      """;

  private static final String RETIRE_SQL =
      """
      UPDATE the.tl_block_area
         SET retirement_date = SYSDATE,
             update_timestamp = SYSDATE,
             update_userid = :userId,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND tl_block_id = :blockId
         AND revision_count = :rev
         AND retirement_date IS NULL
      """;

  private static final String UNRETIRE_SQL =
      """
      UPDATE the.tl_block_area
         SET retirement_date = NULL,
             update_timestamp = SYSDATE,
             update_userid = :userId,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND tl_block_id = :blockId
         AND revision_count = :rev
         AND retirement_date IS NOT NULL
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public TlBlockService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The tenure fields the rules read. */
  private record Tenure(String forestFileId, String fileTypeCode, String statusCode) {}

  /**
   * The tab. For a file that is not a Timber Licence the list is empty, as legacy showed
   * nothing for it.
   *
   * @throws ResponseStatusException 404 if the tenure does not exist
   */
  public TlBlocksResponse get(String forestFileId) {
    Tenure tenure = tenure(forestFileId);
    TlBlockRules rules = TlBlockRules.of(tenure.fileTypeCode(), tenure.statusCode());
    List<TlBlock> blocks = rules.timberLicence() ? list(tenure.forestFileId()) : List.of();
    return new TlBlocksResponse(
        rules,
        blocks,
        sum(blocks, TlBlock::grossHa),
        sum(blocks, TlBlock::eliminHa),
        sum(blocks, TlBlock::netHa));
  }

  /**
   * Retires an active block (sets its retirement date to today).
   *
   * @throws ResponseStatusException 404 no tenure or block; 409 not allowed, or modified or
   *     already retired since read
   */
  @Transactional
  public void retire(String forestFileId, String blockId, Long revisionCount, String userId) {
    retirement(forestFileId, blockId, revisionCount, userId, RETIRE_SQL);
  }

  /**
   * Un-retires a retired block (clears its retirement date).
   *
   * @throws ResponseStatusException 404 no tenure or block; 409 not allowed, or modified or
   *     not retired since read
   */
  @Transactional
  public void unretire(String forestFileId, String blockId, Long revisionCount, String userId) {
    retirement(forestFileId, blockId, revisionCount, userId, UNRETIRE_SQL);
  }

  private void retirement(
      String forestFileId, String blockId, Long revisionCount, String userId, String sql) {
    Tenure tenure = tenure(forestFileId);
    TlBlockRules rules = TlBlockRules.of(tenure.fileTypeCode(), tenure.statusCode());
    if (!rules.retire()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.retireReason());
    }
    badRequestIf(revisionCount == null ? List.of("Revision count is mandatory.") : List.of());
    MapSqlParameterSource p = params(tenure, blockId, userId)
        .addValue("rev", revisionCount, Types.NUMERIC);
    guard(jdbc.update(sql, p), p, blockId);
  }

  private List<TlBlock> list(String forestFileId) {
    return jdbc.query(
        LIST_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId),
        (rs, rowNum) -> new TlBlock(
            rs.getString("tl_block_id"),
            rs.getBigDecimal("tl_block_gross_ha"),
            rs.getBigDecimal("tl_block_elimin_ha"),
            rs.getBigDecimal("tl_block_net_ha"),
            rs.getObject("retirement_date", LocalDate.class),
            rs.getObject("revision_count", Long.class)));
  }

  private Tenure tenure(String forestFileId) {
    try {
      return jdbc.queryForObject(
          TENURE_SQL,
          Map.of("forestFileId", forestFileId),
          (rs, rowNum) -> new Tenure(
              rs.getString("forest_file_id"),
              rs.getString("file_type_code"),
              rs.getString("file_status_st")));
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }
  }

  private static MapSqlParameterSource params(Tenure tenure, String blockId, String userId) {
    return new MapSqlParameterSource()
        .addValue("forestFileId", tenure.forestFileId())
        .addValue("blockId", blockId)
        .addValue("userId", userId);
  }

  private boolean blockExists(MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(BLOCK_EXISTS_SQL, p, Long.class);
    return n != null && n > 0;
  }

  /** A write that touched no row: the block is gone (404) or changed since it was read (409). */
  private void guard(int rows, MapSqlParameterSource p, String blockId) {
    if (rows > 0) {
      return;
    }
    if (!blockExists(p)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "TL block " + blockId + " not found.");
    }
    throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "TL block " + blockId + " has been modified by another user. Reload the list and try"
            + " again.");
  }

  private static void badRequestIf(List<String> problems) {
    if (!problems.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", problems));
    }
  }

  private static BigDecimal sum(
      List<TlBlock> blocks, java.util.function.Function<TlBlock, BigDecimal> field) {
    return blocks.stream()
        .map(field)
        .filter(v -> v != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
