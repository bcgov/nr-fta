package ca.bc.gov.nrs.fta.tenure.tab.details;

import ca.bc.gov.nrs.fta.configuration.CodeListCacheConfiguration;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The code lists behind FTA100's dropdowns ({@code FTA_CODE_LISTS} procedures named in legacy's
 * packagemap.xml) and the single-value PL/SQL functions its form validators call.
 *
 * <p>Each list comes in two forms: every code (to describe a stored value, expired or not) and
 * the current ones (what a dropdown offers; an expired code is legacy's
 * {@code CodeExpiryValidator} error). Both are cached with the app's other code lists.
 */
@Component
public class TenureDetailsLookups implements TenureDetailsValidator.Checks {

  /** One list: its table and code column. Names are the keys the frontend reads. */
  enum CodeTable {
    STATUSES("the.tenure_file_status_code", "tenure_file_status_code"),
    RECREATION_STATUSES("the.recreation_file_status_code", "recreation_file_status_code"),
    FILE_TYPES("the.file_type_code", "file_type_code"),
    EXTEND_REASONS("the.tenure_extend_reason_code", "tenure_extend_reason_code"),
    DEPOSIT_TYPES("the.deposit_type_code", "deposit_type_code"),
    OCC_LICENCE_TO_CUT("the.occupant_licence_to_cut_code", "occupant_licence_to_cut_code"),
    FOR_LICENCE_TO_CUT("the.forestry_licence_to_cut_code", "forestry_licence_to_cut_code"),
    SPECIAL_USE("the.special_use_code", "special_use_code"),
    QUOTA_TYPES("the.quota_type_code", "quota_type_code"),
    MARKING_METHODS("the.marking_method_code", "marking_method_code"),
    MARKING_INSTRUMENTS("the.marking_instrument_code", "marking_instrument_code"),
    LAND_REGIONS("the.crown_lands_region_code", "crown_lands_region_code"),
    PAYMENT_METHODS("the.payment_method_code", "payment_method_code"),
    FUP_TYPES("the.free_use_permit_type_code", "free_use_permit_type_code"),
    FUP_FN_USAGES("the.free_use_permit_fn_usage_code", "free_use_permit_fn_usage_code"),
    MAP_NOTATION_TYPES("the.map_notation_type_code", "map_notation_type_code"),
    FN_HELD_LEVELS("the.fn_held_percent_code", "fn_held_percent_code");

    final String table;
    final String column;

    CodeTable(String table, String column) {
      this.table = table;
      this.column = column;
    }

    /** camelCase key, e.g. EXTEND_REASONS → extendReasons. */
    String key() {
      StringBuilder sb = new StringBuilder();
      boolean upper = false;
      for (char c : name().toLowerCase().toCharArray()) {
        if (c == '_') {
          upper = true;
        } else {
          sb.append(upper ? Character.toUpperCase(c) : c);
          upper = false;
        }
      }
      return sb.toString();
    }
  }

  private static final RowMapper<CodeOptionDto> MAPPER =
      (rs, rowNum) -> new CodeOptionDto(rs.getString("code"), rs.getString("description"));

  private static final String ORG_UNITS_SQL =
      """
      SELECT TO_CHAR(org_unit_no) AS code,
             org_unit_code || ' - ' || org_unit_name AS description
        FROM the.org_unit
       WHERE (:all = 'Y' OR SYSDATE BETWEEN effective_date AND expiry_date)
         AND (:level IS NULL OR org_level_code = :level)
       ORDER BY org_unit_code
      """;

  /** GET_BCTS_ORG_UNIT_2_CODE: BCTS business areas (level T, three-letter codes). */
  private static final String BCTS_ORG_UNITS_SQL =
      """
      SELECT TO_CHAR(org_unit_no) AS code,
             org_unit_code || ' - ' || org_unit_name AS description
        FROM the.org_unit
       WHERE org_level_code = 'T'
         AND LENGTH(org_unit_code) = 3
         AND (:all = 'Y' OR expiry_date > SYSDATE)
       ORDER BY org_unit_code
      """;

  /** Headquarters: every FTA_ADMIN gets its rules, so the package is told the user sits there. */
  private static final String HQ_ORG_SQL =
      """
      SELECT TO_CHAR(org_unit_no) AS org_unit_no, org_unit_code
        FROM (SELECT org_unit_no, org_unit_code
                FROM the.org_unit
               WHERE org_level_code = 'H'
               ORDER BY CASE WHEN org_unit_code = 'HQ' THEN 0 ELSE 1 END,
                        CASE WHEN SYSDATE BETWEEN effective_date AND expiry_date THEN 0 ELSE 1 END,
                        org_unit_no)
       WHERE ROWNUM = 1
      """;

