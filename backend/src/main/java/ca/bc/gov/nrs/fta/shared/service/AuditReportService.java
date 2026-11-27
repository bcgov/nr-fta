package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.billing.service.BillingDistrictScope;
import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Criteria;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Report;
import ca.bc.gov.nrs.fta.shared.service.AuditReportRules.Scope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA675 Audit Report (Admin &gt; Audit Report): the FTA403 District Override Audit or the FTA405
 * Delete EE Audit, as a PDF, for a region and district (or "*"), an entry-date range and up to
 * five file types.
 *
 * <p>Ports {@code FTAR403P} (FDC_AUDIT_LOG, the before/after of each district override) and
 * {@code FTAR405P} (FTAEE_AUDIT_LOG, each deleted tenure, mark, permit or block); their
 * Sil_Get_Org_Unit_Code / Sil_Get_Org_Desc / Current_Server calls are joins here. The layouts
 * are the legacy Jasper Server reports. Legacy queued them on Jasper Server; they are produced
 * on request here, every row with no cap.
 *
 * <p>Legacy protected Region and District for a district user; a district-scoped administrator
 * here may report only on a district they administer, and not on "*".
 *
 * <p>Runs against the shared {@code THE} Oracle schema — there is no local database, so it is
 * exercised only in a deployed environment.
 */
@Service
public class AuditReportService {

  private static final String ORG_UNIT_SQL =
      "SELECT org_unit_code, org_unit_name FROM the.org_unit WHERE org_unit_no = :orgUnitNo";

  /**
   * validDistrictForRegion: the district is in the region's GET_ORG_UNIT_FILTERED list — a
   * region, district or HQ unit, not expired, rolling up to the region.
   */
  private static final String DISTRICT_IN_REGION_SQL =
      """
      SELECT COUNT(*)
        FROM the.org_unit d
        JOIN the.org_unit r ON r.org_unit_no = :regionNo
       WHERE d.org_unit_no = :districtNo
         AND d.rollup_region_code = r.org_unit_code
         AND d.expiry_date > SYSDATE
         AND (d.org_unit_no = d.rollup_region_no
              OR d.org_unit_no = d.rollup_dist_no
              OR (d.org_unit_code LIKE 'H%' AND LENGTH(d.org_unit_code) = 3))
      """;

  /** Each district's region, for the screen to check and lock its choices. */
  private static final String DISTRICT_REGIONS_SQL =
      """
      SELECT TO_CHAR(org_unit_no) AS district, TO_CHAR(rollup_region_no) AS region
        FROM the.org_unit
       WHERE org_unit_no = rollup_dist_no
         AND rollup_region_no IS NOT NULL
      """;

  private static final String SERVER_SQL = "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM dual";

  /** Every district office, and every region office (the procedures' "*" lists). */
  private static final String DISTRICT_OFFICES =
      """
      SELECT org_unit_no FROM the.org_unit
       WHERE org_level_code = 'D'
         AND org_unit_code = org_level_code || office_name_code
         AND org_unit_code >= 'DAA'""";

  private static final String REGION_OFFICES =
      """
      SELECT org_unit_no FROM the.org_unit
       WHERE org_level_code = 'R'
         AND org_unit_code = org_level_code || office_name_code
         AND org_unit_code >= 'RAA'""";

  /** A file type filter: "*" or none in the first means every type. */
  private static final String FILE_TYPE_FILTER =
      " AND (:allTypes = 'Y' OR %s IN (:ft1, :ft2, :ft3, :ft4, :ft5))";

