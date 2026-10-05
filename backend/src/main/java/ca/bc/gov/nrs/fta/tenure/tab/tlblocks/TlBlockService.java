package ca.bc.gov.nrs.fta.tenure.tab.tlblocks;

import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlock;
import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlockSaveRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tlblocks.TlBlockDtos.TlBlocksResponse;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
 *   <li>{@link #add} / {@link #update}: {@code SAVE} ({@code ADD} / {@code CHANGE}) — net is
 *       gross less eliminated, and the licence's {@code TIMBER_LIC_AREA} is re-rolled from the
 *       blocks ({@code updatesum}); an add first creates that row if the licence has none, and
 *       touches {@code PROV_FOREST_USE} for HDBS transaction processing, as legacy did;
 *   <li>{@link #delete}: {@code REMOVE}, then the same roll-up;
 *   <li>{@link #retire} / {@link #unretire}: {@code RETIRE} / {@code UNRETIRE} — set or clear
 *       the block's retirement date; totals are untouched, as in legacy.
 * </ul>
 *
 * <p>Every write is gated by {@link TlBlockRules} and guarded by the block's revision count
 * (409 when it changed). Legacy also locked {@code TIMBER_LIC_AREA}'s revision count; here its
 * areas are recomputed from the blocks inside the same transaction, so that lock is not needed.
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

  /** ADD's TIMBER_LIC_AREA create, when the licence has none yet (areas set by the roll-up). */
  private static final String INSERT_LICENCE_AREA_SQL =
      """
      INSERT INTO the.timber_lic_area (
        forest_file_id, init_licence_area, obligation_area, eliminated_area, non_harvest_area,
        update_userid, update_timestamp, revision_count, entry_userid, entry_timestamp
      )
      SELECT :forestFileId, 0, 0, 0, 0, :userId, SYSDATE, 0, :userId, SYSDATE
        FROM dual
       WHERE NOT EXISTS (SELECT 1 FROM the.timber_lic_area WHERE forest_file_id = :forestFileId)
      """;

  /** ADD's "touch the record in prov_forest_use for HDBS transaction processing". */
  private static final String TOUCH_TENURE_SQL =
      "UPDATE the.prov_forest_use SET update_userid = :userId WHERE forest_file_id = :forestFileId";

  /** {@code updatesum}: the licence's areas are the sums of its blocks'. */
  private static final String ROLL_UP_SQL =
      """
      UPDATE the.timber_lic_area
         SET (init_licence_area, eliminated_area, obligation_area) =
             (SELECT NVL(SUM(tb.tl_block_gross_ha), 0),
                     NVL(SUM(tb.tl_block_elimin_ha), 0),
                     NVL(SUM(tb.tl_block_net_ha), 0)
                FROM the.tl_block_area tb
               WHERE tb.forest_file_id = :forestFileId),
             update_timestamp = SYSDATE,
             update_userid = :userId,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
      """;

  /**
   * ADD's insert. The GUID is what ESF's {@code process_tl_block} gives each block (legacy's
   * own ADD predates the column).
   */
  private static final String INSERT_SQL =
      """
      INSERT INTO the.tl_block_area (
        forest_file_id, tl_block_id, tl_block_area_guid, tl_block_gross_ha,
        tl_block_elimin_ha, tl_block_net_ha,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :forestFileId, :blockId, SYS_GUID(), :gross,
        :elim, :gross - :elim,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE the.tl_block_area
         SET tl_block_gross_ha = :gross,
             tl_block_elimin_ha = :elim,
             tl_block_net_ha = :gross - :elim,
             update_timestamp = SYSDATE,
             update_userid = :userId,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND tl_block_id = :blockId
         AND revision_count = :rev
      """;

  private static final String DELETE_SQL =
      """
      DELETE FROM the.tl_block_area
       WHERE forest_file_id = :forestFileId
         AND tl_block_id = :blockId
         AND revision_count = :rev
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
   * Adds a block.
   *
   * @throws ResponseStatusException 404 no tenure; 409 not allowed or already exists; 400
   *     invalid fields
   */
  @Transactional
  public void add(String forestFileId, TlBlockSaveRequest q, String userId) {
    Tenure tenure = editable(forestFileId);
    String blockId = TlBlockFieldChecks.normalizeBlockId(q.tlBlockId());
    List<String> e = new ArrayList<>(TlBlockFieldChecks.blockIdProblems(blockId));
    e.addAll(TlBlockFieldChecks.areaProblems(q.grossHa(), q.eliminHa()));
    badRequestIf(e);

    MapSqlParameterSource p = params(tenure, blockId, userId)
        .addValue("gross", q.grossHa(), Types.NUMERIC)
        .addValue("elim", zeroIfNull(q.eliminHa()), Types.NUMERIC);
    if (blockExists(p)) {
      throw alreadyExists(blockId);
    }
    jdbc.update(INSERT_LICENCE_AREA_SQL, p);
    jdbc.update(TOUCH_TENURE_SQL, p);
    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw alreadyExists(blockId);
    }
    jdbc.update(ROLL_UP_SQL, p);
  }

  /**
   * Changes a block's gross and eliminated areas.
   *
   * @throws ResponseStatusException 404 no tenure or block; 409 not allowed or modified since
   *     read; 400 invalid fields
   */
  @Transactional
  public void update(String forestFileId, String blockId, TlBlockSaveRequest q, String userId) {
    Tenure tenure = editable(forestFileId);
    List<String> e = new ArrayList<>(TlBlockFieldChecks.areaProblems(q.grossHa(), q.eliminHa()));
    if (q.revisionCount() == null) {
      e.add("Revision count is mandatory.");
    }
    badRequestIf(e);

    MapSqlParameterSource p = params(tenure, blockId, userId)
        .addValue("gross", q.grossHa(), Types.NUMERIC)
        .addValue("elim", zeroIfNull(q.eliminHa()), Types.NUMERIC)
        .addValue("rev", q.revisionCount(), Types.NUMERIC);
    guard(jdbc.update(UPDATE_SQL, p), p, blockId);
    jdbc.update(INSERT_LICENCE_AREA_SQL, p);
    jdbc.update(ROLL_UP_SQL, p);
  }

  /**
   * Deletes a block.
   *
   * @throws ResponseStatusException 404 no tenure or block; 409 not allowed, modified since
   *     read, or the block has related records
   */
  @Transactional
  public void delete(String forestFileId, String blockId, Long revisionCount, String userId) {
    Tenure tenure = editable(forestFileId);
    MapSqlParameterSource p = params(tenure, blockId, userId)
        .addValue("rev", revisionCount, Types.NUMERIC);
    badRequestIf(revisionCount == null ? List.of("Revision count is mandatory.") : List.of());
    int n;
    try {
      n = jdbc.update(DELETE_SQL, p);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "TL block " + blockId + " has related records (amendments or map features) and"
              + " cannot be deleted.");
    }
    guard(n, p, blockId);
    jdbc.update(INSERT_LICENCE_AREA_SQL, p);
    jdbc.update(ROLL_UP_SQL, p);
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

  /** The tenure, when its blocks may be added, changed or deleted; else 409 with why not. */
  private Tenure editable(String forestFileId) {
    Tenure tenure = tenure(forestFileId);
    TlBlockRules rules = TlBlockRules.of(tenure.fileTypeCode(), tenure.statusCode());
    if (!rules.edit()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, rules.editReason());
    }
    return tenure;
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

  private static ResponseStatusException alreadyExists(String blockId) {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "TL block " + blockId + " already exists on this licence.");
  }

  private static BigDecimal zeroIfNull(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }

  private static BigDecimal sum(
      List<TlBlock> blocks, java.util.function.Function<TlBlock, BigDecimal> field) {
    return blocks.stream()
        .map(field)
        .filter(v -> v != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
