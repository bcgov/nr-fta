package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.Options;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.RemoveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.Result;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.SaveRequest;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA615R Associated Land Base: add, update and remove (de-activate) a land base record.
 *
 * <p>{@code FTA_615_RANGE_LAND_BASE}'s SAVE_LAND_BASE and REMOVE_LAND_BASE are a plain insert
 * (keyed by RANGE_LAND_BASE_SEQ, active, revision 0), an update under the revision read, and a
 * soft delete ({@code range_land_base_act_ind = 'N'}) under the revision read. They are ported
 * here as the same SQL.
 *
 * <p>Legacy 615R checked no authority at all: anyone who could open the screen could save. Here
 * the URL rules require FTA_ADMIN or a range role, and {@link RangeFileAccess#requireEditable}
 * limits a district admin to the files of their district, as on the other range tabs.
 *
 * <p>Legacy's Remove required the record to carry a comment, read from the record as listed (so
 * a user first had to Update the record with one). Here the comment is given with the Remove and,
 * when it differs, stored on the record first, in the same transaction.
 */
@Service
public class RangeLandBaseWriteService {

  static final String NOT_FOUND = "The selected land base does not exist.";
  static final String MODIFIED =
      "The land base record was changed by someone else. Reload it and try again.";

  /** FTA_CODE_LISTS ownership types, current codes only. */
  static final String OWNERSHIP_TYPES_SQL =
      """
      SELECT range_land_ownership_type_code AS code,
             range_land_ownership_type_code || ' - ' || description AS description
        FROM the.range_land_ownership_type_code
       WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE
       ORDER BY range_land_ownership_type_code
      """;

  /** FTA_CODE_LISTS.GET_RANGE_LAND_PURPOSE_CODE, current codes only. */
  static final String PURPOSES_SQL =
      """
      SELECT range_land_purpose_code AS code,
             range_land_purpose_code || ' - ' || description AS description
        FROM the.range_land_purpose_code
       WHERE effective_date <= SYSDATE AND expiry_date >= SYSDATE
       ORDER BY range_land_purpose_code
      """;

  /** FTA_615.GET's current record: an active record of the file. */
  static final String ROW_SQL =
      """
      SELECT range_land_ownership_type_code, range_land_purpose_code, land_base_comment,
             revision_count
        FROM the.range_land_base
       WHERE forest_file_id = :forestFileId
         AND rlb_skey = :rlbSkey
         AND range_land_base_act_ind = 'Y'
      """;

  /** SAVE_LAND_BASE, a new record. */
  static final String INSERT_SQL =
      """
      INSERT INTO the.range_land_base (
        forest_file_id, rlb_skey, range_land_base_pid, range_land_base_id, description,
        range_land_ownership_type_code, range_land_purpose_code, fenced_pasture,
        unfenced_pasture, forage_production, land_base_comment, lease_start_date,
        lease_end_date, range_land_base_act_ind, entry_userid, entry_timestamp, update_userid,
        update_timestamp, revision_count
      ) VALUES (
        :forestFileId, the.range_land_base_seq.NEXTVAL, :pid, :landBaseId, :description,
        :ownershipTypeCode, :purposeCode, :fencedPasture,
        :unfencedPasture, :forageProduction, :comment, :leaseStartDate,
        :leaseEndDate, 'Y', :userId, SYSDATE, :userId,
        SYSDATE, 0
      )
      """;

  /** SAVE_LAND_BASE, an existing record. */
  static final String UPDATE_SQL =
      """
      UPDATE the.range_land_base
         SET range_land_base_pid = :pid,
             range_land_base_id = :landBaseId,
             description = :description,
             range_land_ownership_type_code = :ownershipTypeCode,
             range_land_purpose_code = :purposeCode,
             fenced_pasture = :fencedPasture,
             unfenced_pasture = :unfencedPasture,
             forage_production = :forageProduction,
             land_base_comment = :comment,
             lease_start_date = :leaseStartDate,
             lease_end_date = :leaseEndDate,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND rlb_skey = :rlbSkey
         AND revision_count = :revisionCount
      """;

  /** The Remove's comment, stored on the record before it is de-activated. */
  static final String COMMENT_SQL =
      """
      UPDATE the.range_land_base
         SET land_base_comment = :comment,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND rlb_skey = :rlbSkey
         AND revision_count = :revisionCount
      """;