  private static final String FTA403_SQL =
      """
      SELECT TO_CHAR(fal.org_unit_no)               AS org_unit_no,
             ou.org_unit_code                       AS org_unit_code,
             ou.org_unit_name                       AS org_unit_name,
             fal.forest_file_id                     AS forest_file_id,
             fal.timber_mark                        AS timber_mark,
             fal.cut_block_id                       AS cut_block_id,
             fal.b_file_status_st                   AS b_file_status_st,
             TRUNC(fal.b_file_tenure_term / 12)     AS b_file_tenure_term_yr,
             MOD(fal.b_file_tenure_term, 12)        AS b_file_tenure_term_mn,
             TO_CHAR(fal.b_file_award_dt, 'YYYY-MM-DD')    AS b_file_award_dt,
             TO_CHAR(fal.b_file_expiry_dt, 'YYYY-MM-DD')   AS b_file_expiry_dt,
             TO_CHAR(fal.b_file_extend_dt, 'YYYY-MM-DD')   AS b_file_extend_dt,
             fal.b_file_extend_cnt                  AS b_file_extend_cnt,
             fal.b_mark_status_st                   AS b_mark_status_st,
             TRUNC(fal.b_mark_tenure_term / 12)     AS b_mark_tenure_term_yr,
             MOD(fal.b_mark_tenure_term, 12)        AS b_mark_tenure_term_mn,
             TO_CHAR(fal.b_mark_issue_dt, 'YYYY-MM-DD')    AS b_mark_issue_dt,
             TO_CHAR(fal.b_mark_expiry_dt, 'YYYY-MM-DD')   AS b_mark_expiry_dt,
             TO_CHAR(fal.b_mark_extend_dt, 'YYYY-MM-DD')   AS b_mark_extend_dt,
             fal.b_mark_extend_cnt                  AS b_mark_extend_cnt,
             fal.b_block_status_st                  AS b_block_status_st,
             TO_CHAR(fal.b_harvest_start_dt, 'YYYY-MM-DD') AS b_harvest_start_dt,
             fal.b_logged_hectares                  AS b_logged_hectares,
             fal.b_sp_exempt_ind                    AS b_sp_exempt_ind,
             fal.a_file_status_st                   AS a_file_status_st,
             TRUNC(fal.a_file_tenure_term / 12)     AS a_file_tenure_term_yr,
             MOD(fal.a_file_tenure_term, 12)        AS a_file_tenure_term_mn,
             TO_CHAR(fal.a_file_award_dt, 'YYYY-MM-DD')    AS a_file_award_dt,
             TO_CHAR(fal.a_file_expiry_dt, 'YYYY-MM-DD')   AS a_file_expiry_dt,
             TO_CHAR(fal.a_file_extend_dt, 'YYYY-MM-DD')   AS a_file_extend_dt,
             fal.a_file_extend_cnt                  AS a_file_extend_cnt,
             fal.a_mark_status_st                   AS a_mark_status_st,
             TRUNC(fal.a_mark_tenure_term / 12)     AS a_mark_tenure_term_yr,
             MOD(fal.a_mark_tenure_term, 12)        AS a_mark_tenure_term_mn,
             TO_CHAR(fal.a_mark_issue_dt, 'YYYY-MM-DD')    AS a_mark_issue_dt,
             TO_CHAR(fal.a_mark_expiry_dt, 'YYYY-MM-DD')   AS a_mark_expiry_dt,
             TO_CHAR(fal.a_mark_extend_dt, 'YYYY-MM-DD')   AS a_mark_extend_dt,
             fal.a_mark_extend_cnt                  AS a_mark_extend_cnt,
             fal.a_block_status_st                  AS a_block_status_st,
             TO_CHAR(fal.a_harvest_start_dt, 'YYYY-MM-DD') AS a_harvest_start_dt,
             fal.a_logged_hectares                  AS a_logged_hectares,
             fal.a_sp_exempt_ind                    AS a_sp_exempt_ind,
             fal.change_comments                    AS change_comments,
             fal.entry_userid                       AS entry_userid,
             TO_CHAR(fal.entry_timestamp, 'YYYY-MM-DD HH24:MI:SS') AS entry_timestamp,
             pfu.file_type_code                     AS file_type_code
        FROM the.fdc_audit_log fal
        JOIN the.prov_forest_use pfu ON pfu.forest_file_id = fal.forest_file_id
        LEFT JOIN the.org_unit ou    ON ou.org_unit_no = fal.org_unit_no
       WHERE fal.entry_timestamp BETWEEN :fromTs AND :toTs
      """;

