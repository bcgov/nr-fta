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
      "SELECT COUNT(*) FROM the.forest_file WHERE forest_file_id = :forestFileId";

  private static final String INSERT_SQL =
      """
      INSERT INTO the.forest_file (
        forest_file_id, file_type_code, admin_district_no,
        file_status_st, file_issue_date, entry_userid, entry_timestamp,
        update_userid, update_timestamp
      ) VALUES (
        :forestFileId, :fileTypeCode, :adminDistrictNo,
        'PA', :issueDate, :userId, SYSDATE, :userId, SYSDATE
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

    // Resolve the admin district from the org-unit code up front so an unknown
    // code is rejected with a 400 instead of inserting a NULL admin_district_no.
    Long adminDistrictNo;
    try {
      adminDistrictNo = jdbc.queryForObject(
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
        .addValue("adminDistrictNo", adminDistrictNo)
        .addValue("issueDate", request.issueDate())
        .addValue("userId", userId);
    try {
      jdbc.update(INSERT_SQL, params);
    } catch (DuplicateKeyException e) {
      // Race: another request inserted the same id between the check and here.
      throw new DuplicateDataException("Forest file " + forestFileId + " already exists", e);
    }
    return forestFileId;
  }
}