  /** FTA100_EDIT_STATUS_CHANGE for every target status at once. */
  private static final String OVERRIDE_STATUSES_SQL =
      """
      SELECT c.tenure_file_status_code AS code
        FROM the.tenure_file_status_code c
       WHERE the.fta100_edit_status_change(c.tenure_file_status_code, :old) = 'Y'
       ORDER BY c.tenure_file_status_code
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public TenureDetailsLookups(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static String codeSql(CodeTable t, boolean all) {
    return """
        SELECT %1$s AS code, %1$s || ' - ' || description AS description
          FROM %2$s
         %3$s
         ORDER BY %1$s
        """.formatted(t.column, t.table,
        all ? "" : "WHERE SYSDATE BETWEEN effective_date AND expiry_date");
  }

  /** The current codes of every list, for the edit form's dropdowns. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'tenureDetails.options'")
  public Map<String, List<CodeOptionDto>> options() {
    return lists(false);
  }

  /** Every code of every list, expired ones too, to describe stored values. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'tenureDetails.allCodes'")
  public Map<String, List<CodeOptionDto>> allCodes() {
    return lists(true);
  }

  private Map<String, List<CodeOptionDto>> lists(boolean all) {
    Map<String, List<CodeOptionDto>> out = new LinkedHashMap<>();
    for (CodeTable t : CodeTable.values()) {
      out.put(t.key(), jdbc.query(codeSql(t, all), MAPPER));
    }
    MapSqlParameterSource flag = new MapSqlParameterSource().addValue("all", all ? "Y" : "N");
    out.put("orgUnits", jdbc.query(ORG_UNITS_SQL,
        new MapSqlParameterSource(flag.getValues()).addValue("level", null, Types.VARCHAR), MAPPER));
    out.put("districts", jdbc.query(ORG_UNITS_SQL,
        new MapSqlParameterSource(flag.getValues()).addValue("level", "D", Types.VARCHAR), MAPPER));
    out.put("bctsOrgUnits", jdbc.query(BCTS_ORG_UNITS_SQL, flag, MAPPER));
    return out;
  }

  /** Headquarters' ORG_UNIT_NO and code, or nulls when the table has none. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'tenureDetails.headquarters'")
  public String[] headquarters() {
    List<String[]> rows = jdbc.query(HQ_ORG_SQL, (rs, n) ->
        new String[] {rs.getString("org_unit_no"), rs.getString("org_unit_code")});
    return rows.isEmpty() ? new String[] {null, null} : rows.get(0);
  }

  /** The target statuses for which a change from {@code oldStatus} needs an override reason. */
  public List<String> overrideStatuses(String oldStatus) {
    if (oldStatus == null || oldStatus.isBlank()) {
      return List.of();
    }
    return jdbc.queryForList(OVERRIDE_STATUSES_SQL,
        new MapSqlParameterSource("old", oldStatus), String.class);
  }

  private String fn(String sql, MapSqlParameterSource params) {
    List<String> rows = jdbc.queryForList(sql, params, String.class);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private boolean yes(String sql, MapSqlParameterSource params) {
    return "Y".equals(fn(sql, params));
  }

  /** FTA_VALID_LICENCE_TO_CUT_TYPE. */
  public boolean validLicenceToCutType(String fileType) {
    return yes("SELECT the.fta_valid_licence_to_cut_type(:t) FROM dual",
        new MapSqlParameterSource("t", fileType));
  }

  /** FTA_CALC_EXPIRY_DATE: Effective Date plus the term, less a day (yyyy-mm-dd), or null. */
  public String calcExpiryDate(String award, String years, String months) {
    String out = fn("SELECT the.fta_calc_expiry_date(:a, :y, :m) FROM dual",
        new MapSqlParameterSource().addValue("a", award, Types.VARCHAR)
            .addValue("y", years, Types.VARCHAR).addValue("m", months, Types.VARCHAR));
    return out == null || out.isBlank() ? null : out;
  }

  /** FTA_GET_ECAS_APPRAISAL_DATE for the file (no CP): the appraisal's effective date. */
  public String ecasAppraisalDate(String forestFileId) {
    String out = fn("SELECT the.fta_get_ecas_appraisal_date(:f, NULL) FROM dual",
        new MapSqlParameterSource("f", forestFileId));
    return out == null || out.isBlank() ? null : out;
  }

  @Override
  public boolean mgmtUnitExists(String type, String id) {
    return yes("SELECT the.fta_edit_mgmt_unit(:t, :i) FROM dual",
        new MapSqlParameterSource().addValue("t", type, Types.VARCHAR)
            .addValue("i", id, Types.VARCHAR));
  }

  @Override
  public boolean expiryMatchesTerm(String award, String years, String months, String expiry) {
    return yes("SELECT the.fta_edit_expiry_term(:a, :y, :m, :e) FROM dual",
        new MapSqlParameterSource().addValue("a", award, Types.VARCHAR)
            .addValue("y", years, Types.VARCHAR).addValue("m", months, Types.VARCHAR)
            .addValue("e", expiry, Types.VARCHAR));
  }

  @Override
  public boolean quotaTypeValid(String fileType, String quotaType) {
    return yes("SELECT the.fta_edit_quota_type(:t, :q) FROM dual",
        new MapSqlParameterSource("t", fileType).addValue("q", quotaType));
  }

  @Override
  public boolean timberMarkExists(String mark) {
    return yes("SELECT the.fta_edit_timber_mark(:m) FROM dual",
        new MapSqlParameterSource("m", mark.toUpperCase()));
  }

  @Override
  public boolean fileExists(String forestFileId) {
    return yes("SELECT the.fta_edit_file_id(:f) FROM dual",
        new MapSqlParameterSource("f", forestFileId));
  }

  @Override
  public boolean sameLicensee(String forestFileId, String pulpwoodFile) {
    return yes("SELECT the.fta_edit_pulpwood_file(:f, :p) FROM dual",
        new MapSqlParameterSource("f", forestFileId).addValue("p", pulpwoodFile));
  }

  @Override
  public boolean statusChangeNeedsOverride(String newStatus, String oldStatus) {
    return yes("SELECT the.fta100_edit_status_change(:n, :o) FROM dual",
        new MapSqlParameterSource("n", newStatus).addValue("o", oldStatus));
  }
}
