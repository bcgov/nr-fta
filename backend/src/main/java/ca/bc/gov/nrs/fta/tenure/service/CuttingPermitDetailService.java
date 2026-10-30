package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.security.HarvestAccess;
import ca.bc.gov.nrs.fta.security.RoleConstants;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitDetailDto;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitRules;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitConditionRow;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitLookups;
import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitPostponementRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Cutting permit detail business logic.
 *
 * <p>Ports the read side of the legacy Oracle package {@code THE.FTA_902_CP_DETAIL} (the FTA902
 * Cutting Permit / Timber Mark detail screen) to a native query against the shared {@code THE}
 * schema. The column selection mirrors the package's {@code GET} / {@code GET_TIMBER_MARK}
 * procedures, with outer joins where {@code GET_TIMBER_MARK} has a branch for a permit with no
 * timber mark.
 *
 * <p>A permit id is unique only within its tenure, so the lookup always names the tenure. An FSJ
 * permit is known by its harvesting authority id instead ({@code Fta_Get_Fsj_Ind}).
 *
 * <p>NOTE: the descriptive lookups (status/file-type descriptions, licensee via
 * {@code FOREST_FILE_CLIENT}/{@code CLIENT} and admin org via {@code ORG_UNIT}) are derived from
 * the file-header helpers the package calls ({@code Fta_Get_File_Header}) rather than reproduced
 * verbatim; they follow the same conventions as the tenure-search port.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the configured
 * {@code DataSource}; there is no local database, so it is exercised only in a deployed
 * environment.
 */
