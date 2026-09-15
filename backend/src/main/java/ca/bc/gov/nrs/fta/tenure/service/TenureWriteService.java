package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.exception.DuplicateDataException;
import ca.bc.gov.nrs.fta.tenure.dto.CreateTenureRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write operations for the tenure spine.
 *
 * <p>Ports the create path of the legacy add-tenure flow to an INSERT against
 * {@code THE.FOREST_FILE} via {@link NamedParameterJdbcTemplate}. Runs against
 * the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 *
 * <p>The create is guarded so client mistakes surface as clean 4xx responses
 * rather than raw Oracle errors: an unknown org-unit code is a {@code 400}
 * (instead of silently inserting a NULL district), and a duplicate forest-file
 * id is a {@code 409} (instead of an {@code ORA-00001} surfaced as a 500).
 */
@Service
public class TenureWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public TenureWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String RESOLVE_DISTRICT_SQL =
      "SELECT org_unit_no FROM the.org_unit WHERE org_unit_code = :orgUnitCode";

  private static final String EXISTS_SQL =
      "SELECT COUNT(*) FROM the.prov_forest_use WHERE forest_file_id = :forestFileId";

  // The file header is THE.PROV_FOREST_USE — there is no THE.FOREST_FILE. The
  // column list mirrors the legacy THE.FTA_CREATE_PROV_FOREST_USE procedure:
  //
  //   * FOREST_REGION is the org-unit key (NUMBER, NOT NULL). The table has no
  //     "admin district" column at all; DISTRICT_ADMIN_ZONE is an optional
  //     free-text district label, not a foreign key, so the resolved org unit
  //     belongs here. FTA_001_TENR_SRCH joins it the same way
  //     ("org.org_unit_no = pfu.forest_region").
  //   * SB_FUNDED_IND is NOT NULL and defaulted 'N'; set explicitly.
  //   * REVISION_COUNT is NOT NULL and starts at 0, as the legacy procedure
  //     does — the ministry packages increment it for optimistic locking.
  //   * FILE_STATUS_DATE records when the status was set; it is not the issue
  //     date. See TERM_INSERT_SQL for where the issue date actually lives.
  private static final String INSERT_SQL =
      """
      INSERT INTO the.prov_forest_use (
        forest_file_id, file_type_code, forest_region,
        file_status_st, file_status_date, sb_funded_ind, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :forestFileId, :fileTypeCode, :forestRegion,
        'PA', SYSDATE, 'N', 0,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  // The issue date is not part of the file header: FTA_001_TENR_SRCH derives
  // file_issue_date from TENURE_TERM.legal_effective_dt (and file_expiry_date
  // from NVL(current_expiry_dt, initial_expiry_dt)). A term row is written only
  // when the caller supplied an issue date, so a tenure created without one
  // simply has no term yet rather than a row full of nulls.
  private static final String TERM_INSERT_SQL =
      """
      INSERT INTO the.tenure_term (
        forest_file_id, legal_effective_dt, revision_count,
        entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        :forestFileId, :issueDate, 0,
        :userId, SYSDATE, :userId, SYSDATE
      )
      """;

  /**
   * Create a new forest file. Returns the forest-file id of the created record.
   *
   * @param request the tenure to create
   * @param userId  the authenticated user id (audit columns)
   * @throws ResponseStatusException 400 if the forest-file id is blank or the
   *     org-unit code is unknown
   * @throws DuplicateDataException 409 if the forest-file id already exists
   */
  @Transactional
  public String create(CreateTenureRequest request, String userId) {
    String forestFileId = request.forestFileId();
    if (forestFileId == null || forestFileId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "forestFileId is required");
    }

    // Resolve the org unit up front so an unknown code is rejected with a 400.
    // forest_region is NOT NULL, so this cannot be allowed to fall through.
    Long forestRegion;
    try {
      forestRegion = jdbc.queryForObject(
          RESOLVE_DISTRICT_SQL,
          new MapSqlParameterSource("orgUnitCode", request.orgUnitCode()),
          Long.class);
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unknown org unit code: " + request.orgUnitCode());
    }

    // Reject a duplicate up front with a clean 409.
    Integer existing = jdbc.queryForObject(
        EXISTS_SQL, new MapSqlParameterSource("forestFileId", forestFileId), Integer.class);
    if (existing != null && existing > 0) {
      throw new DuplicateDataException("Forest file " + forestFileId + " already exists");
    }

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("fileTypeCode", request.fileTypeCode())
        .addValue("forestRegion", forestRegion)
        .addValue("userId", userId);
    try {
      jdbc.update(INSERT_SQL, params);
    } catch (DuplicateKeyException e) {
      // Race: another request inserted the same id between the check and here.
      throw new DuplicateDataException("Forest file " + forestFileId + " already exists", e);
    }

    // Same transaction: the term carries the issue date, so a failure here must
    // not leave a header behind claiming a date it never stored.
    if (request.issueDate() != null) {
      jdbc.update(TERM_INSERT_SQL, new MapSqlParameterSource()
          .addValue("forestFileId", forestFileId)
          .addValue("issueDate", request.issueDate())
          .addValue("userId", userId));
    }
    return forestFileId;
  }
}
