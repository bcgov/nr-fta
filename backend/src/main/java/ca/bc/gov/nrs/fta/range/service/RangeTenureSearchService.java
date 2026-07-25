package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Range tenure search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_001R_TENR_SRCH} (Range
 * Tenure Search) to a native query against the shared {@code THE} schema. The
 * SELECT / FROM list mirrors the package body's {@code GET_LIST} procedure and
 * the WHERE clause mirrors {@code BUILD_WHERE_CLAUSE} — each filter is applied
 * only when its bind value is supplied (NVL-style), matching the legacy
 * behaviour. Column selection matches the package's {@code rec_tenure_results}
 * record.
 *
 * <p>NOTE: some legacy behaviour is simplified for the native port —
 * <ul>
 *   <li>the org-unit filter binds directly on {@code ou.org_unit_code} rather
 *       than resolving region-vs-district via {@code Sil_Get_Org_Level};</li>
 *   <li>{@code RANGE_PROVISION rp} is always LEFT-joined rather than joined only
 *       when a range-provision filter is supplied;</li>
 *   <li>the client-name filter matches on {@code fc.client_name} as a prefix.</li>
 * </ul>
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class RangeTenureSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public RangeTenureSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SEARCH_SQL =
      """
      SELECT ou.org_unit_code AS org_unit_code,
             fcl.client_number AS client_number,
             fcl.client_locn_code AS client_locn_code,
             SUBSTR(the.sil_get_client_name(fcl.client_number), 1, 60) AS client_name,
             pfu.forest_file_id AS forest_file_id,
             pfu.file_type_code AS file_type_code,
             fcl.forest_file_client_type_code
               || DECODE(fcl.forest_file_client_type_code, NULL, NULL, ' - ')
               || fclt.description AS file_client_type_desc,
             pfu.mgmt_unit_type AS mgmt_unit_type,
             pfu.mgmt_unit_id AS mgmt_unit_id,
             pfu.file_status_st AS file_status_code,
             SUBSTR(pfu.file_status_st || ' - ' || sts.description, 1, 30) AS file_status_desc,
             tt.legal_effective_dt AS issue_date,
             NVL(tt.current_expiry_dt, tt.initial_expiry_dt) AS expiry_date
        FROM the.prov_forest_use pfu
        LEFT OUTER JOIN the.tenure_file_status_code sts
               ON pfu.file_status_st = sts.tenure_file_status_code
        LEFT OUTER JOIN the.range_tenure rt
               ON pfu.forest_file_id = rt.forest_file_id
        LEFT OUTER JOIN the.org_unit ou
               ON rt.admin_forest_district_no = ou.org_unit_no
        LEFT OUTER JOIN the.tenure_term tt
               ON pfu.forest_file_id = tt.forest_file_id
        LEFT OUTER JOIN the.forest_file_client fcl
               ON fcl.forest_file_id = pfu.forest_file_id
        LEFT OUTER JOIN the.file_client_type_code fclt
               ON fclt.file_client_type_code = fcl.forest_file_client_type_code
        LEFT OUTER JOIN the.v_client_public fc
               ON fc.client_number = fcl.client_number
        LEFT OUTER JOIN the.range_provision rp
               ON rp.forest_file_id = pfu.forest_file_id
       WHERE (:orgUnitCode IS NULL OR ou.org_unit_code = :orgUnitCode)
         AND (:forestFileId IS NULL OR pfu.forest_file_id LIKE :forestFileId || '%')
         AND (:mgmtUnitType IS NULL OR pfu.mgmt_unit_type = :mgmtUnitType)
         AND (:mgmtUnitId IS NULL OR pfu.mgmt_unit_id = :mgmtUnitId)
         AND (:zone IS NULL OR pfu.district_admin_zone = :zone)
         AND ((:fileTypeCode IS NULL AND (pfu.file_type_code LIKE 'E%' OR pfu.file_type_code LIKE 'H%'))
              OR pfu.file_type_code = :fileTypeCode)
         AND (:fileStatus IS NULL OR pfu.file_status_st = :fileStatus)
         AND (:issueDateFrom IS NULL OR tt.legal_effective_dt >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD'))
         AND (:issueDateTo IS NULL OR tt.legal_effective_dt <= TO_DATE(:issueDateTo, 'YYYY-MM-DD'))
         AND (:expiryDateFrom IS NULL
              OR NVL(tt.current_expiry_dt, tt.initial_expiry_dt) >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD'))
         AND (:expiryDateTo IS NULL
              OR NVL(tt.current_expiry_dt, tt.initial_expiry_dt) <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD'))
         AND (:clientNumber IS NULL OR fcl.client_number = :clientNumber)
         AND (:clientLocnCode IS NULL OR fcl.client_locn_code = :clientLocnCode)
         AND ((:fileClientType IS NULL AND fcl.forest_file_client_type_code = 'A')
              OR fcl.forest_file_client_type_code = :fileClientType)
         AND (:clientName IS NULL OR UPPER(fc.client_name) LIKE UPPER(:clientName) || '%')
         AND (:provisionYear IS NULL OR rp.calendar_year = :provisionYear)
         AND (:authorizedUseFrom IS NULL OR rp.authorized_use >= :authorizedUseFrom)
         AND (:authorizedUseTo IS NULL OR rp.authorized_use <= :authorizedUseTo)
         AND (:temporaryIncreaseFrom IS NULL OR rp.temp_increase >= :temporaryIncreaseFrom)
         AND (:temporaryIncreaseTo IS NULL OR rp.temp_increase <= :temporaryIncreaseTo)
         AND (:billableNonUseFrom IS NULL OR rp.non_use_billable >= :billableNonUseFrom)
         AND (:billableNonUseTo IS NULL OR rp.non_use_billable <= :billableNonUseTo)
         AND (:nonBillableNonUseFrom IS NULL OR rp.non_use_nonbillable >= :nonBillableNonUseFrom)
         AND (:nonBillableNonUseTo IS NULL OR rp.non_use_nonbillable <= :nonBillableNonUseTo)
         AND (:totalAnnualUseFrom IS NULL OR rp.total_annual_use >= :totalAnnualUseFrom)
         AND (:totalAnnualUseTo IS NULL OR rp.total_annual_use <= :totalAnnualUseTo)
       ORDER BY ou.org_unit_code, pfu.forest_file_id
       FETCH FIRST 200 ROWS ONLY
      """;

  /**
   * Range tenure search — mirrors {@code FTA_001R_TENR_SRCH.mainline}
   * ({@code GET} action). Each filter is applied only when its bind value is
   * non-null.
   */
  public List<RangeTenureSearchDto> search(
      String forestFileId,
      String fileTypeCode,
      String orgUnitCode,
      String zone,
      String clientName,
      String clientNumber,
      String clientLocnCode,
      String fileClientType,
      String fileStatus,
      String mgmtUnitType,
      String mgmtUnitId,
      String issueDateFrom,
      String issueDateTo,
      String expiryDateFrom,
      String expiryDateTo,
      String provisionYear,
      String authorizedUseFrom,
      String authorizedUseTo,
      String temporaryIncreaseFrom,
      String temporaryIncreaseTo,
      String billableNonUseFrom,
      String billableNonUseTo,
      String nonBillableNonUseFrom,
      String nonBillableNonUseTo,
      String totalAnnualUseFrom,
      String totalAnnualUseTo) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("fileTypeCode", blankToNull(fileTypeCode))
        .addValue("orgUnitCode", blankToNull(orgUnitCode))
        .addValue("zone", blankToNull(zone))
        .addValue("clientName", blankToNull(clientName))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientLocnCode", blankToNull(clientLocnCode))
        .addValue("fileClientType", blankToNull(fileClientType))
        .addValue("fileStatus", blankToNull(fileStatus))
        .addValue("mgmtUnitType", blankToNull(mgmtUnitType))
        .addValue("mgmtUnitId", blankToNull(mgmtUnitId))
        .addValue("issueDateFrom", blankToNull(issueDateFrom))
        .addValue("issueDateTo", blankToNull(issueDateTo))
        .addValue("expiryDateFrom", blankToNull(expiryDateFrom))
        .addValue("expiryDateTo", blankToNull(expiryDateTo))
        .addValue("provisionYear", blankToNull(provisionYear))
        .addValue("authorizedUseFrom", blankToNull(authorizedUseFrom))
        .addValue("authorizedUseTo", blankToNull(authorizedUseTo))
        .addValue("temporaryIncreaseFrom", blankToNull(temporaryIncreaseFrom))
        .addValue("temporaryIncreaseTo", blankToNull(temporaryIncreaseTo))
        .addValue("billableNonUseFrom", blankToNull(billableNonUseFrom))
        .addValue("billableNonUseTo", blankToNull(billableNonUseTo))
        .addValue("nonBillableNonUseFrom", blankToNull(nonBillableNonUseFrom))
        .addValue("nonBillableNonUseTo", blankToNull(nonBillableNonUseTo))
        .addValue("totalAnnualUseFrom", blankToNull(totalAnnualUseFrom))
        .addValue("totalAnnualUseTo", blankToNull(totalAnnualUseTo));

    return jdbc.query(SEARCH_SQL, params, (rs, rowNum) -> new RangeTenureSearchDto(
        rs.getString("org_unit_code"),
        rs.getString("client_number"),
        rs.getString("client_locn_code"),
        rs.getString("client_name"),
        rs.getString("forest_file_id"),
        rs.getString("file_type_code"),
        rs.getString("file_client_type_desc"),
        rs.getString("mgmt_unit_type"),
        rs.getString("mgmt_unit_id"),
        rs.getString("file_status_code"),
        rs.getString("file_status_desc"),
        rs.getObject("issue_date", java.time.LocalDate.class),
        rs.getObject("expiry_date", java.time.LocalDate.class)));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
