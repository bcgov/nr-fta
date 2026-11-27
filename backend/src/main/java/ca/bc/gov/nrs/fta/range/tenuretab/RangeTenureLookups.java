package ca.bc.gov.nrs.fta.range.tenuretab;

import ca.bc.gov.nrs.fta.configuration.CodeListCacheConfiguration;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureTabDto.ConditionCode;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FTA100R's code lists — FTA_CODE_LISTS' GET_RANGE_TENURE_STATUS_CODE,
 * GET_RANGE_FILE_TYPE_CODE and GET_RANGE_SPEC_CONDITION_CODE, which legacy read with
 * {@code p_valid_only = 'Y'} for the dropdowns — plus every code, to describe a stored value, and
 * FTA010's range file types. Cached with the app's other code lists.
 */
@Component
public class RangeTenureLookups {

  private static final RowMapper<CodeOptionDto> MAPPER =
      (rs, n) -> new CodeOptionDto(rs.getString("code"), rs.getString("description"));

  private static final String CURRENT = " WHERE SYSDATE BETWEEN effective_date AND expiry_date";

  /** The range file types FTA010 handed to FTA100R's Add New (Fta010AddNewTenureAction). */
  static final List<String> CREATE_TYPES = List.of("E01", "E02", "H01", "H02");

  private final NamedParameterJdbcTemplate jdbc;

  public RangeTenureLookups(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private List<CodeOptionDto> codes(String table, String column, boolean currentOnly) {
    return jdbc.query("SELECT " + column + " AS code, " + column
        + " || ' - ' || description AS description FROM the." + table
        + (currentOnly ? CURRENT : "") + " ORDER BY " + column, MAPPER);
  }

  /** Current range tenure statuses ("A - Active"). */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.statuses'")
  public List<CodeOptionDto> statuses() {
    return codes("range_tenure_status_code", "range_tenure_status_code", true);
  }

  /** Every range tenure status, expired included. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.allStatuses'")
  public List<CodeOptionDto> allStatuses() {
    return codes("range_tenure_status_code", "range_tenure_status_code", false);
  }

  /** Current range file types. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.fileTypes'")
  public List<CodeOptionDto> fileTypes() {
    return codes("range_file_type_code", "range_file_type_code", true);
  }

  /** Every range file type, expired included. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.allFileTypes'")
  public List<CodeOptionDto> allFileTypes() {
    return codes("range_file_type_code", "range_file_type_code", false);
  }

  /**
   * Current special condition codes: the code's title (DESCRIPTION) and text (NOTE), which
   * legacy's form split back out of GET_RANGE_SPEC_CONDITION_CODE's "description_note".
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.conditionCodes'")
  public List<ConditionCode> conditionCodes() {
    return jdbc.query("""
        SELECT range_special_condition_code AS code, description, note
          FROM the.range_special_condition_code
         WHERE SYSDATE BETWEEN effective_date AND expiry_date
         ORDER BY range_special_condition_code
        """, (rs, n) -> new ConditionCode(rs.getString("code"), rs.getString("description"),
        rs.getString("note")));
  }

  /** FTA010's current add-new file types that are range types FTA100R creates. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS,
      key = "'rangeTenureTab.createTypes'")
  public List<CodeOptionDto> createTypes() {
    return jdbc.query("""
        SELECT a.file_type_addnew_code AS code,
               a.file_type_addnew_code || ' - ' || a.description AS description
          FROM the.file_type_addnew_code a
         WHERE SYSDATE BETWEEN a.effective_date AND a.expiry_date
           AND a.file_type_addnew_code IN (:types)
         ORDER BY a.file_type_addnew_code
        """, new MapSqlParameterSource("types", CREATE_TYPES), MAPPER);
  }

  /** A special condition on the file, as GET read it for Update. */
  public record StoredCondition(String code, String title, Integer revisionCount) {}

  /** The file's special condition with this key, or null. */
  public StoredCondition condition(String forestFileId, long skey) {
    List<StoredCondition> rows = jdbc.query("""
        SELECT range_special_condition_code AS code, condition_title AS title, revision_count
          FROM the.range_special_condition
         WHERE forest_file_id = :file
           AND rtspc_skey = :skey
        """, new MapSqlParameterSource().addValue("file", forestFileId).addValue("skey", skey),
        (rs, n) -> new StoredCondition(rs.getString("code"), rs.getString("title"),
            rs.getObject("revision_count", Integer.class)));
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** A district's ORG_UNIT_CODE from its ORG_UNIT_NO, or null. */
  public String orgUnitCode(String orgUnitNo) {
    if (orgUnitNo == null || !orgUnitNo.trim().matches("\\d{1,10}")) {
      return null;
    }
    List<String> rows = jdbc.queryForList(
        "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :no",
        new MapSqlParameterSource("no", Long.parseLong(orgUnitNo.trim())), String.class);
    return rows.isEmpty() ? null : rows.get(0);
  }
}
