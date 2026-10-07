package ca.bc.gov.nrs.fta.mark.service;

import static ca.bc.gov.nrs.fta.mark.service.MarkFieldChecks.trim;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkEditRules;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexRequest;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexUpdateRequest;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Adds a land index to a private mark, or updates one on it — legacy FTA511's add row and
 * the save of an existing one.
 *
 * <p>Ports {@code FTA_511_MARK_LAND_INDEX}: the gate from its {@code GET} (Headquarters only;
 * not at HX, DV or DD; not for B15/B16 — {@link MarkEditRules#landIndex()}), the form's checks
 * ({@code Fta511MarkLandIndexForm}: Land District/Island required, both codes current,
 * description up to 40), and {@code SAVE}'s insert. The row carries the certificate and the
 * timber mark, so an application not yet issued keeps its land index when the mark is.
 * An update has its own gate ({@link MarkEditRules#landIndexUpdate()}): Headquarters may
 * correct a row at any status.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class MarkLandIndexWriteService {

  /** The legacy add row's description maxlength. */
  private static final int MAX_DESC = 40;

  private final NamedParameterJdbcTemplate jdbc;
  private final MarkDetailService markDetailService;
  private final MarkFieldChecks checks;

  public MarkLandIndexWriteService(
      NamedParameterJdbcTemplate jdbc,
      MarkDetailService markDetailService,
      MarkFieldChecks checks) {
    this.jdbc = jdbc;
    this.markDetailService = markDetailService;
    this.checks = checks;
  }

  private static final String PRIMARY_SQL =
      """
      SELECT COUNT(*) FROM the.primary_land_index_code
       WHERE primary_land_index_code = :code AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  private static final String SECONDARY_SQL =
      """
      SELECT COUNT(*) FROM the.secondary_land_index_code
       WHERE secondary_land_index_code = :code
         AND SYSDATE BETWEEN effective_date AND expiry_date
      """;

  /** The same index already on the mark — legacy relied on the unique key for this. */
  private static final String DUPLICATE_SQL =
      """
      SELECT COUNT(*) FROM the.mark_land_index
       WHERE (timber_mark = :timberMark OR certificate = :certificate)
         AND primary_land_index_code = :primary
         AND NVL(secondary_land_index_code, ' ') = NVL(:secondary, ' ')
         AND NVL(mark_land_index_desc, ' ') = NVL(:description, ' ')
         AND mark_land_index_skey <> NVL(:skey, -1)
      """;

  /** The row being updated, provided it is this mark's. */
  private static final String ROW_SQL =
      """
      SELECT primary_land_index_code, secondary_land_index_code FROM the.mark_land_index
       WHERE mark_land_index_skey = :skey
         AND (timber_mark = :timberMark OR certificate = :certificate)
      """;

  /** FTA_511.SAVE's update, guarded by the revision count as read. */
  private static final String UPDATE_SQL =
      """
      UPDATE the.mark_land_index
         SET primary_land_index_code = :primary,
             secondary_land_index_code = :secondary,
             mark_land_index_desc = :description,
             index_deactivate_date = :deactivate,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE mark_land_index_skey = :skey
         AND revision_count = :revisionCount
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.mark_land_index (
        mark_land_index_skey, certificate, timber_mark, primary_land_index_code,
        secondary_land_index_code, mark_land_index_desc, index_deactivate_date,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        the.mark_land_index_seq.NEXTVAL, :certificate, :timberMark, :primary,
        :secondary, :description, NULL,
        :userId, SYSDATE, :userId, SYSDATE, 0
      )
      """;

  /**
   * Adds the land index.
   *
   * @throws ResponseStatusException 404 if the mark does not exist; 409 if land index cannot
   *     be changed at its status or type, or the index is already on it; 400 if a field is
   *     invalid
   */
  @Transactional
  public void add(
      String id, boolean byCertificate, MarkLandIndexRequest request, String userId) {
    MarkDetailDto mark = find(id, byCertificate);
    if (!MarkEditRules.of(mark, true).landIndex()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Land index may not be updated at this mark's status or type.");
    }
    MapSqlParameterSource p = validated(
        mark, null, null, request.primaryLandIndexCode(), request.secondaryLandIndexCode(),
        request.markLandIndexDesc(), userId);
    rejectDuplicate(p);
    try {
      jdbc.update(INSERT_SQL, p);
    } catch (DuplicateKeyException ex) {
      // The unique key, should two adds race past the check above.
      throw alreadyOnMark();
    }
  }

  /**
   * Updates the land index {@code skey} on the mark.
   *
   * @throws ResponseStatusException 404 if the mark or the row on it does not exist; 409 if
   *     the user may not update it, the change duplicates another row, or someone else saved
   *     it first; 400 if a field is invalid
   */
  @Transactional
  public void update(
      String id,
      boolean byCertificate,
      long skey,
      MarkLandIndexUpdateRequest request,
      boolean districtUser,
      String userId) {
    MarkDetailDto mark = find(id, byCertificate);
    if (!MarkEditRules.of(mark, true, districtUser).landIndexUpdate()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Land index may not be updated at this mark's status or type.");
    }
    if (request.revisionCount() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revision count is required.");
    }
    List<Map<String, Object>> rows = jdbc.queryForList(ROW_SQL, new MapSqlParameterSource()
        .addValue("skey", skey)
        .addValue("timberMark", mark.timberMark())
        .addValue("certificate", mark.certificate()));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Land index not found on this mark.");
    }
    Map<String, Object> stored = rows.get(0);
    LocalDate deactivate = request.indexDeactivateDate();
    MapSqlParameterSource p = validated(
        mark, (String) stored.get("primary_land_index_code"),
        (String) stored.get("secondary_land_index_code"), request.primaryLandIndexCode(),
        request.secondaryLandIndexCode(), request.markLandIndexDesc(), userId)
        .addValue("skey", skey)
        .addValue("deactivate", deactivate, Types.DATE)
        .addValue("revisionCount", request.revisionCount());
    rejectDuplicate(p);
    int n;
    try {
      n = jdbc.update(UPDATE_SQL, p);
    } catch (DuplicateKeyException ex) {
      throw alreadyOnMark();
    }
    if (n == 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "This land index was changed by someone else. Reload the mark and try again.");
    }
  }

  private MarkDetailDto find(String id, boolean byCertificate) {
    return (byCertificate
            ? markDetailService.findByCertificate(id)
            : markDetailService.findByMarkNumber(id))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Private mark not found."));
  }

  /**
   * Fta511MarkLandIndexForm's checks; the parameters for the write when they pass. A code
   * the row already holds passes even if it has since expired.
   */
  private MapSqlParameterSource validated(
      MarkDetailDto mark,
      String storedPrimary,
      String storedSecondary,
      String primaryIn,
      String secondaryIn,
      String descriptionIn,
      String userId) {
    String primary = trim(primaryIn);
    String secondary = trim(secondaryIn);
    String description = trim(descriptionIn);

    List<String> e = new ArrayList<>();
    if (primary == null) {
      e.add("Land District/Island is required.");
    } else if (!checks.unchangedOrActive(storedPrimary, primary, PRIMARY_SQL)) {
      e.add("Land District/Island " + primary + " is not a current code.");
    }
    if (secondary != null && !checks.unchangedOrActive(storedSecondary, secondary, SECONDARY_SQL)) {
      e.add("Primary ID " + secondary + " is not a current code.");
    }
    if (description != null && description.length() > MAX_DESC) {
      e.add("Description can be at most " + MAX_DESC + " characters.");
    }
    if (!e.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", e));
    }

    return new MapSqlParameterSource()
        .addValue("certificate", mark.certificate())
        .addValue("timberMark", mark.timberMark())
        .addValue("primary", primary)
        .addValue("secondary", secondary)
        .addValue("description", description)
        .addValue("skey", null, Types.NUMERIC)
        .addValue("userId", userId);
  }

  private void rejectDuplicate(MapSqlParameterSource p) {
    Long dup = jdbc.queryForObject(DUPLICATE_SQL, p, Long.class);
    if (dup != null && dup > 0) {
      throw alreadyOnMark();
    }
  }

  private static ResponseStatusException alreadyOnMark() {
    return new ResponseStatusException(
        HttpStatus.CONFLICT, "This land index is already on the mark.");
  }
}
