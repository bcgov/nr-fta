package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Range files: which files are range files, and whether the user may edit one.
 *
 * <p><b>A range file</b> is one whose file type is in {@code RANGE_FILE_TYPE_CODE} — legacy's
 * {@code FTA_VALID_RANGE_FILE_TYPE}, which FTA100 used to send every range file to FTA100R.
 *
 * <p><b>Who may edit one</b> follows legacy FTA_100RANGE_TENURE.SET_AUTHORITY_IND: the user's
 * district must be the file's administering org ({@code PROV_FOREST_USE.FOREST_REGION}) or the
 * range tenure's district ({@code RANGE_TENURE.ADMIN_FOREST_DISTRICT_NO}). Here "the user's
 * district" is {@link LoggedUserHelper#mayEditRangeDistrict}: unscoped FTA_ADMIN and
 * FTA_RANGE_HEADQUARTERS_ADMIN pass for every file.
 *
 * <p>Range write endpoints call {@link #requireEditable} before saving.
 */
@Component
public class RangeFileAccess {

  static final String NOT_A_RANGE_FILE = "%s is not a range tenure.";
  static final String NOT_YOUR_DISTRICT =
      "You may edit only range tenures administered by your district.";

  /** FTA_VALID_RANGE_FILE_TYPE on the file's type. */
  static final String IS_RANGE_FILE_SQL =
      """
      SELECT COUNT(*)
        FROM the.prov_forest_use pfu
        JOIN the.range_file_type_code rftc ON rftc.range_file_type_code = pfu.file_type_code
       WHERE pfu.forest_file_id = :forestFileId
      """;

  /** The two districts SET_AUTHORITY_IND compares, as ORG_UNIT_CODEs. */
  static final String DISTRICTS_SQL =
      """
      SELECT adm.org_unit_code AS admin_org_unit_code,
             dst.org_unit_code AS district_org_unit_code
        FROM the.prov_forest_use pfu
        JOIN the.range_file_type_code rftc ON rftc.range_file_type_code = pfu.file_type_code
        LEFT JOIN the.org_unit adm    ON adm.org_unit_no = pfu.forest_region
        LEFT JOIN the.range_tenure rt ON rt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.org_unit dst    ON dst.org_unit_no = rt.admin_forest_district_no
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final LoggedUserHelper auth;

  public RangeFileAccess(NamedParameterJdbcTemplate jdbc, LoggedUserHelper auth) {
    this.jdbc = jdbc;
    this.auth = auth;
  }

  /** Whether the file exists and its type is a range file type. */
  public boolean isRangeFile(String forestFileId) {
    Integer count = jdbc.queryForObject(IS_RANGE_FILE_SQL, params(forestFileId), Integer.class);
    return count != null && count > 0;
  }

  /**
   * Whether the user may edit a range file with these districts.
   *
   * @param adminOrgUnitCode    the file's administering org (PROV_FOREST_USE.FOREST_REGION)
   * @param districtOrgUnitCode the range tenure's district, or null when it has none
   */
  public boolean mayEdit(String adminOrgUnitCode, String districtOrgUnitCode) {
    return auth.mayEditRangeDistrict(adminOrgUnitCode)
        || (districtOrgUnitCode != null && auth.mayEditRangeDistrict(districtOrgUnitCode));
  }

  /** Whether the file is a range file the user may edit. */
  public boolean mayEdit(String forestFileId) {
    List<Map<String, Object>> rows = jdbc.queryForList(DISTRICTS_SQL, params(forestFileId));
    if (rows.isEmpty()) {
      return false;
    }
    Map<String, Object> row = rows.get(0);
    return mayEdit(
        (String) row.get("admin_org_unit_code"), (String) row.get("district_org_unit_code"));
  }

  /**
   * For a range write endpoint, before it saves.
   *
   * @throws ResponseStatusException 400 if the file is not a range file (or does not exist), 403
   *     if the user may not edit it
   */
  public void requireEditable(String forestFileId) {
    List<Map<String, Object>> rows = jdbc.queryForList(DISTRICTS_SQL, params(forestFileId));
    if (rows.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, NOT_A_RANGE_FILE.formatted(forestFileId));
    }
    Map<String, Object> row = rows.get(0);
    if (!mayEdit(
        (String) row.get("admin_org_unit_code"), (String) row.get("district_org_unit_code"))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_YOUR_DISTRICT);
    }
  }

  private static MapSqlParameterSource params(String forestFileId) {
    return new MapSqlParameterSource("forestFileId", forestFileId);
  }
}
