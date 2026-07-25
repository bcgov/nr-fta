package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for archiving tenures (FTA640).
 *
 * <p>The FTA640 screen is an explicit selection, so the archive is scoped to the
 * exact forest files the user picked ({@code forestFileIds}) rather than a broad
 * org/expiry-year filter. Only files still in status {@code A} among those listed
 * are flipped to status {@code AR} (archived), so the operation can never affect
 * more tenures than the user selected. The {@code file_status_st = 'A'} guard is
 * kept so an already-archived or otherwise non-active file is silently skipped.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class ArchiveTenuresWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public ArchiveTenuresWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String ARCHIVE_SQL =
      """
      UPDATE the.prov_forest_use
      SET file_status_st = 'AR'
        , file_status_date = TRUNC(SYSDATE)
        , update_userid = :userId
        , update_timestamp = SYSDATE
      WHERE file_status_st = 'A'
        AND forest_file_id IN (:forestFileIds)
      """;

  /**
   * Archive exactly the selected, still-active forest files. Returns the number
   * of tenures archived (rows updated) — which is at most the number of files
   * supplied.
   *
   * @param request the explicit list of forest files to archive
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public int archive(ArchiveTenuresRequest request, String userId) {
    List<String> ids = request.forestFileIds();
    if (ids == null || ids.isEmpty()) {
      return 0; // nothing selected — never fall through to an unscoped UPDATE
    }
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileIds", ids)
        .addValue("userId", userId);
    return jdbc.update(ARCHIVE_SQL, params);
  }
}