  private static final String FTA403_ORDER =
      " ORDER BY fal.org_unit_no, pfu.file_type_code, fal.entry_timestamp";

  private static final String FTA405_SQL =
      """
      SELECT TO_CHAR(fal.org_unit_no)  AS org_unit_no,
             ou.org_unit_code          AS org_unit_code,
             fal.forest_file_id        AS forest_file_id,
             fal.timber_mark           AS timber_mark,
             fal.cutting_permit_id     AS cutting_permit_id,
             fal.cut_block_id          AS cut_block_id,
             fal.file_type_code        AS file_type_code,
             fal.delete_option_cd      AS delete_option_cd,
             fal.entry_userid          AS entry_userid,
             fal.entry_timestamp       AS entry_timestamp
        FROM the.ftaee_audit_log fal
        LEFT JOIN the.org_unit ou ON ou.org_unit_no = fal.org_unit_no
       WHERE fal.entry_timestamp BETWEEN :fromTs AND :toTs
      """;

  private static final String FTA405_ORDER =
      " ORDER BY ou.org_unit_code, fal.file_type_code, fal.entry_timestamp";

  private static final List<String> FTA403_STRINGS = List.of(
      "org_unit_no", "org_unit_code", "org_unit_name", "forest_file_id", "timber_mark",
      "cut_block_id", "b_file_status_st", "b_file_tenure_term_yr", "b_file_tenure_term_mn",
      "b_file_award_dt", "b_file_expiry_dt", "b_file_extend_dt", "b_file_extend_cnt",
      "b_mark_status_st", "b_mark_tenure_term_yr", "b_mark_tenure_term_mn", "b_mark_issue_dt",
      "b_mark_expiry_dt", "b_mark_extend_dt", "b_mark_extend_cnt", "b_block_status_st",
      "b_harvest_start_dt", "b_logged_hectares", "b_sp_exempt_ind", "a_file_status_st",
      "a_file_tenure_term_yr", "a_file_tenure_term_mn", "a_file_award_dt", "a_file_expiry_dt",
      "a_file_extend_dt", "a_file_extend_cnt", "a_mark_status_st", "a_mark_tenure_term_yr",
      "a_mark_tenure_term_mn", "a_mark_issue_dt", "a_mark_expiry_dt", "a_mark_extend_dt",
      "a_mark_extend_cnt", "a_block_status_st", "a_harvest_start_dt", "a_logged_hectares",
      "a_sp_exempt_ind", "change_comments", "entry_userid", "entry_timestamp", "file_type_code");

  private static final List<String> FTA405_STRINGS = List.of(
      "org_unit_no", "org_unit_code", "forest_file_id", "timber_mark", "cutting_permit_id",
      "cut_block_id", "file_type_code", "delete_option_cd", "entry_userid");

  private final NamedParameterJdbcTemplate jdbc;
  private final BillingDistrictScope scope;

  public AuditReportService(NamedParameterJdbcTemplate jdbc, LoggedUserHelper auth) {
    this.jdbc = jdbc;
    // FTA_ADMIN's screen: the range headquarters role does not widen it.
    this.scope = BillingDistrictScope.adminOnly(auth);
  }

  /** The PDF and the name to save it under. */
  public record Pdf(String filename, byte[] content) {}

  /** Each district's ORG_UNIT_NO to its region's ORG_UNIT_NO. */
  public Map<String, String> districtRegions() {
    Map<String, String> out = new HashMap<>();
    jdbc.query(DISTRICT_REGIONS_SQL, Map.of(),
        rs -> {
          out.put(rs.getString("district"), rs.getString("region"));
        });
    return out;
  }

