package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeLandBaseDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeSpecialConditionDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeUsageDto;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Range tenure detail business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_100RANGE_TENURE} (range
 * tenure detail) plus its two sibling tab packages
 * {@code THE.FTA_613R_RANGE_USAGE} and {@code THE.FTA_615_RANGE_LAND_BASE} to
 * native queries against the shared {@code THE} schema. Each query mirrors the
 * relevant procedure body:
 *
 * <ul>
 *   <li>the header mirrors {@code FTA_100RANGE_TENURE.GET} +
 *       {@code GET_RANGE_TENURE} (PROV_FOREST_USE / TENURE_TERM / RANGE_TENURE),
 *       with its helper functions inlined: {@code FTA_GET_MAIN_LICENSEE},
 *       {@code FTA_GET_MGMT_UNIT_DESC}, {@code FTA_VALID_RANGE_FILE_TYPE}, and
 *       the secondary-licensee and notes-attached counts;</li>
 *   <li>the Range Usage tab mirrors {@code FTA_613R_RANGE_USAGE.GET}: the
 *       RANGE_PROVISION years within the tenure term, ascending;</li>
 *   <li>the usage history mirrors {@code FTA_613R_RANGE_USAGE.GET_HISTORY}:
 *       every year, newest first;</li>
 *   <li>land base mirrors {@code FTA_615_RANGE_LAND_BASE.GET_LAND_BASE};</li>
 *   <li>special conditions mirror
 *       {@code FTA_100RANGE_TENURE.GET_SPECIAL_CONDITIONS}.</li>
 * </ul>
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RangeTenureDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public RangeTenureDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // GET reads the zone from PROV_FOREST_USE, then GET_RANGE_TENURE overwrites it
  // (and supplies the district) from RANGE_TENURE; with no RANGE_TENURE row the
  // select finds nothing and the PROV_FOREST_USE zone stands.
  static final String HEADER_SQL =
      """
      SELECT pfu.forest_file_id            AS forest_file_id,
             pfu.file_type_code            AS file_type_code,
             pfu.forest_region             AS admin_org_unit_no,
             rt.admin_forest_district_no   AS forest_district_no,
             CASE WHEN rt.forest_file_id IS NOT NULL
                  THEN rt.district_admn_zone
                  ELSE pfu.district_admin_zone END AS district_admin_zone,
             -- FTA_GET_MAIN_LICENSEE: the first type A client, "number locn  name".
             (SELECT ffc.client_number || ' ' || ffc.client_locn_code || '  ' || {CLIENT_NAME}
                FROM the.forest_file_client ffc
                LEFT JOIN the.forest_client cli ON cli.client_number = ffc.client_number
               WHERE ffc.forest_file_id = pfu.forest_file_id
                 AND ffc.forest_file_client_type_code = 'A'
                 AND ROWNUM = 1)           AS licensee,
             (SELECT DECODE(COUNT(*), 0, 'N', 'Y')
                FROM the.forest_file_client sec
               WHERE sec.forest_file_id = pfu.forest_file_id
                 AND sec.forest_file_client_type_code = 'B') AS sec_licensee_ind,
             (SELECT DECODE(COUNT(*), 0, ' ', 'Note(s) Attached')
                FROM the.provforest_note pn
               WHERE pn.forest_file_id = pfu.forest_file_id) AS notes_label,
             pfu.file_status_st            AS file_status_st,
             pfu.file_status_date          AS file_status_date,
             pfu.mgmt_unit_type            AS mgmt_unit_type,
             pfu.mgmt_unit_id              AS mgmt_unit_id,
             -- FTA_GET_MGMT_UNIT_DESC: the id may be blank, hence the NVLs.
             (SELECT fmu.description
                FROM the.forest_mgmt_unit fmu
               WHERE fmu.mgmt_unit_type_code = UPPER(pfu.mgmt_unit_type)
                 AND NVL(fmu.mgmt_unit_id, ' ') = NVL(pfu.mgmt_unit_id, ' ')
                 AND ROWNUM = 1)           AS mgmt_unit_name,
             rt.file_name                  AS file_name,
             tt.legal_effective_dt         AS issue_date,
             tt.initial_expiry_dt          AS expiry_date,
             -- FTA_GET_FILE_HEADER's expiry, which bounds the Range Usage tab.
             NVL(tt.current_expiry_dt, tt.initial_expiry_dt) AS usage_expiry_date,
             rt.original_issuance_date     AS original_issue_date,
             FLOOR(NVL(tt.tenure_term, 0) / 12) AS tenure_term_years,
             MOD(NVL(tt.tenure_term, 0), 12)    AS tenure_term_months,
             rt.replacement_term           AS replacement_term_years,
             NVL(tt.tenure_extend_cnt, 0)  AS replacement_count,
             -- FTA_VALID_RANGE_FILE_TYPE
             (SELECT DECODE(COUNT(*), 0, 'N', 'Y')
                FROM the.range_file_type_code rftc
               WHERE rftc.range_file_type_code = pfu.file_type_code) AS range_ind
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_term tt      ON tt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.range_tenure rt     ON rt.forest_file_id = pfu.forest_file_id
       WHERE pfu.forest_file_id = :agreementId
      """.replace("{CLIENT_NAME}", ClientNameSql.displayName("cli"));

  private static final String USAGE_COLUMNS =
      """
      SELECT rp.forest_file_id             AS forest_file_id,
             rp.calendar_year              AS calendar_year,
             rp.authorized_use             AS authorized_use,
             rp.non_use_agreement_no       AS non_use_agreement_no,
             rp.non_use_nonbillable        AS non_use_nonbillable,
             rp.non_use_billable           AS non_use_billable,
             rp.range_non_use_reason_code  AS range_non_use_reason_code,
             nurc.description              AS range_non_use_reason_desc,
             rp.temp_increase              AS temp_increase,
             rp.range_increase_reason_code AS range_increase_reason_code,
             irc.description               AS range_increase_reason_desc,
             rp.total_annual_use           AS total_annual_use,
             rp.revision_count             AS revision_count
        FROM the.range_provision rp
        LEFT JOIN the.range_non_use_reason_code nurc
               ON nurc.range_non_use_reason_code = rp.range_non_use_reason_code
        LEFT JOIN the.range_increase_reason_code irc
               ON irc.range_increase_reason_code = rp.range_increase_reason_code
       WHERE rp.forest_file_id = :agreementId
      """;

  /** FTA_613R_RANGE_USAGE.GET: the years of the tenure term, ascending. */
  static final String USAGE_SQL =
      USAGE_COLUMNS
          + """
               AND rp.calendar_year >= :startYear
               AND rp.calendar_year <= :expiryYear
             ORDER BY rp.calendar_year
            """;

  /** FTA_613R_RANGE_USAGE.GET_HISTORY: every year, newest first. */
  static final String USAGE_HISTORY_SQL =
      USAGE_COLUMNS
          + """
             ORDER BY rp.calendar_year DESC
            """;

  private static final String LAND_BASE_SQL =
      """
      SELECT ran.rlb_skey                        AS land_base_skey,
             ran.range_land_base_pid             AS range_land_base_pid,
             ran.range_land_base_id              AS range_land_base_id,
             ran.description                     AS description,
             ran.range_land_ownership_type_code  AS range_land_ownership_type_code,
             ran.range_land_purpose_code         AS range_land_purpose_code,
             ran.fenced_pasture                  AS fenced_pasture,
             ran.unfenced_pasture                AS unfenced_pasture,
             ran.forage_production               AS forage_production,
             ran.land_base_comment               AS land_base_comment,
             ran.lease_start_date                AS lease_start_date,
             ran.lease_end_date                  AS lease_end_date,
             ran.range_land_base_act_ind         AS range_land_base_act_ind,
             oc.description                      AS range_land_ownership_type_desc,
             pc.description                      AS range_land_purpose_desc,
             ran.revision_count                  AS revision_count
        FROM the.range_land_base ran
        LEFT OUTER JOIN the.range_land_ownership_type_code oc
          ON ran.range_land_ownership_type_code = oc.range_land_ownership_type_code
        LEFT OUTER JOIN the.range_land_purpose_code pc
          ON ran.range_land_purpose_code = pc.range_land_purpose_code
       WHERE ran.forest_file_id = :agreementId
         AND ran.range_land_base_act_ind = 'Y'
       ORDER BY ran.range_land_base_id
      """;

  private static final String SPECIAL_CONDITION_SQL =
      """
      SELECT rsp.rtspc_skey                   AS special_condition_skey,
             rsp.range_special_condition_code AS range_special_condition_code,
             rsp.condition_title              AS condition_title,
             rsp.condition_description        AS condition_description,
             rsp.revision_count               AS rsc_revision_count
        FROM the.range_special_condition rsp
       WHERE rsp.forest_file_id = :agreementId
       ORDER BY rsp.rtspc_skey
      """;

  private static final RowMapper<RangeUsageDto> USAGE_MAPPER = (rs, rowNum) -> new RangeUsageDto(
      rs.getString("forest_file_id"),
      rs.getObject("calendar_year", Integer.class),
      rs.getObject("authorized_use", Double.class),
      rs.getObject("non_use_agreement_no", Integer.class),
      rs.getObject("non_use_nonbillable", Double.class),
      rs.getObject("non_use_billable", Double.class),
      rs.getString("range_non_use_reason_code"),
      rs.getString("range_non_use_reason_desc"),
      rs.getObject("temp_increase", Double.class),
      rs.getString("range_increase_reason_code"),
      rs.getString("range_increase_reason_desc"),
      rs.getObject("total_annual_use", Double.class),
      rs.getObject("revision_count", Integer.class));

  /**
   * Range tenure detail — mirrors {@code FTA_100RANGE_TENURE.GET} plus the range
   * usage (FTA_613R) and land base (FTA_615) tabs.
   *
   * @param agreementId the range forest file id (path id)
   * @return the aggregated detail, or {@code null} when the file id is unknown
   */
  public RangeTenureDetailDto getDetail(String agreementId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("agreementId", agreementId);

    List<HeaderRow> headers = jdbc.query(HEADER_SQL, params, (rs, rowNum) -> new HeaderRow(
        rs.getString("forest_file_id"),
        rs.getString("file_type_code"),
        rs.getString("admin_org_unit_no"),
        rs.getString("forest_district_no"),
        rs.getString("district_admin_zone"),
        rs.getString("licensee"),
        rs.getString("sec_licensee_ind"),
        rs.getString("notes_label"),
        rs.getString("file_status_st"),
        rs.getObject("file_status_date", LocalDate.class),
        rs.getString("mgmt_unit_type"),
        rs.getString("mgmt_unit_id"),
        rs.getString("mgmt_unit_name"),
        rs.getString("file_name"),
        rs.getObject("issue_date", LocalDate.class),
        rs.getObject("expiry_date", LocalDate.class),
        rs.getObject("usage_expiry_date", LocalDate.class),
        rs.getObject("original_issue_date", LocalDate.class),
        rs.getObject("tenure_term_years", Integer.class),
        rs.getObject("tenure_term_months", Integer.class),
        rs.getObject("replacement_term_years", Integer.class),
        rs.getObject("replacement_count", Integer.class),
        rs.getString("range_ind")));

    if (headers.isEmpty()) {
      return null;
    }
    HeaderRow h = headers.get(0);

    // GET takes the years of the term's start and expiry; when either is
    // missing its year comparison is null and no row matches.
    List<RangeUsageDto> usage = List.of();
    if (h.issueDate() != null && h.usageExpiryDate() != null) {
      MapSqlParameterSource usageParams = new MapSqlParameterSource()
          .addValue("agreementId", agreementId)
          .addValue("startYear", h.issueDate().getYear())
          .addValue("expiryYear", h.usageExpiryDate().getYear());
      usage = jdbc.query(USAGE_SQL, usageParams, USAGE_MAPPER);
    }

    List<RangeUsageDto> history = jdbc.query(USAGE_HISTORY_SQL, params, USAGE_MAPPER);

    List<RangeLandBaseDto> landBase =
        jdbc.query(LAND_BASE_SQL, params, (rs, rowNum) -> new RangeLandBaseDto(
            rs.getObject("land_base_skey", Long.class),
            rs.getString("range_land_base_pid"),
            rs.getString("range_land_base_id"),
            rs.getString("description"),
            rs.getString("range_land_ownership_type_code"),
            rs.getString("range_land_purpose_code"),
            rs.getObject("fenced_pasture", Double.class),
            rs.getObject("unfenced_pasture", Double.class),
            rs.getObject("forage_production", Double.class),
            rs.getString("land_base_comment"),
            rs.getObject("lease_start_date", LocalDate.class),
            rs.getObject("lease_end_date", LocalDate.class),
            rs.getString("range_land_base_act_ind"),
            rs.getString("range_land_ownership_type_desc"),
            rs.getString("range_land_purpose_desc"),
            rs.getObject("revision_count", Integer.class)));

    List<RangeSpecialConditionDto> conditions =
        jdbc.query(SPECIAL_CONDITION_SQL, params, (rs, rowNum) -> new RangeSpecialConditionDto(
            rs.getObject("special_condition_skey", Long.class),
            rs.getString("range_special_condition_code"),
            rs.getString("condition_title"),
            rs.getString("condition_description"),
            rs.getObject("rsc_revision_count", Integer.class)));

    return new RangeTenureDetailDto(
        h.forestFileId(),
        h.fileTypeCode(),
        h.adminOrgUnitNo(),
        h.forestDistrictNo(),
        h.districtAdminZone(),
        h.licensee(),
        h.secLicenseeInd(),
        h.notesLabel(),
        h.fileStatusSt(),
        h.fileStatusDate(),
        h.mgmtUnitType(),
        h.mgmtUnitId(),
        h.mgmtUnitName(),
        h.fileName(),
        h.issueDate(),
        h.expiryDate(),
        h.originalIssueDate(),
        h.tenureTermYears(),
        h.replacementTermYears(),
        h.replacementCount(),
        replacementLabel(h.replacementTermYears(), h.expiryDate()),
        licenceReplaceableInd(h.fileTypeCode()),
        h.rangeInd(),
        termWarning(h.issueDate(), h.expiryDate(), h.tenureTermYears(), h.tenureTermMonths()),
        usage,
        history,
        landBase,
        conditions);
  }

  /**
   * GET's replaceable indicator: only E01 and H01 licences are replaced, and
   * only they show the Historical and Replacement sections.
   */
  static String licenceReplaceableInd(String fileTypeCode) {
    return "E01".equals(fileTypeCode) || "H01".equals(fileTypeCode) ? "Y" : "N";
  }

  /**
   * GET's replacement label: set when a replacement term is pending, naming
   * the January 1 after the initial expiry.
   */
  static String replacementLabel(Integer replacementTermYears, LocalDate initialExpiry) {
    if (replacementTermYears == null) {
      return null;
    }
    String year = initialExpiry == null ? "" : String.valueOf(initialExpiry.plusDays(1).getYear());
    return "This tenure will be replaced on Jan. 1, " + year + " for " + replacementTermYears
        + " years";
  }

  /**
   * GET's warning that the tenure term breaks the range rules (whole years,
   * following the calendar year).
   *
   * <p>Legacy tests the term's odd months and the effective date's January 1;
   * its expiry test reads a variable the procedure never sets, so it never
   * fires, and neither does it here.
   */
  static String termWarning(
      LocalDate issueDate, LocalDate expiryDate, Integer termYears, Integer termMonths) {
    int months = termMonths == null ? 0 : termMonths;
    boolean offCalendar =
        issueDate != null && (issueDate.getMonthValue() != 1 || issueDate.getDayOfMonth() != 1);
    if (months == 0 && !offCalendar) {
      return null;
    }
    return "Tenure term data retrieved does not meet range requirements. Tenures must be whole"
        + " years and follow the calendar year. Effective: " + (issueDate == null ? "" : issueDate)
        + "  Expiry: " + (expiryDate == null ? "" : expiryDate)
        + " Term: " + (termYears == null ? 0 : termYears) + " yr " + months + " mo";
  }

  /** Internal carrier for the single header row before aggregation. */
  private record HeaderRow(
      String forestFileId,
      String fileTypeCode,
      String adminOrgUnitNo,
      String forestDistrictNo,
      String districtAdminZone,
      String licensee,
      String secLicenseeInd,
      String notesLabel,
      String fileStatusSt,
      LocalDate fileStatusDate,
      String mgmtUnitType,
      String mgmtUnitId,
      String mgmtUnitName,
      String fileName,
      LocalDate issueDate,
      LocalDate expiryDate,
      LocalDate usageExpiryDate,
      LocalDate originalIssueDate,
      Integer tenureTermYears,
      Integer tenureTermMonths,
      Integer replacementTermYears,
      Integer replacementCount,
      String rangeInd) {}
}
