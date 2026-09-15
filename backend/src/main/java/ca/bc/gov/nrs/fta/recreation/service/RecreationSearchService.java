package ca.bc.gov.nrs.fta.recreation.service;

import ca.bc.gov.nrs.fta.recreation.dto.RecreationSearchCriteria;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA007 Recreation Search.
 *
 * <p>Ports {@code THE.FTA_007_REC_SEARCH.MAINLINE}. Recreation files come in
 * two generations and the legacy package queries them as two statements joined
 * by {@code UNION}: "new" files in {@code RECREATION_PROJECT} (ids like
 * {@code REC%}) and "old" files in {@code REC_PROJECT} (ids like {@code 900%}).
 * Both take their status, management unit and org unit from
 * {@code PROV_FOREST_USE} — {@code REC_PROJECT} has no status column of its own.
 *
 * <h2>Deliberate departures from legacy</h2>
 *
 * <ul>
 *   <li><b>Bound parameters.</b> Legacy concatenates every criterion into three
 *       {@code EXECUTE IMMEDIATE} statements with no {@code USING} clause, so a
 *       quote in a project name breaks or hijacks the query. Everything here is
 *       bound.
 *   <li><b>No staging table.</b> Legacy inserts into the global temporary table
 *       {@code RECREATION_SEARCH_RESULT} and selects it back. That table is not
 *       granted INSERT to the application role — it only works in legacy because
 *       the insert runs inside the package under definer's rights — and a
 *       session-scoped temp table is the wrong shape for a pooled, stateless
 *       service. Querying the sources directly avoids both problems.
 *   <li><b>Deterministic ordering.</b> Legacy's four named sorts have an
 *       {@code ORDER BY}; its fallback branch has none. Every sort here ends
 *       with the file id so paging cannot repeat or skip rows.
 *   <li><b>Dead join dropped.</b> {@code tenure_application_type_code} is joined
 *       twice in legacy and never selected or filtered on.
 * </ul>
 *
 * <h2>Legacy behaviour kept on purpose</h2>
 *
 * <ul>
 *   <li>{@code fileId} matches with {@code LIKE} but no wildcard is added, so it
 *       is an exact match unless the user types {@code %} — which the legacy
 *       validator explicitly permits.
 *   <li>{@code definedCampingSpaces} is a strict "more than", not an equality:
 *       legacy asks {@code :n < (SELECT COUNT(*) ...)}.
 *   <li>Four criteria — risk rating, controlled access, maintenance standard and
 *       resource feature — exist only on {@code RECREATION_PROJECT}. They cannot
 *       be applied to old files because {@code REC_PROJECT} has no such columns.
 *       Legacy drops them silently; {@link #appliesOnlyToNewFiles} lets the
 *       caller say so instead.
 * </ul>
 */
@Service
public class RecreationSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public RecreationSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** New-generation files: RECREATION_PROJECT, ids like REC%. */
  private static final String FROM_NEW =
      """
        FROM the.prov_forest_use pfu
        LEFT JOIN the.recreation_project rp ON rp.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.org_unit ou           ON ou.org_unit_no = pfu.forest_region
        LEFT JOIN the.tenure_application ta ON ta.forest_file_id = rp.forest_file_id
        LEFT JOIN the.tenure_application_map_feature tamf ON tamf.tenure_app_id = ta.tenure_app_id
        LEFT JOIN the.fta_map_feature_code fmfc
               ON fmfc.fta_map_feature_code = tamf.feature_type_code
       WHERE pfu.forest_file_id LIKE 'REC%'
         AND pfu.file_type_code LIKE 'F%'
      """;

  /** Old-generation files: REC_PROJECT, ids like 900%. */
  private static final String FROM_OLD =
      """
        FROM the.prov_forest_use pfu
        LEFT JOIN the.rec_project orp ON orp.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.org_unit ou     ON ou.org_unit_no = orp.org_unit_no
        LEFT JOIN the.tenure_application ta ON ta.forest_file_id = orp.forest_file_id
        LEFT JOIN the.tenure_application_map_feature tamf ON tamf.tenure_app_id = ta.tenure_app_id
        LEFT JOIN the.fta_map_feature_code fmfc
               ON fmfc.fta_map_feature_code = tamf.feature_type_code
       WHERE pfu.forest_file_id LIKE '900%'
         AND pfu.file_type_code LIKE 'F%'
      """;

  private static final String SELECT_NEW =
      """
      SELECT DISTINCT pfu.forest_file_id AS forest_file_id,
             pfu.file_status_st          AS file_status_code,
             ou.org_unit_code            AS org_unit_code,
             ou.org_unit_name            AS org_unit_name,
             rp.project_name             AS project_name,
             fmfc.description            AS project_type
      """;

  private static final String SELECT_OLD =
      """
      SELECT DISTINCT pfu.forest_file_id AS forest_file_id,
             pfu.file_status_st          AS file_status_code,
             ou.org_unit_code            AS org_unit_code,
             ou.org_unit_name            AS org_unit_name,
             orp.rec_project_name        AS project_name,
             fmfc.description            AS project_type
      """;

  /**
   * Whether the search carries criteria that only the newer table supports
   * while also asking for old files — in which case those criteria cannot be
   * honoured and the caller should say so.
   */
  public boolean appliesOnlyToNewFiles(RecreationSearchCriteria c) {
    return RecreationSearchCriteria.OLD_FILES.equals(c.oldFileInd())
        && c.hasNewFileOnlyCriteria();
  }

  /** Runs the search, returning one page of results. */
  public PagedResponse<RecreationSearchDto> search(
      RecreationSearchCriteria c, int page, int size) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    String common = commonPredicates(c, params);

    boolean wantsNew = !RecreationSearchCriteria.OLD_FILES.equals(c.oldFileInd());
    boolean wantsOld = !RecreationSearchCriteria.NEW_FILES.equals(c.oldFileInd());

    List<String> branches = new ArrayList<>();
    if (wantsNew) {
      branches.add(SELECT_NEW + FROM_NEW + common + newOnlyPredicates(c, params, "rp"));
    }
    if (wantsOld) {
      branches.add(SELECT_OLD + FROM_OLD + common + oldOnlyPredicates(c, params, "orp"));
    }
    String body = String.join("\n UNION \n", branches);

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM (" + body + ")", params, Long.class);
    long totalElements = total == null ? 0L : total;
    if (totalElements == 0) {
      return PagedResponse.ofPage(List.of(), page, size, 0);
    }

    String sql = "SELECT * FROM (" + body + ")" + orderBy(c)
        + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";
    params.addValue("offset", (long) page * size).addValue("limit", size);

    List<RecreationSearchDto> rows = jdbc.query(sql, params, (rs, n) ->
        new RecreationSearchDto(
            rs.getString("forest_file_id"),
            rs.getString("file_status_code"),
            rs.getString("org_unit_code"),
            rs.getString("org_unit_name"),
            rs.getString("project_name"),
            rs.getString("project_type")));

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /** Predicates that apply to both generations, all against pfu/ou/tamf. */
  private static String commonPredicates(
      RecreationSearchCriteria c, MapSqlParameterSource p) {
    StringBuilder w = new StringBuilder();

    if (notBlank(c.fileId())) {
      // No wildcard is appended, matching legacy: an exact match unless the
      // user supplies '%' themselves.
      p.addValue("fileId", c.fileId().trim().toUpperCase());
      w.append("   AND pfu.forest_file_id LIKE :fileId\n");
    }
    if (notBlank(c.orgUnit())) {
      // A region matches every org unit rolling up to it; a district matches
      // itself. Legacy interpolates this unquoted in one branch and quoted in
      // the other; both are bound here.
      p.addValue("orgUnit", c.orgUnit().trim());
      w.append("""
             AND ( ( the.sil_get_org_level(:orgUnit) = 'R'
                     AND ou.org_unit_no IN (SELECT org_unit_no
                                              FROM the.org_unit
                                             WHERE rollup_region_no = TO_NUMBER(:orgUnit)) )
                   OR ( the.sil_get_org_level(:orgUnit) != 'R'
                        AND ou.org_unit_no = TO_NUMBER(:orgUnit) ) )
          """);
    }
    if (notBlank(c.mgmtUnitType())) {
      p.addValue("mgmtUnitType", c.mgmtUnitType().trim().toUpperCase());
      w.append("   AND pfu.mgmt_unit_type = :mgmtUnitType\n");
    }
    if (notBlank(c.mgmtUnitNumber())) {
      p.addValue("mgmtUnitNumber", c.mgmtUnitNumber().trim().toUpperCase());
      w.append("   AND pfu.mgmt_unit_id = :mgmtUnitNumber\n");
    }
    if (notBlank(c.fileStatus())) {
      p.addValue("fileStatus", c.fileStatus().trim());
      w.append("   AND pfu.file_status_st = :fileStatus\n");
    }
    if (notBlank(c.fileStatusFrom())) {
      p.addValue("fileStatusFrom", c.fileStatusFrom().trim());
      w.append("   AND pfu.file_status_date >= TO_DATE(:fileStatusFrom, 'YYYY-MM-DD')\n");
    }
    if (notBlank(c.fileStatusTo())) {
      p.addValue("fileStatusTo", c.fileStatusTo().trim());
      w.append("   AND pfu.file_status_date <= TO_DATE(:fileStatusTo, 'YYYY-MM-DD')\n");
    }
    if (notBlank(c.projectType())) {
      p.addValue("projectType", c.projectType().trim());
      w.append("   AND tamf.feature_type_code = :projectType\n");
    }
    if (notBlank(c.recreationDistrict())) {
      p.addValue("recreationDistrict", c.recreationDistrict().trim());
      w.append("""
             AND EXISTS (SELECT 1 FROM the.recreation_district_xref rdx
                          WHERE rdx.forest_file_id = pfu.forest_file_id
                            AND rdx.recreation_district_code = :recreationDistrict)
          """);
    }
    return w.toString();
  }

  /** Predicates valid only against RECREATION_PROJECT. */
  private static String newOnlyPredicates(
      RecreationSearchCriteria c, MapSqlParameterSource p, String alias) {
    StringBuilder w = new StringBuilder(nameAndCampsites(c, p, alias, "project_name"));
    if (notBlank(c.riskRating())) {
      p.addValue("riskRating", c.riskRating().trim());
      w.append("   AND rp.recreation_risk_rating_code = :riskRating\n");
    }
    if (notBlank(c.controlledAccessType())) {
      p.addValue("controlledAccess", c.controlledAccessType().trim());
      w.append("   AND rp.recreation_control_access_code = :controlledAccess\n");
    }
    if (notBlank(c.maintenanceStandard())) {
      p.addValue("maintenanceStandard", c.maintenanceStandard().trim());
      w.append("   AND rp.recreation_maintain_std_code = :maintenanceStandard\n");
    }
    if (notBlank(c.resourceFeatureInd())) {
      p.addValue("resourceFeature", c.resourceFeatureInd().trim());
      w.append("   AND rp.resource_feature_ind = :resourceFeature\n");
    }
    return w.toString();
  }

  /** Predicates valid only against REC_PROJECT. */
  private static String oldOnlyPredicates(
      RecreationSearchCriteria c, MapSqlParameterSource p, String alias) {
    return nameAndCampsites(c, p, alias, "rec_project_name");
  }

  /**
   * The two criteria that exist on both generations but under different column
   * names, so each branch binds its own.
   */
  private static String nameAndCampsites(
      RecreationSearchCriteria c, MapSqlParameterSource p, String alias, String nameColumn) {
    StringBuilder w = new StringBuilder();
    if (notBlank(c.projectName())) {
      p.addValue("projectName", "%" + c.projectName().trim().toUpperCase() + "%");
      w.append("   AND UPPER(").append(alias).append('.').append(nameColumn)
          .append(") LIKE :projectName\n");
    }
    if (notBlank(c.definedCampingSpaces())) {
      // Strictly "more than", as legacy has it — not an equality, despite the
      // screen label reading as one.
      p.addValue("campingSpaces", Integer.valueOf(c.definedCampingSpaces().trim()));
      w.append("   AND :campingSpaces < (SELECT COUNT(*) FROM the.recreation_defined_campsite rdc")
          .append(" WHERE rdc.forest_file_id = ").append(alias).append(".forest_file_id)\n");
    }
    return w.toString();
  }

  /**
   * The legacy sorts, each given a stable tiebreaker. Legacy's fallback branch
   * has no {@code ORDER BY} at all, which is harmless for a cursor read straight
   * through but not for paging.
   */
  private static String orderBy(RecreationSearchCriteria c) {
    String key = switch (c.sortBy() == null ? "" : c.sortBy()) {
      case RecreationSearchCriteria.SORT_ADMIN_ORG -> "org_unit_name";
      case RecreationSearchCriteria.SORT_FILE_STATUS -> "file_status_code";
      case RecreationSearchCriteria.SORT_PROJECT_NAME -> "project_name";
      default -> "forest_file_id";
    };
    return key.equals("forest_file_id")
        ? " ORDER BY forest_file_id"
        : " ORDER BY " + key + ", forest_file_id";
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