  /**
   * Submit.
   *
   * @param region     a region's ORG_UNIT_NO, or "*"
   * @param district   a district's ORG_UNIT_NO, or "*"
   * @param fromDate   YYYY-MM-DD, or blank for no start
   * @param toDate     YYYY-MM-DD, or blank for no end
   * @param fileTypes  up to five file types; "*" or blank first means every type
   * @throws ResponseStatusException 400 with the form's errors, 403 for a district the user
   *     does not administer or "*" from a district-scoped administrator
   */
  public Pdf render(String report, String region, String district, String fromDate,
      String toDate, List<String> fileTypes, String userId) {
    Criteria c = new Criteria(report, AuditReportRules.blank(region),
        AuditReportRules.blank(district), fromDate, toDate, fileTypes);
    List<String> errors = AuditReportRules.validate(c, districtInRegion(c));
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    Report r = Report.of(report);
    Scope s = Scope.of(c.region(), c.district());

    Map<String, Object> regionUnit = orgUnit(c.region());
    Map<String, Object> districtUnit = orgUnit(c.district());
    String regionCode = code(c.region(), regionUnit);
    String districtCode = code(c.district(), districtUnit);

    if (s != Scope.DISTRICT) {
      // Region or District "*": legacy protected both for a district user.
      scope.requireAll();
    }
    if (s == Scope.DISTRICT_ANY_REGION || s == Scope.DISTRICT) {
      scope.requireDistrict(districtCode);
    }

    List<String> types = AuditReportRules.compactFileTypes(fileTypes);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("fromTs", Timestamp.valueOf(AuditReportRules.from(fromDate)))
        .addValue("toTs", Timestamp.valueOf(AuditReportRules.to(r, toDate)))
        .addValue("allTypes", AuditReportRules.allFileTypes(types) ? "Y" : "N")
        .addValue("regionNo", number(c.region()), Types.NUMERIC)
        .addValue("districtNo", number(c.district()), Types.NUMERIC);
    for (int i = 0; i < types.size(); i++) {
      p.addValue("ft" + (i + 1), types.get(i).isEmpty() ? null : types.get(i), Types.VARCHAR);
    }
    String server = jdbc.queryForObject(SERVER_SQL, Map.of(), String.class);

    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", userId);
    params.put("p_region_code", regionCode);
    params.put("p_district_code", districtCode);
    List<Map<String, ?>> rows;
    if (r == Report.FTA403) {
      // FTA403's header prints a blank date or file type as nothing (it tests for null).
      params.put("p_from_date", nullIfBlank(fromDate));
      params.put("p_to_date", nullIfBlank(toDate));
      for (int i = 0; i < types.size(); i++) {
        params.put("p_file_type_code_" + (i + 1), types.get(i).isEmpty() ? null : types.get(i));
      }
      rows = fta403(p, s, server, regionCode, regionUnit, districtCode, districtUnit);
    } else {
      // FTA405's header calls isEmpty() on each, so a blank is "".
      params.put("p_from_date", AuditReportRules.blank(fromDate));
      params.put("p_to_date", AuditReportRules.blank(toDate));
      for (int i = 0; i < types.size(); i++) {
        params.put("p_file_type_code_" + (i + 1), types.get(i));
      }
      rows = fta405(p, s, server,
          AuditReportRules.outMessage(s, name(regionUnit), name(districtUnit)));
    }
    return new Pdf(r.filename, JasperTemplates.pdf(r.template, params, rows));
  }

  private List<Map<String, ?>> fta403(MapSqlParameterSource p, Scope s, String server,
      String regionCode, Map<String, Object> regionUnit, String districtCode,
      Map<String, Object> districtUnit) {
    String sql = FTA403_SQL + FILE_TYPE_FILTER.formatted("pfu.file_type_code")
        + " AND " + orgFilter(Report.FTA403, s) + FTA403_ORDER;
    List<Map<String, ?>> rows = new ArrayList<>();
    jdbc.query(sql, p, rs -> {
      Map<String, Object> row = strings(rs, FTA403_STRINGS);
      row.put("db_name", server);
      row.put("region_code", regionCode);
      row.put("region_name", name(regionUnit));
      row.put("district_code", districtCode);
      row.put("district_name", name(districtUnit));
      row.put("rep_from_date", String.valueOf(p.getValue("fromTs")));
      row.put("rep_to_date", String.valueOf(p.getValue("toTs")));
      rows.add(row);
    });
    return rows;
  }

