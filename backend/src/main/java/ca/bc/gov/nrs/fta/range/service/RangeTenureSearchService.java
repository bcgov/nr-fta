package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeTenureSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.sql.Types;
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

  // The client name is built from the joined V_CLIENT_PUBLIC row rather than
  // by calling SIL_GET_CLIENT_NAME per row; see ClientNameSql. Concatenated, not
  // String.formatted, because the SQL below contains LIKE '%' patterns.
  private static final String SELECT_COLUMNS =
      """
      SELECT ou.org_unit_code AS org_unit_code,
             fcl.client_number AS client_number,
             fcl.client_locn_code AS client_locn_code,
      """
          + "       SUBSTR(" + ClientNameSql.displayName("fc") + ", 1, 60) AS client_name,\n"
          + """
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
      """;

  private static final String FROM_WHERE =
      """
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
              AND :provisionCriteria = 'Y'
       WHERE (:regionNo IS NULL OR pfu.forest_region = :regionNo)
         AND (:districtNo IS NULL OR rt.admin_forest_district_no = :districtNo)
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
      """;

  // The legacy sort is org unit then file. A file can carry several client rows
  // and tenure terms, so the rest of the key breaks those ties — without it
  // OFFSET/FETCH could repeat or skip rows between pages. Rows still tied after
  // this show identical values, so their relative order cannot be seen.
  private static final String ORDER_BY =
      "\n ORDER BY ou.org_unit_code, pfu.forest_file_id, fcl.client_number,"
          + " fcl.client_locn_code, fcl.forest_file_client_type_code,"
          + " tt.legal_effective_dt, NVL(tt.current_expiry_dt, tt.initial_expiry_dt)";

  /**
   * Range tenure search — mirrors {@code FTA_001R_TENR_SRCH.mainline}
   * ({@code GET} action). Each filter is applied only when its bind value is
   * non-null.
   */
  public PagedResponse<RangeTenureSearchDto> search(
      String forestFileId,
      String fileTypeCode,
      String orgUnitNo,
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
      String totalAnnualUseTo,
      int page,
      int size) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("fileTypeCode", blankToNull(fileTypeCode))
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

    addOrgUnitFilter(params, orgUnitNo);

    // Legacy joins RANGE_PROVISION only when one of its criteria is entered.
    // Joined unconditionally, a tenure would repeat once per provision year.
    boolean provisionCriteria = java.util.stream.Stream.of(
            provisionYear, authorizedUseFrom, authorizedUseTo,
            temporaryIncreaseFrom, temporaryIncreaseTo,
            billableNonUseFrom, billableNonUseTo,
            nonBillableNonUseFrom, nonBillableNonUseTo,
            totalAnnualUseFrom, totalAnnualUseTo)
        .anyMatch(v -> blankToNull(v) != null);
    params.addValue("provisionCriteria", provisionCriteria ? "Y" : "N");

    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + FROM_WHERE, params, Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(params.getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<RangeTenureSearchDto> rows = jdbc.query(
        SELECT_COLUMNS + FROM_WHERE + ORDER_BY
            + "\n OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        (rs, rowNum) -> new RangeTenureSearchDto(
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

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /**
   * The admin org unit filter, following {@code BUILD_WHERE_CLAUSE}: the caller
   * passes an org unit number, and its level decides what it filters.
   * <ul>
   *   <li>a region matches files whose {@code pfu.forest_region} is that region;</li>
   *   <li>a district matches files whose {@code rt.admin_forest_district_no} is
   *       that district;</li>
   *   <li>anything else — another level, an unknown number — adds no filter at
   *       all, because the legacy level lookup returns null and neither branch
   *       applies.</li>
   * </ul>
   */
  private void addOrgUnitFilter(MapSqlParameterSource params, String orgUnitNo) {
    Long regionNo = null;
    Long districtNo = null;
    Long unitNo = parseLong(blankToNull(orgUnitNo));
    if (unitNo != null) {
      List<String> levels = jdbc.queryForList(
          "SELECT org_level_code FROM the.org_unit WHERE org_unit_no = :orgUnitNo",
          new MapSqlParameterSource("orgUnitNo", unitNo),
          String.class);
      String level = levels.isEmpty() ? null : levels.get(0);
      if ("R".equals(level)) {
        regionNo = unitNo;
      } else if ("D".equals(level)) {
        districtNo = unitNo;
      }
    }
    params.addValue("regionNo", regionNo, Types.NUMERIC);
    params.addValue("districtNo", districtNo, Types.NUMERIC);
  }

  private static Long parseLong(String s) {
    if (s == null) {
      return null;
    }
    try {
      return Long.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