@Service
public class CuttingPermitDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public CuttingPermitDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT_SQL =
      """
      SELECT hva.hva_skey                      AS hva_skey,
             hva.forest_file_id                AS forest_file_id,
             CASE WHEN hva.harvest_type_code = 'F' AND hva.harvesting_authority_id IS NOT NULL
                  THEN hva.harvesting_authority_id
                  ELSE hva.cutting_permit_id END AS cutting_permit_id,
             CASE WHEN hva.harvest_type_code = 'F' AND hva.harvesting_authority_id IS NOT NULL
                  THEN 'Y' ELSE 'N' END        AS fsj_ind,
             xref.timber_mark                  AS timber_mark,
             pfu.file_type_code                AS file_type_code,
             ftc.description                   AS file_type_description,
             ou.org_unit_code                  AS admin_org_code,
             cli.client_name                   AS licensee,
             hva.harvest_auth_status_code      AS status_code,
             hsc.description                   AS status_desc,
             hva.status_date                   AS status_date,
             hva.issue_date                    AS issue_date,
             hva.expiry_date                   AS expiry_date,
             hva.extend_date                   AS extend_date,
             hva.harvest_auth_extend_reas_code AS extend_reason_code,
             hva.extend_count                  AS extend_count,
             FLOOR(hva.tenure_term / 12)       AS tenure_term_years,
             MOD(hva.tenure_term, 12)          AS tenure_term_months,
             hva.forest_district               AS forest_district,
             hva.quota_type_code               AS quota_type_code,
             hva.salvage_type_code             AS salvage_type_code,
             hva.deciduous_ind                 AS deciduous_ind,
             hva.catastrophic_ind              AS catastrophic_ind,
             hva.cruise_based_ind              AS cruise_based_ind,
             hva.crown_lands_region_code       AS crown_lands_region_code,
             haa.marking_method_code           AS marking_method_code,
             haa.marking_instrument_code       AS marking_instrument_code,
             hva.district_admn_zone            AS district_admn_zone,
             hva.harvest_area                  AS harvest_area,
             hva.location                      AS location,
             hva.mgmt_unit_id                  AS mgmt_unit_id,
             hva.mgmt_unit_type_code           AS mgmt_unit_type_code,
             hva.licence_to_cut_code           AS licence_to_cut_code,
             hva.is_cp_extensn_appl_fee_waived AS ext_app_fee_waived,
             hva.is_cp_extension_appl_fee_paid AS ext_app_fee_paid,
             hva.is_within_fibre_recovery_zone AS within_fibre_recovery_zone,
             hva.retirement_date               AS retirement_date,
             hva.revision_count                AS revision_count
        FROM the.harvesting_authority hva
        LEFT JOIN the.harvesting_hauling_xref xref
               ON xref.hva_skey = hva.hva_skey
              AND xref.primary_mark_ind = 'Y'
        LEFT JOIN the.hauling_authority haa
               ON haa.forest_file_id = hva.forest_file_id
              AND haa.timber_mark = xref.timber_mark
        JOIN the.prov_forest_use pfu
               ON pfu.forest_file_id = hva.forest_file_id
        LEFT JOIN the.org_unit ou
               ON ou.org_unit_no = pfu.forest_region
        LEFT JOIN the.file_type_code ftc
               ON ftc.file_type_code = pfu.file_type_code
        LEFT JOIN the.harvest_auth_status_code hsc
               ON hsc.harvest_auth_status_code = hva.harvest_auth_status_code
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = hva.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.forest_client cli
               ON cli.client_number = ffc.client_number
      """;

  private static final String BY_ID_SQL = SELECT_SQL
      + """
       WHERE hva.forest_file_id = :forestFileId
         AND (hva.cutting_permit_id = :cpId
              OR (hva.harvest_type_code = 'F' AND hva.harvesting_authority_id = :cpId))
       ORDER BY hva.hva_skey, cli.client_number
       FETCH FIRST 1 ROWS ONLY
      """;

  private static final String BY_SKEY_SQL = SELECT_SQL
      + """
       WHERE hva.hva_skey = :hvaSkey
       ORDER BY cli.client_number
       FETCH FIRST 1 ROWS ONLY
      """;

  /** {@code SET_RETIRED_IND}: an application in progress keeps the permit from retiring. */
  private static final String APPLICATION_IN_PROGRESS_SQL =
      """
      SELECT COUNT(*)
        FROM the.harvest_authority_amend hvaa
        JOIN the.harvest_authority_amend_geom hvag
          ON hvag.hva_skey = hvaa.hva_skey AND hvag.amendment_id = hvaa.amendment_id
        JOIN the.tenure_application_map_feature tamf ON tamf.map_feature_id = hvag.map_feature_id
        JOIN the.tenure_application ta ON ta.tenure_app_id = tamf.tenure_app_id
       WHERE hvaa.hva_skey = :hvaSkey
         AND ta.tenure_application_state_code IN ('INB', 'LOB')
      """;

  private static final String SPATIAL_EXISTS_SQL =
      "SELECT the.fta_spatial_exists(:forestFileId, :cpId) FROM dual";

  /** {@code GET_HARVESTING_POSTPONE_HIST}, with the id each row's Update and Delete need. */
  private static final String POSTPONEMENTS_SQL =
      """
      SELECT cpp.cp_postponement_id          AS id,
             cpp.cp_postponement_start_date  AS start_date,
             cpp.cp_postponement_expiry_date AS expiry_date,
             cpp.postponement_rescinded_ind  AS rescinded,
             cpp.previous_hva_expiry_date    AS previous_expiry_date,
             the.fta_902_cp_detail.get_harv_postpone_term_mo(
                 cpp.cp_postponement_start_date, cpp.cp_postponement_expiry_date) AS term_months,
             the.fta_902_cp_detail.get_harv_postpone_term_yrs(
                 cpp.cp_postponement_start_date, cpp.cp_postponement_expiry_date) AS term_years
        FROM the.cutting_permit_postponement cpp
       WHERE cpp.hva_skey = :hvaSkey
       ORDER BY cpp.cp_postponement_id DESC
      """;

  /** {@code FTA_902_CP_CONDITIONS.GET}, with the theme's code for the row's label. */
  private static final String CONDITIONS_SQL =
      """
      SELECT cond.iss_cond_theme_id          AS id,
             cond.permit_iss_cond_theme_code AS theme_code,
             code.description                AS theme_description,
             cond.other_description          AS description
        FROM the.permit_issuance_cond_theme cond
        JOIN the.permit_iss_cond_theme_code code
          ON code.permit_iss_cond_theme_code = cond.permit_iss_cond_theme_code
       WHERE cond.hva_skey = :hvaSkey
       ORDER BY cond.iss_cond_theme_id
      """;

  private static String codesSql(String table) {
    return "SELECT " + table + " AS code, " + table + " || ' - ' || description AS description"
        + " FROM the." + table
        + " WHERE SYSDATE BETWEEN effective_date AND expiry_date ORDER BY " + table;
  }

  private static final RowMapper<CodeOptionDto> CODE_MAPPER =
      (rs, rowNum) -> new CodeOptionDto(rs.getString("code"), rs.getString("description"));

  /**
   * Cutting permit detail — mirrors {@code FTA_902_CP_DETAIL.GET}.
   *
   * @param cpId         the cutting-permit id, or an FSJ permit's harvesting authority id
   * @param forestFileId the tenure it belongs to
   * @param caller       decides the rules served with it
   * @return the permit detail, or empty when no matching authority exists
   */
  public Optional<CuttingPermitDetailDto> findByCpId(
      String cpId, String forestFileId, Authentication caller) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("cpId", cpId)
        .addValue("forestFileId", forestFileId);
    return jdbc.query(BY_ID_SQL, params, this::row).stream().findFirst().map(p -> withRules(p, caller));
  }

  /** The permit a write addresses, by its key. */
  public Optional<CuttingPermitDetailDto> findByHvaSkey(long hvaSkey, Authentication caller) {
    return jdbc.query(BY_SKEY_SQL, Map.of("hvaSkey", hvaSkey), this::row).stream().findFirst()
        .map(p -> withRules(p, caller));
  }

  /** The permit's postponements, newest first. */
  public List<PermitPostponementRow> postponements(long hvaSkey) {
    return jdbc.query(POSTPONEMENTS_SQL, Map.of("hvaSkey", hvaSkey), (rs, n) ->
        new PermitPostponementRow(
            rs.getLong("id"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("expiry_date", LocalDate.class),
            rs.getString("rescinded"),
            rs.getObject("previous_expiry_date", LocalDate.class),
            getInteger(rs, "term_months"),
            getInteger(rs, "term_years")));
  }

  /** The permit's issuance conditions. */
  public List<PermitConditionRow> conditions(long hvaSkey) {
    return jdbc.query(CONDITIONS_SQL, Map.of("hvaSkey", hvaSkey), (rs, n) ->
        new PermitConditionRow(
            rs.getLong("id"),
            rs.getString("theme_code"),
            rs.getString("theme_description"),
            rs.getString("description")));
  }

  /** The edit dialog's code lists. */
  public PermitLookups lookups() {
    Map<String, ?> none = Map.of();
    return new PermitLookups(
        jdbc.query(codesSql("quota_type_code"), none, CODE_MAPPER),
        jdbc.query(codesSql("harvest_auth_extend_reas_code"), none, CODE_MAPPER),
        jdbc.query(codesSql("crown_lands_region_code"), none, CODE_MAPPER),
        jdbc.query(codesSql("permit_iss_cond_theme_code"), none, CODE_MAPPER));
  }

  /** The permit with the caller's rules. */
  private CuttingPermitDetailDto withRules(CuttingPermitDetailDto p, Authentication caller) {
    boolean editor = HarvestAccess.canEdit(caller);
    boolean inProgress = false;
    boolean spatial = false;
    boolean rescinded = false;
    // Only an editor's rules need these, so a reader's page does not depend on them.
    if (editor) {
      inProgress = positive(jdbc.queryForObject(
          APPLICATION_IN_PROGRESS_SQL, Map.of("hvaSkey", p.hvaSkey()), Long.class));
      spatial = "Y".equals(jdbc.queryForObject(
          SPATIAL_EXISTS_SQL,
          new MapSqlParameterSource()
              .addValue("forestFileId", p.forestFileId())
              .addValue("cpId", p.cuttingPermitId()),
          String.class));
      rescinded = postponements(p.hvaSkey()).stream().findFirst()
          .map(r -> "Y".equals(r.rescinded())).orElse(false);
    }
    boolean fsj = p.rules() != null && p.rules().canAssignMarks();
    CuttingPermitRules rules = CuttingPermitRules.of(new CuttingPermitRules.Facts(
        p.fileTypeCode(),
        p.statusCode(),
        fsj,
        p.issueDate(),
        p.retirementDate(),
        inProgress,
        spatial,
        rescinded,
        editor,
        HarvestAccess.isSenior(caller),
        isAdmin(caller)));
    return new CuttingPermitDetailDto(
        p.hvaSkey(), p.forestFileId(), p.cuttingPermitId(), p.timberMark(), p.fileTypeCode(),
        p.fileTypeDescription(), p.adminOrgCode(), p.licensee(), p.statusCode(), p.statusDesc(),
        p.statusDate(), p.issueDate(), p.expiryDate(), p.extendDate(), p.extendReasonCode(),
        p.extendCount(), p.tenureTermYears(), p.tenureTermMonths(), p.forestDistrict(),
        p.quotaTypeCode(), p.salvageTypeCode(), p.deciduousInd(), p.catastrophicInd(),
        p.cruiseBasedInd(), p.crownLandsRegionCode(), p.markingMethodCode(),
        p.markingInstrumentCode(), p.districtAdmnZone(), p.harvestArea(), p.location(),
        p.mgmtUnitId(), p.mgmtUnitTypeCode(), p.licenceToCutCode(), p.extAppFeeWaived(),
        p.extAppFeePaid(), p.withinFibreRecoveryZone(), p.retirementDate(), p.revisionCount(),
        rules);
  }

  /** FTA_ADMIN is the nearest thing here to legacy's Super User. */
  static boolean isAdmin(Authentication caller) {
    return caller != null && caller.getAuthorities().stream()
        .anyMatch(a -> RoleConstants.ADMIN_AUTHORITY.equals(a.getAuthority()));
  }

  /**
   * A row. {@code rules} carries only whether the permit is FSJ until {@link #withRules}
   * replaces it: every other rule needs the caller.
   */
  private CuttingPermitDetailDto row(ResultSet rs, int rowNum) throws SQLException {
    boolean fsj = "Y".equals(rs.getString("fsj_ind"));
    return new CuttingPermitDetailDto(
        rs.getLong("hva_skey"),
        rs.getString("forest_file_id"),
        rs.getString("cutting_permit_id"),
        rs.getString("timber_mark"),
        rs.getString("file_type_code"),
        rs.getString("file_type_description"),
        rs.getString("admin_org_code"),
        rs.getString("licensee"),
        rs.getString("status_code"),
        rs.getString("status_desc"),
        rs.getObject("status_date", LocalDate.class),
        rs.getObject("issue_date", LocalDate.class),
        rs.getObject("expiry_date", LocalDate.class),
        rs.getObject("extend_date", LocalDate.class),
        rs.getString("extend_reason_code"),
        getInteger(rs, "extend_count"),
        getInteger(rs, "tenure_term_years"),
        getInteger(rs, "tenure_term_months"),
        rs.getString("forest_district"),
        rs.getString("quota_type_code"),
        rs.getString("salvage_type_code"),
        rs.getString("deciduous_ind"),
        rs.getString("catastrophic_ind"),
        rs.getString("cruise_based_ind"),
        rs.getString("crown_lands_region_code"),
        rs.getString("marking_method_code"),
        rs.getString("marking_instrument_code"),
        rs.getString("district_admn_zone"),
        getDouble(rs, "harvest_area"),
        rs.getString("location"),
        rs.getString("mgmt_unit_id"),
        rs.getString("mgmt_unit_type_code"),
        rs.getString("licence_to_cut_code"),
        rs.getString("ext_app_fee_waived"),
        rs.getString("ext_app_fee_paid"),
        rs.getString("within_fibre_recovery_zone"),
        rs.getObject("retirement_date", LocalDate.class),
        rs.getLong("revision_count"),
        new CuttingPermitRules(false, false, false, false, false, false, false, false, false,
            false, false, false, fsj, false));
  }

  private static boolean positive(Long n) {
    return n != null && n > 0;
  }

  private static Integer getInteger(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static Double getDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }
}