  private List<Map<String, ?>> fta405(
      MapSqlParameterSource p, Scope s, String server, String outMessage) {
    String sql = FTA405_SQL + FILE_TYPE_FILTER.formatted("fal.file_type_code")
        + " AND " + orgFilter(Report.FTA405, s) + FTA405_ORDER;
    List<Map<String, ?>> rows = new ArrayList<>();
    jdbc.query(sql, p, rs -> {
      Map<String, Object> row = strings(rs, FTA405_STRINGS);
      row.put("entry_timestamp", rs.getTimestamp("entry_timestamp"));
      row.put("db_name", server);
      row.put("rep_from_date", String.valueOf(p.getValue("fromTs")));
      row.put("rep_to_date", String.valueOf(p.getValue("toTs")));
      row.put("out_message", outMessage);
      rows.add(row);
    });
    return rows;
  }

  /**
   * The procedures' org unit condition per scope. They differ in one case: with Region "*" and
   * one district, FTAR405P also lists every region office's deletions; FTAR403P does not.
   */
  static String orgFilter(Report report, Scope scope) {
    return switch (scope) {
      case ALL -> "fal.org_unit_no IN (" + DISTRICT_OFFICES + " UNION " + REGION_OFFICES + ")";
      case REGION ->
          "fal.org_unit_no IN (SELECT org_unit_no FROM the.org_unit"
              + " WHERE rollup_region_no = :regionNo)";
      case DISTRICT_ANY_REGION -> "fal.org_unit_no IN (" + DISTRICT_OFFICES
          + " AND rollup_dist_no = :districtNo"
          + (report == Report.FTA405 ? " UNION " + REGION_OFFICES : "") + ")";
      case DISTRICT -> "fal.org_unit_no = :districtNo";
    };
  }

  private boolean districtInRegion(Criteria c) {
    Long regionNo = number(c.region());
    Long districtNo = number(c.district());
    if (regionNo == null || districtNo == null) {
      return true;
    }
    Long n = jdbc.queryForObject(DISTRICT_IN_REGION_SQL,
        Map.of("regionNo", regionNo, "districtNo", districtNo), Long.class);
    return n != null && n > 0;
  }

  /** The org unit's code and name, or an empty map for "*" or an unknown number. */
  private Map<String, Object> orgUnit(String value) {
    Long no = number(value);
    if (no == null) {
      return Map.of();
    }
    List<Map<String, Object>> found = jdbc.queryForList(ORG_UNIT_SQL, Map.of("orgUnitNo", no));
    return found.isEmpty() ? Map.of() : found.get(0);
  }

  /** What the report prints for a region or district: its code, or "*". */
  private static String code(String value, Map<String, Object> unit) {
    if (AuditReportRules.ALL.equals(value)) {
      return AuditReportRules.ALL;
    }
    Object code = unit.get("ORG_UNIT_CODE");
    return code == null ? value : code.toString();
  }

  private static String name(Map<String, Object> unit) {
    Object name = unit.get("ORG_UNIT_NAME");
    return name == null ? null : name.toString();
  }

  private static Long number(String value) {
    return value != null && value.matches("\\d+") ? Long.valueOf(value) : null;
  }

  private static String nullIfBlank(String s) {
    String v = AuditReportRules.blank(s);
    return v.isEmpty() ? null : v;
  }

  private static Map<String, Object> strings(ResultSet rs, List<String> columns)
      throws SQLException {
    Map<String, Object> row = new HashMap<>();
    for (String col : columns) {
      row.put(col, rs.getString(col));
    }
    return row;
  }
}
