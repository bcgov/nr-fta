package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptOrgUnitDto;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The code lists the APT screens share: APT2's lookup functions ported to SQL against the code
 * tables. The views those functions read ({@code APT_MU_TYPE_V}, {@code APT_FOA_TYPES_V} and so
 * on) are each a join of {@code CODE_SUBSET_TBL} and {@code CODE_LIST_TABLE}; the joins are
 * written out here.
 *
 * <p>Ordering is what the functions had: only the org units were ordered. The others come back
 * in the database's order, as they did in APT2.
 *
 * <p>Not cached: they are small, and the code tables behind them are maintained outside FTA.
 */
@Service
public class AptLookupService {

  /**
   * {@code APT_ORG_UNITS_GET}: current three-character region ({@code R__}) and/or district
   * ({@code D__}) codes, ordered by code. {@code :pattern2} repeats {@code :pattern1} when only
   * one level is asked for.
   */
  static final String ORG_UNITS_SQL = """
      SELECT ou.org_unit_code                         AS dsp_org_unit_code,
             ou.org_unit_name                         AS dsp_org_unit_name,
             ou.org_unit_code || ' - ' || ou.org_unit_name AS dsp_org_unit_display_name,
             ou.org_unit_no                           AS org_unit_no
        FROM the.org_unit ou
       WHERE (ou.org_unit_code LIKE :pattern1 OR ou.org_unit_code LIKE :pattern2)
         AND ou.effective_date <= SYSDATE
         AND ou.expiry_date >= SYSDATE
       ORDER BY 1
      """;

  /** {@code APT_MGMT_UNIT_TYPE_GET} over {@code APT_MU_TYPE_V}. */
  static final String MU_TYPES_SQL = """
      SELECT cs.code_argument,
             clt.expanded_result
        FROM the.code_subset_tbl cs
        JOIN the.code_list_table clt
          ON clt.code_argument = cs.code_argument
         AND clt.column_name = cs.column_name
       WHERE cs.code_subset_name = 'APT_MGMT_UNIT_ST'
      """;

  /** {@code APT_MGMT_UNIT_STATUS_GET} over {@code APT_MGMT_UNIT_STATUS_ST_V}. */
  static final String MU_STATUSES_SQL = """
      SELECT clt.code_argument,
             clt.expanded_result
        FROM the.code_list_table clt
        JOIN the.code_subset_tbl cs
          ON clt.column_name = cs.column_name
         AND clt.code_argument = cs.code_argument
       WHERE cs.code_subset_name = 'MGMT_UNIT_STS_ST'
      """;

  /**
   * {@code APT_FOA_TYPE_GET} over {@code APT_FOA_TYPES_V}: current FOA types an apportionment
   * category allows for the MU type; for {@code Z} also those allowed for {@code U}; for no type,
   * every current one.
   */
  static final String FOA_TYPES_SQL = """
      SELECT DISTINCT clt.code_argument,
                      clt.expanded_result
        FROM the.code_list_table clt
        JOIN the.code_subset_tbl cst
          ON clt.column_name = cst.column_name
         AND clt.code_argument = cst.code_argument
        JOIN the.apt_apportionment_categories acat
          ON acat.apt_foa_st = clt.code_argument
       WHERE cst.code_subset_name = 'APT_FOA_ST'
         AND (acat.apt_mgmt_unit_st = :muType
              OR (:muType = 'Z' AND acat.apt_mgmt_unit_st IN ('Z', 'U'))
              OR :muType IS NULL)
         AND SYSDATE BETWEEN clt.effective_date AND clt.expiry_date
      """;

  /** {@code APT_COMMITMENT_TYPE_GET}: the {@code COMMIT_TYPE_ST} subset, expired codes too. */
  static final String COMMITMENT_TYPES_SQL = """
      SELECT cs_comm_aac.code_argument     AS commit_type_st,
             clt_comm_aac.expanded_result  AS dsp_expanded_result
        FROM the.code_subset_tbl cs_comm_aac
        JOIN the.code_list_table clt_comm_aac
          ON clt_comm_aac.column_name = cs_comm_aac.column_name
         AND clt_comm_aac.code_argument = cs_comm_aac.code_argument
       WHERE cs_comm_aac.code_subset_name = 'COMMIT_TYPE_ST'
      """;

  /** {@code APT_PART_TYPE_GET} over {@code APT_PART_TYPES_V}. */
  static final String PART_TYPES_SQL = """
      SELECT clt.code_argument   AS part_type_cd,
             clt.expanded_result AS part_type_desc
        FROM the.code_list_table clt
        JOIN the.code_subset_tbl cst
          ON clt.column_name = cst.column_name
         AND clt.code_argument = cst.code_argument
       WHERE cst.code_subset_name = 'APT_PARTITION_ST'
      """;