  /** REMOVE_LAND_BASE. */
  static final String REMOVE_SQL =
      """
      UPDATE the.range_land_base
         SET range_land_base_act_ind = 'N',
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE forest_file_id = :forestFileId
         AND rlb_skey = :rlbSkey
         AND revision_count = :revisionCount
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final RangeFileAccess access;

  public RangeLandBaseWriteService(NamedParameterJdbcTemplate jdbc, RangeFileAccess access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  /** The editors' lists. */
  public Options options() {
    return new Options(codes(OWNERSHIP_TYPES_SQL), codes(PURPOSES_SQL));
  }

  /** Save a new land base record. */
  @Transactional
  public Result add(String forestFileId, SaveRequest r, String userId) {
    access.requireEditable(forestFileId);
    check(RangeLandBaseRules.validateSave(r, codeContext(null, null)));
    jdbc.update(INSERT_SQL, params(forestFileId, RangeLandBaseRules.parse(r), userId));
    return new Result(RangeLandBaseRules.SAVE_SUCCESS);
  }

  /** Save an existing land base record, under the revision read. */
  @Transactional
  public Result update(String forestFileId, long rlbSkey, SaveRequest r, String userId) {
    access.requireEditable(forestFileId);
    Stored stored = stored(forestFileId, rlbSkey);
    check(RangeLandBaseRules.validateSave(
        r, codeContext(stored.ownershipTypeCode(), stored.purposeCode())));
    if (r.revisionCount() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    MapSqlParameterSource p = params(forestFileId, RangeLandBaseRules.parse(r), userId)
        .addValue("rlbSkey", rlbSkey)
        .addValue("revisionCount", r.revisionCount());
    if (jdbc.update(UPDATE_SQL, p) <= 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return new Result(RangeLandBaseRules.SAVE_SUCCESS);
  }

  /** De-activate a land base record, storing the comment the removal requires. */
  @Transactional
  public Result remove(String forestFileId, long rlbSkey, RemoveRequest r, String userId) {
    access.requireEditable(forestFileId);
    check(RangeLandBaseRules.validateRemove(r.landBaseComment()));
    Stored stored = stored(forestFileId, rlbSkey);
    if (r.revisionCount() == null || r.revisionCount() != stored.revisionCount()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    int revision = r.revisionCount();
    String comment = RangeLandBaseRules.trimToNull(r.landBaseComment());
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("rlbSkey", rlbSkey)
        .addValue("userId", userId)
        .addValue("comment", comment);
    if (!Objects.equals(comment, stored.comment())) {
      if (comment.length() > RangeLandBaseRules.MAX_COMMENT) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Comment must be at most " + RangeLandBaseRules.MAX_COMMENT + " characters.");
      }
      if (jdbc.update(COMMENT_SQL, p.addValue("revisionCount", revision)) <= 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
      }
      revision++;
    }
    if (jdbc.update(REMOVE_SQL, p.addValue("revisionCount", revision)) <= 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, MODIFIED);
    }
    return new Result(RangeLandBaseRules.REMOVE_SUCCESS);
  }

  private static void check(List<String> errors) {
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
  }

  private Stored stored(String forestFileId, long rlbSkey) {
    List<Stored> rows = jdbc.query(ROW_SQL,
        new MapSqlParameterSource()
            .addValue("forestFileId", forestFileId)
            .addValue("rlbSkey", rlbSkey),
        (rs, n) -> new Stored(
            rs.getString("range_land_ownership_type_code"),
            rs.getString("range_land_purpose_code"),
            rs.getString("land_base_comment"),
            rs.getInt("revision_count")));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND);
    }
    return rows.get(0);
  }

  private RangeLandBaseRules.Codes codeContext(String storedOwnership, String storedPurpose) {
    return new RangeLandBaseRules.Codes(
        codeSet(OWNERSHIP_TYPES_SQL), codeSet(PURPOSES_SQL), storedOwnership, storedPurpose);
  }

  private static MapSqlParameterSource params(
      String forestFileId, RangeLandBaseRules.Parsed v, String userId) {
    return new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("pid", v.pid(), Types.VARCHAR)
        .addValue("landBaseId", v.landBaseId(), Types.VARCHAR)
        .addValue("description", v.description(), Types.VARCHAR)
        .addValue("ownershipTypeCode", v.ownershipTypeCode(), Types.VARCHAR)
        .addValue("purposeCode", v.purposeCode(), Types.VARCHAR)
        .addValue("fencedPasture", v.fencedPasture(), Types.NUMERIC)
        .addValue("unfencedPasture", v.unfencedPasture(), Types.NUMERIC)
        .addValue("forageProduction", v.forageProduction(), Types.NUMERIC)
        .addValue("comment", v.comment(), Types.VARCHAR)
        .addValue("leaseStartDate", v.leaseStartDate(), Types.DATE)
        .addValue("leaseEndDate", v.leaseEndDate(), Types.DATE)
        .addValue("userId", userId);
  }

  private List<CodeOptionDto> codes(String sql) {
    return jdbc.query(sql, Map.of(),
        (rs, n) -> new CodeOptionDto(rs.getString("code"), rs.getString("description")));
  }

  private Set<String> codeSet(String sql) {
    Set<String> set = new HashSet<>();
    for (CodeOptionDto c : codes(sql)) {
      set.add(c.code());
    }
    return set;
  }

  record Stored(String ownershipTypeCode, String purposeCode, String comment,
      int revisionCount) {}
}
