package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeLandBaseDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeSpecialConditionDto;
import ca.bc.gov.nrs.fta.range.dto.RangeTenureDetailDto.RangeUsageDto;
import java.util.List;
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
 *   <li>the header/tombstone mirrors {@code FTA_100RANGE_TENURE.GET} +
 *       {@code GET_RANGE_TENURE} (PROV_FOREST_USE / TENURE_TERM / RANGE_TENURE),
 *       with the main licensee joined the same way the common tenure search
 *       does (FOREST_FILE_CLIENT type 'A' → CLIENT);</li>
 *   <li>range usage mirrors {@code FTA_613R_RANGE_USAGE.GET_HISTORY}
 *       (RANGE_PROVISION);</li>
 *   <li>land base mirrors {@code FTA_615_RANGE_LAND_BASE.GET_LAND_BASE}
 *       (RANGE_LAND_BASE);</li>
 *   <li>special conditions mirror
 *       {@code FTA_100RANGE_TENURE.GET_SPECIAL_CONDITIONS}
 *       (RANGE_SPECIAL_CONDITION).</li>
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

  private static final String HEADER_SQL =
      """
      SELECT pfu.forest_file_id            AS forest_file_id,
             pfu.file_type_code            AS file_type_code,
             pfu.forest_region             AS admin_org_unit_no,
             rt.admin_forest_district_no   AS forest_district_no,
             rt.district_admn_zone         AS district_admin_zone,
             cli.client_name               AS licensee,
             pfu.file_status_st            AS file_status_st,
             pfu.file_status_date          AS file_status_date,
             pfu.mgmt_unit_type            AS mgmt_unit_type,
             pfu.mgmt_unit_id              AS mgmt_unit_id,
             rt.file_name                  AS file_name,
             tt.legal_effective_dt         AS issue_date,
             tt.initial_expiry_dt          AS expiry_date,
             rt.original_issuance_date     AS original_issue_date,
             FLOOR(NVL(tt.tenure_term, 0) / 12) AS tenure_term_years,
             rt.replacement_term           AS replacement_term_years,
             tt.tenure_extend_cnt          AS replacement_count
        FROM the.prov_forest_use pfu
        LEFT JOIN the.tenure_term tt      ON tt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.range_tenure rt     ON rt.forest_file_id = pfu.forest_file_id
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = pfu.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli          ON cli.client_number = ffc.client_number
       WHERE pfu.forest_file_id = :agreementId
      """;

  private static final String USAGE_SQL =
      """
      SELECT rp.forest_file_id             AS forest_file_id,
             rp.calendar_year              AS calendar_year,
             rp.authorized_use             AS authorized_use,
             rp.non_use_agreement_no       AS non_use_agreement_no,
             rp.non_use_nonbillable        AS non_use_nonbillable,
             rp.non_use_billable           AS non_use_billable,
             rp.range_non_use_reason_code  AS range_non_use_reason_code,
             rp.temp_increase              AS temp_increase,
             rp.range_increase_reason_code AS range_increase_reason_code,
             rp.total_annual_use           AS total_annual_use,
             rp.revision_count             AS revision_count
        FROM the.range_provision rp
       WHERE rp.forest_file_id = :agreementId
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
        rs.getString("file_status_st"),
        rs.getObject("file_status_date", java.time.LocalDate.class),
        rs.getString("mgmt_unit_type"),
        rs.getString("mgmt_unit_id"),
        rs.getString("file_name"),
        rs.getObject("issue_date", java.time.LocalDate.class),
        rs.getObject("expiry_date", java.time.LocalDate.class),
        rs.getObject("original_issue_date", java.time.LocalDate.class),
        rs.getObject("tenure_term_years", Integer.class),
        rs.getObject("replacement_term_years", Integer.class),
        rs.getObject("replacement_count", Integer.class)));

    if (headers.isEmpty()) {
      return null;
    }
    HeaderRow h = headers.get(0);

    List<RangeUsageDto> usage = jdbc.query(USAGE_SQL, params, (rs, rowNum) -> new RangeUsageDto(
        rs.getString("forest_file_id"),
        rs.getObject("calendar_year", Integer.class),
        rs.getObject("authorized_use", Double.class),
        rs.getObject("non_use_agreement_no", Integer.class),
        rs.getObject("non_use_nonbillable", Double.class),
        rs.getObject("non_use_billable", Double.class),
        rs.getString("range_non_use_reason_code"),
        rs.getObject("temp_increase", Double.class),
        rs.getString("range_increase_reason_code"),
        rs.getObject("total_annual_use", Double.class),
        rs.getObject("revision_count", Integer.class)));

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
            rs.getObject("lease_start_date", java.time.LocalDate.class),
            rs.getObject("lease_end_date", java.time.LocalDate.class),
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
        h.fileStatusSt(),
        h.fileStatusDate(),
        h.mgmtUnitType(),
        h.mgmtUnitId(),
        h.fileName(),
        h.issueDate(),
        h.expiryDate(),
        h.originalIssueDate(),
        h.tenureTermYears(),
        h.replacementTermYears(),
        h.replacementCount(),
        rangeInd(h.fileTypeCode()),
        usage,
        landBase,
        conditions);
  }

  /**
   * Derives the range indicator from the file type — mirrors
   * {@code Fta_Valid_Range_File_Type}: the range tenure file types are the E0x
   * (grazing) and H0x (hay cutting) families.
   */
  private static String rangeInd(String fileTypeCode) {
    if (fileTypeCode == null) {
      return "N";
    }
    return (fileTypeCode.startsWith("E") || fileTypeCode.startsWith("H")) ? "Y" : "N";
  }

  /** Internal carrier for the single header row before aggregation. */
  private record HeaderRow(
      String forestFileId,
      String fileTypeCode,
      String adminOrgUnitNo,
      String forestDistrictNo,
      String districtAdminZone,
      String licensee,
      String fileStatusSt,
      java.time.LocalDate fileStatusDate,
      String mgmtUnitType,
      String mgmtUnitId,
      String fileName,
      java.time.LocalDate issueDate,
      java.time.LocalDate expiryDate,
      java.time.LocalDate originalIssueDate,
      Integer tenureTermYears,
      Integer replacementTermYears,
      Integer replacementCount) {}
}
