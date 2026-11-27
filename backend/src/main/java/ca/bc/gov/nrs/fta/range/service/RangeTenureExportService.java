package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The FTA001 Range Tenure Export: legacy's {@code FTA001_RangeTenureExport} Excel workbook,
 * which districts mail-merged into legal documents.
 *
 * <p>Legacy ran it two ways, both Export buttons:
 * <ul>
 *   <li>on FTA001R search, with the search criteria
 *       ({@code Fta001RangeTenureSearchAction.handleSubmitReport});</li>
 *   <li>on a file's 100R, 613R, 615R and 920R tabs, with only the file id and the file's
 *       administration org unit ({@code PROV_FOREST_USE.FOREST_REGION}, the tab set's
 *       {@code FtaBucket} admin org).</li>
 * </ul>
 */
@Service
public class RangeTenureExportService {

  private static final String ADMIN_ORG_SQL =
      """
      SELECT pfu.forest_region
        FROM the.prov_forest_use pfu
       WHERE pfu.forest_file_id = :forestFileId
      """;

  private final RangeTenureExportLegacy legacy;
  private final RangeFileAccess access;
  private final NamedParameterJdbcTemplate jdbc;
  private final LoggedUserHelper auth;

  public RangeTenureExportService(
      RangeTenureExportLegacy legacy,
      RangeFileAccess access,
      NamedParameterJdbcTemplate jdbc,
      LoggedUserHelper auth) {
    this.legacy = legacy;
    this.access = access;
    this.jdbc = jdbc;
    this.auth = auth;
  }

  /**
   * The search export.
   *
   * <p>Four of the procedures (CSV5, CSV8, CSV9, CSV10) splice the org unit into their client
   * name subquery unconditionally, so without one their SQL does not parse and legacy's report
   * failed. That is refused up front instead.
   *
   * @throws ResponseStatusException 400 without an administration org unit, or with a malformed
   *                                 number or date
   */
  public byte[] search(RangeTenureExportCriteria criteria) {
    if (criteria.adminOrgUnitNo() == null || criteria.adminOrgUnitNo().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Choose an administration organization unit to export.");
    }
    return legacy.xlsx(criteria, auth.getLoggedUserId());
  }

  /**
   * One file's export.
   *
   * @throws ResponseStatusException 404 when the file is unknown or not a range file
   */
  public byte[] file(String forestFileId) {
    if (forestFileId == null || forestFileId.isBlank() || !access.isRangeFile(forestFileId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    List<String> orgs = jdbc.queryForList(ADMIN_ORG_SQL,
        new MapSqlParameterSource("forestFileId", forestFileId), String.class);
    String adminOrg = orgs.isEmpty() ? null : orgs.get(0);
    return legacy.xlsx(
        RangeTenureExportCriteria.forFile(forestFileId, adminOrg), auth.getLoggedUserId());
  }
}
