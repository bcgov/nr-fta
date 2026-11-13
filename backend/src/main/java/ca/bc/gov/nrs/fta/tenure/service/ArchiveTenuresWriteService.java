package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresSummaryDto;
import java.sql.Types;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA640 Archive Tenures, ported from {@code THE.FTA_640_ARCHIVETEN}.
 *
 * <p>GET counts, per district, the active grazing (E01–E03) and hay (H01–H03)
 * range tenures in a region (or one of its districts) whose term expired in a
 * given year. SAVE sets every one of them to status {@code AR}, but only once
 * that year is over. Both match on the same criteria, so the archive changes
 * exactly what the count showed.
 *
 * <p>Legacy also matched on the user's own org unit (a district user saw their
 * district, a region user their region). Users here have no org unit, so the
 * region is always required, as it was for Headquarters users.
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class ArchiveTenuresWriteService {

  private static final ZoneId ZONE = ZoneId.of("America/Vancouver");

  /** The range tenures FTA640 covers, as {@code FTA_640_ARCHIVETEN} lists them. */
  private static final String CANDIDATES =
      """
       FROM the.prov_forest_use pfu
       JOIN the.tenure_term tt  ON tt.forest_file_id = pfu.forest_file_id
       JOIN the.range_tenure rt ON rt.forest_file_id = pfu.forest_file_id
       JOIN the.org_unit ou     ON ou.org_unit_no = rt.admin_forest_district_no
      WHERE pfu.file_status_st = 'A'
        AND pfu.file_type_code IN ('E01', 'E02', 'E03', 'H01', 'H02', 'H03')
        AND TO_CHAR(NVL(tt.current_expiry_dt, tt.initial_expiry_dt), 'YYYY') = :expiryYear
        AND ou.rollup_region_no = :regionNo
        AND (:districtNo IS NULL OR rt.admin_forest_district_no = :districtNo)
      """;

  // DISTINCT, so a file is counted once whatever its term rows.
  private static final String SUMMARY_SQL =
      """
      SELECT ou.org_unit_code || ' - ' || ou.org_unit_name AS district,
             COUNT(DISTINCT CASE WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'E'
                                 THEN pfu.forest_file_id END) AS grazing_count,
             COUNT(DISTINCT CASE WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'H'
                                 THEN pfu.forest_file_id END) AS hay_count
      """ + CANDIDATES + """
       GROUP BY ou.org_unit_code, ou.org_unit_name
       ORDER BY ou.org_unit_code
      """;

  private static final String ARCHIVE_SQL =
      """
      UPDATE the.prov_forest_use
         SET file_status_st = 'AR',
             file_status_date = TRUNC(SYSDATE),
             update_userid = :userId,
             update_timestamp = SYSDATE
       WHERE file_status_st = 'A'
         AND forest_file_id IN (SELECT pfu.forest_file_id
      """ + CANDIDATES + """
             )
      """;

  /** Sil_Get_Region_No: the region a district rolls up to. */
  private static final String DISTRICT_REGION_SQL =
      "SELECT rollup_region_no FROM the.org_unit WHERE org_unit_no = :districtNo";

  private final NamedParameterJdbcTemplate jdbc;

  public ArchiveTenuresWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The Go button: what an archive would change, per district.
   *
   * @throws ResponseStatusException 400 if the region or year is missing, or the
   *     district is not in the region
   */
  public List<ArchiveTenuresSummaryDto> summary(ArchiveTenuresRequest q) {
    MapSqlParameterSource p = criteria(q);
    return jdbc.query(SUMMARY_SQL, p, (rs, n) -> new ArchiveTenuresSummaryDto(
        rs.getString("district"), rs.getLong("grazing_count"), rs.getLong("hay_count")));
  }

  /**
   * The Archive button: sets every tenure the summary counts to status AR.
   * Returns how many were archived.
   *
   * @throws ResponseStatusException 400 if the criteria are invalid, the year is
   *     not over, or nothing matches (legacy's NO_DATA_FOUND)
   */
  @Transactional
  public int archive(ArchiveTenuresRequest q, String userId) {
    MapSqlParameterSource p = criteria(q).addValue("userId", userId);
    if (!ArchiveTenuresRules.yearIsOver(q.expiryYear(), Year.now(ZONE).getValue())) {
      throw bad(ArchiveTenuresRules.NOT_EXPIRED);
    }
    int updated = jdbc.update(ARCHIVE_SQL, p);
    if (updated == 0) {
      throw bad(ArchiveTenuresRules.NOTHING_TO_ARCHIVE);
    }
    return updated;
  }

  private MapSqlParameterSource criteria(ArchiveTenuresRequest q) {
    List<String> e = ArchiveTenuresRules.validate(q);
    if (!e.isEmpty()) {
      throw bad(String.join(" ", e));
    }
    if (q.districtNo() != null && !q.regionNo().equals(regionOf(q.districtNo()))) {
      throw bad(ArchiveTenuresRules.DISTRICT_NOT_IN_REGION);
    }
    return new MapSqlParameterSource()
        .addValue("regionNo", q.regionNo())
        // Typed, so Oracle can bind a null for ":districtNo IS NULL".
        .addValue("districtNo", q.districtNo(), Types.NUMERIC)
        .addValue("expiryYear", String.format("%04d", q.expiryYear()));
  }

  private Long regionOf(long districtNo) {
    try {
      return jdbc.queryForObject(
          DISTRICT_REGION_SQL, new MapSqlParameterSource("districtNo", districtNo), Long.class);
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  private static ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