  /**
   * {@code APT_PART_TYPE_GET_BY_MU}: the partition types any history row of the management unit
   * uses.
   */
  static final String PART_TYPES_BY_MU_SQL = """
      SELECT DISTINCT cltp.expanded_result AS part_type_desc,
                      cltp.code_argument   AS part_type_cd
        FROM the.code_list_table cltp,
             the.code_subset_tbl csp,
             the.apt_management_units mu,
             the.apt_mgmt_unit_history muh,
             the.apt_partitions part
       WHERE mu.apt_mgmt_unit_st = :muType
         AND mu.mgmt_unit_number = :muNumber
         AND muh.mu_id = mu.id
         AND part.muh_id = muh.id
         AND csp.code_subset_name = 'APT_PARTITION_ST'
         AND csp.code_argument = part.apt_partition_st
         AND cltp.column_name = csp.column_name
         AND cltp.code_argument = csp.code_argument
      """;

  private static final RowMapper<CodeOptionDto> CODE = (rs, i) ->
      new CodeOptionDto(rs.getString("CODE_ARGUMENT"), rs.getString("EXPANDED_RESULT"));

  private static final RowMapper<CodeOptionDto> PART_TYPE = (rs, i) ->
      new CodeOptionDto(rs.getString("PART_TYPE_CD"), rs.getString("PART_TYPE_DESC"));

  private final NamedParameterJdbcTemplate jdbc;

  public AptLookupService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Current regions and districts.
   *
   * @param level {@code R} for regions, {@code D} for districts, anything else for both
   */
  public List<AptOrgUnitDto> orgUnits(String level) {
    return jdbc.query(ORG_UNITS_SQL, orgUnitPatterns(level), (rs, i) -> {
      BigDecimal no = rs.getBigDecimal("ORG_UNIT_NO");
      return new AptOrgUnitDto(
          no == null ? null : no.intValue(),
          rs.getString("DSP_ORG_UNIT_CODE"),
          rs.getString("DSP_ORG_UNIT_NAME"),
          rs.getString("DSP_ORG_UNIT_DISPLAY_NAME"));
    });
  }

  /** The routine's level switch: exactly {@code R} or {@code D}, otherwise both. */
  static MapSqlParameterSource orgUnitPatterns(String level) {
    String first = "D".equals(level) ? "D__" : "R__";
    String second = "R".equals(level) ? "R__" : "D__";
    return new MapSqlParameterSource()
        .addValue("pattern1", first)
        .addValue("pattern2", second);
  }

  /** Management unit types: TSA, TFL and so on. */
  public List<CodeOptionDto> managementUnitTypes() {
    return jdbc.query(MU_TYPES_SQL, CODE);
  }

  /** Management unit statuses. */
  public List<CodeOptionDto> managementUnitStatuses() {
    return jdbc.query(MU_STATUSES_SQL, CODE);
  }

  /**
   * Forest Act (FOA) licence types valid for a management unit type.
   *
   * @param managementUnitType the MU type; null or blank lists every current FOA type
   */
  public List<CodeOptionDto> foaTypes(String managementUnitType) {
    return jdbc.query(FOA_TYPES_SQL,
        new MapSqlParameterSource().addValue("muType", blankToNull(managementUnitType),
            Types.VARCHAR),
        CODE);
  }

  /** Commitment types. */
  public List<CodeOptionDto> commitmentTypes() {
    return jdbc.query(COMMITMENT_TYPES_SQL, (rs, i) ->
        new CodeOptionDto(rs.getString("COMMIT_TYPE_ST"), rs.getString("DSP_EXPANDED_RESULT")));
  }

  /**
   * Partition types: all of them, or those a management unit's partitions use when both MU
   * arguments are given.
   */
  public List<CodeOptionDto> partitionTypes(String managementUnitType,
      String managementUnitNumber) {
    if (managementUnitType != null && !managementUnitType.isBlank()
        && managementUnitNumber != null && !managementUnitNumber.isBlank()) {
      return jdbc.query(PART_TYPES_BY_MU_SQL, new MapSqlParameterSource()
          .addValue("muType", managementUnitType)
          .addValue("muNumber", managementUnitNumber), PART_TYPE);
    }
    return jdbc.query(PART_TYPES_SQL, PART_TYPE);
  }

  /** What the routines received for a blank argument: null. */
  private static String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v;
  }
}
