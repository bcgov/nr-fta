package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.configuration.CodeListCacheConfiguration;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.dto.ManagementUnitDto;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The code lists behind the search-screen dropdowns.
 *
 * <p>Every {@code THE} code table is laid out the same way — a code column, a
 * {@code DESCRIPTION}, and an {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE} pair —
 * so the queries are generated from that one shape. Expired codes are filtered
 * out: they still exist on historical records but must not be offered as new
 * criteria.
 *
 * <p>Every label reads "{@code CODE - description}". The code is what appears on
 * the other screens, in correspondence and in the exported CSVs, so it belongs
 * in front of the description wherever a user picks from a list; the value
 * submitted back is still the bare code.
 *
 * <p>Every list is cached in memory and the cache is cleared on a schedule; see
 * {@link CodeListCacheConfiguration}. Search screens open with several of these at once, and
 * the tables behind them rarely change.
 *
 * <p>Org units are the exception. The legacy screen submits the numeric
 * {@code ORG_UNIT_NO} (the package's {@code p_admin_org_unit_no}, which it
 * feeds to {@code SIL_GET_ORG_LEVEL}) while displaying
 * "{@code code - name}", so the value and the label come from different
 * columns.
 */
@Service
public class CodeListService {

  private final NamedParameterJdbcTemplate jdbc;

  public CodeListService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final RowMapper<CodeOptionDto> MAPPER =
      (rs, rowNum) -> new CodeOptionDto(rs.getString("code"), rs.getString("description"));

  /**
   * The uniform code-table query, for the tables that follow the standard shape.
   *
   * <p>The label carries the code in front of the description — "A01 - Forest
   * Licence". The codes are what appear on other screens, in correspondence and
   * in the exported CSVs, so a user picking from a list needs to see them. The
   * value submitted is still the bare code.
   *
   * <p>Ordering is on the code, which is also the order the labels read in, so
   * the prefixes run A01, A02, B01 down the list. The two lists that do not use
   * this helper — org units and range zones — compose the same shape in their
   * own SQL.
   */
  private static String codeSql(String column, String table) {
    return """
        SELECT %s AS code,
               %s || ' - ' || description AS description
          FROM %s
         WHERE SYSDATE BETWEEN effective_date AND expiry_date
         ORDER BY %s
        """.formatted(column, column, table, column);
  }


  // Three-character codes only, as legacy's org unit lists filter them
  // (FTA_CODE_LISTS: LENGTH(ORG_UNIT_CODE) = 3).
  private static final String ORG_UNITS_SQL =
      """
      SELECT TO_CHAR(org_unit_no)                   AS code,
             org_unit_code || ' - ' || org_unit_name AS description
        FROM the.org_unit
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
         AND LENGTH(org_unit_code) = 3
       ORDER BY org_unit_code
      """;

  /**
   * Management units, ported from the legacy {@code SIL_004_MGMT_SRCH} picker
   * screen (SIL004): every unit with its type, spelled-out type and name.
   *
   * <p>Legacy offered this as a popup search reached from the tenure screens;
   * here it backs a single autocomplete, so the whole list is fetched once and
   * filtered in the browser rather than queried per keystroke.
   *
   * <p>Unlike legacy, expired units are left out — the same rule every other
   * list here follows. A unit that has expired can still sit on a historical
   * tenure, so if those need to be searchable this filter is what to relax.
   */
  private static final String MANAGEMENT_UNITS_SQL =
      """
      SELECT fmu.mgmt_unit_type_code                                  AS mgmt_unit_type_code,
             fmu.mgmt_unit_id                                         AS mgmt_unit_id,
             fmu.mgmt_unit_type_code || ' - ' || mutc.description     AS type_description,
             fmu.description                                         AS description
        FROM the.forest_mgmt_unit fmu
        JOIN the.mgmt_unit_type_code mutc
              ON mutc.mgmt_unit_type_code = fmu.mgmt_unit_type_code
       WHERE SYSDATE BETWEEN fmu.effective_date AND fmu.expiry_date
       ORDER BY fmu.mgmt_unit_type_code, fmu.mgmt_unit_id
      """;

  private static final RowMapper<ManagementUnitDto> MANAGEMENT_UNIT_MAPPER =
      (rs, rowNum) -> new ManagementUnitDto(
          rs.getString("mgmt_unit_type_code"),
          rs.getString("mgmt_unit_id"),
          rs.getString("type_description"),
          rs.getString("description"));

  /** Management units for the tenure search's autocomplete (legacy SIL004). */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<ManagementUnitDto> managementUnits() {
    return jdbc.query(MANAGEMENT_UNITS_SQL, MANAGEMENT_UNIT_MAPPER);
  }

  /** Administrative org units, keyed by the numeric id the search package expects. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> orgUnits() {
    return jdbc.query(ORG_UNITS_SQL, MAPPER);
  }

  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> fileTypes() {
    return jdbc.query(codeSql("file_type_code", "the.file_type_code"), MAPPER);
  }

  /**
   * File statuses. {@code TENURE_FILE_STATUS_CODE}, not {@code FILE_STATUS_CODE}
   * — the latter is a different table with a different key, and the search
   * filters {@code pfu.file_status_st}, which is the tenure one.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> fileStatuses() {
    return jdbc.query(
        codeSql("tenure_file_status_code", "the.tenure_file_status_code"), MAPPER);
  }

  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> fileClientTypes() {
    return jdbc.query(codeSql("file_client_type_code", "the.file_client_type_code"), MAPPER);
  }

  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> fileSources() {
    return jdbc.query(codeSql("file_source_code", "the.file_source_code"), MAPPER);
  }

  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> mapNotationTypes() {
    return jdbc.query(
        codeSql("map_notation_type_code", "the.map_notation_type_code"), MAPPER);
  }

  /** Range unit statuses, for the FTA006 range unit / pasture search. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> rangeUnitStatuses() {
    return jdbc.query(
        codeSql("range_unit_status_code", "the.range_unit_status_code"), MAPPER);
  }

  /**
   * Range zones, for the FTA001R range tenure search.
   *
   * <p>The one code list that takes a filter: zones belong to a district, and
   * the legacy screen repopulates this list whenever Admin Org Unit changes.
   * Passing no district returns every zone.
   *
   * <p>{@code RANGE_ZONE} carries no effective/expiry pair, so this cannot use
   * the uniform code-table query.
   */
  // Keyed by position (#p0), not by parameter name: names are only visible when the code is
  // compiled with -parameters, and without them every district would share one entry.
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "'rangeZones:' + (#p0 == null ? '' : #p0.trim())")
  public List<CodeOptionDto> rangeZones(String adminDistrictNo) {
    String sql =
        """
        SELECT rz.range_zone_code                                   AS code,
               rz.range_zone_code || ' - ' || rz.zone_description   AS description
          FROM the.range_zone rz
         WHERE (:adminDistrictNo IS NULL
                OR rz.admin_forest_district_no = TO_NUMBER(:adminDistrictNo))
         ORDER BY rz.range_zone_code
        """;
    return jdbc.query(
        sql,
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
            "adminDistrictNo",
            (adminDistrictNo == null || adminDistrictNo.isBlank()) ? null : adminDistrictNo.trim()),
        MAPPER);
  }

  /**
   * Land districts, for the FTA002 timber mark search.
   *
   * <p>Reads {@code PRIMARY_LAND_INDEX_CODE}. The legacy procedure names are
   * crossed over relative to the tables they read —
   * {@code PKG_SIL_CODE_LISTS.GET_LAND_DISTRICT_CODE} selects from
   * {@code PRIMARY_LAND_INDEX_CODE}, and {@code GET_PRIMARY_ID_CODE} selects
   * from {@code SECONDARY_LAND_INDEX_CODE}. Matching the names to the
   * similarly-named tables would wire both dropdowns to the wrong list.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> landDistricts() {
    return jdbc.query(
        codeSql("primary_land_index_code", "the.primary_land_index_code"), MAPPER);
  }

  /** Primary IDs, for the FTA002 timber mark search. Reads the secondary index — see above. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> primaryIds() {
    return jdbc.query(
        codeSql("secondary_land_index_code", "the.secondary_land_index_code"), MAPPER);
  }

  /** Salvage types, for the FTA002 and FTA005 searches. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> salvageTypes() {
    return jdbc.query(codeSql("salvage_type_code", "the.salvage_type_code"), MAPPER);
  }

  /**
   * Harvest authority statuses — the FTA002 "Mark Status" list and the FTA005
   * "CP Status" list both resolve to {@code fta.lookup.harvestAuthStatusCode}.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> harvestAuthStatuses() {
    return jdbc.query(
        codeSql("harvest_auth_status_code", "the.harvest_auth_status_code"), MAPPER);
  }

  /** Cut block statuses, for the FTA003 cut block search. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> blockStatuses() {
    return jdbc.query(codeSql("block_status_code", "the.block_status_code"), MAPPER);
  }

  /**
   * Districts — ORG_UNIT rows at the district level, for the FTA510 District
   * dropdown (legacy {@code sil.lookup.districtOrgUnitCode}). The code is the
   * ORG_UNIT_NO, as {@link #orgUnits()}.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> districts() {
    return jdbc.query(
        """
        SELECT TO_CHAR(org_unit_no)                   AS code,
               org_unit_code || ' - ' || org_unit_name AS description
          FROM the.org_unit
         WHERE org_level_code = 'D'
           AND SYSDATE BETWEEN effective_date AND expiry_date
         ORDER BY org_unit_code
        """,
        MAPPER);
  }

  /** Marking requirements (MARKING_METHOD_CODE), for the FTA510 edit form. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> markingMethods() {
    return jdbc.query(codeSql("marking_method_code", "the.marking_method_code"), MAPPER);
  }

  /** Marking instruments (MARKING_INSTRUMENT_CODE), for the FTA510 edit form. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> markingInstruments() {
    return jdbc.query(
        codeSql("marking_instrument_code", "the.marking_instrument_code"), MAPPER);
  }

  /** Cascade split codes, for the FTA510 edit form. */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> cascadeSplits() {
    return jdbc.query(codeSql("cascade_split_code", "the.cascade_split_code"), MAPPER);
  }

  /**
   * Private mark types — the FTA510 Mark Type dropdown (legacy
   * {@code fta.lookup.privateMarkTypeCode}). B15 and B16 are left out: legacy treats them
   * as view-only and refuses to assign a mark of either.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> privateMarkTypes() {
    return jdbc.query(
        """
        SELECT private_mark_type_code AS code,
               private_mark_type_code || ' - ' || description AS description
          FROM the.private_mark_type_code
         WHERE SYSDATE BETWEEN effective_date AND expiry_date
           AND private_mark_type_code NOT IN ('B15', 'B16')
         ORDER BY private_mark_type_code
        """,
        MAPPER);
  }

  /** Private mark amendment statuses (PRIVATE_MARK_AMEND_STATUS_CODE). */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> privateMarkAmendStatuses() {
    return jdbc.query(
        codeSql("private_mark_amend_status_code", "the.private_mark_amend_status_code"),
        MAPPER);
  }

  /**
   * Private mark statuses, for the FTA500 application/amendment list.
   *
   * <p>Not {@code MARK_STATUS_CODE} — that one belongs to
   * {@code TIMBER_MARK.mark_status_st}. This list backs
   * {@code PRIVATE_MARK_CERTIFICATE.private_mark_status_code} and the amendment
   * status beside it, whose values are HN, PA, PI, DV, HI and HX.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> privateMarkStatuses() {
    return jdbc.query(
        codeSql("private_mark_status_code", "the.private_mark_status_code"), MAPPER);
  }

  /**
   * Licence-to-cut codes — the FTA005 "Purpose" dropdown.
   *
   * <p>The control is drawn inside the screen's "Oil and Gas Criteria" box, but
   * it filters {@code HARVESTING_AUTHORITY.licence_to_cut_code}, an ordinary
   * harvesting authority column rather than an oil-and-gas one. Legacy resolves
   * the list through {@code fta.lookup.og.masterLicenceToCutCode}.
   *
   * <p>It is a filter only: {@code licence_to_cut_code} is not selected by the
   * search and never appears in the results grid.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> licenceToCutCodes() {
    return jdbc.query(codeSql("licence_to_cut_code", "the.licence_to_cut_code"), MAPPER);
  }

  /**
   * Harvest authority client types — the FTA005 "Client Type" dropdown.
   *
   * <p>Legacy defaults this to {@code L} (the licensee of the cutting permit)
   * inside its client sub-select. Leaving it unset changes the search shape
   * rather than merely widening it: with no client type the sub-select falls
   * back to the file's {@code A} client from {@code FOREST_FILE_CLIENT}.
   */
  @Cacheable(cacheNames = CodeListCacheConfiguration.CODE_LISTS, key = "#root.methodName")
  public List<CodeOptionDto> harvestAuthClientTypes() {
    return jdbc.query(
        codeSql("harvest_auth_client_type_code", "the.harvest_auth_client_type_code"), MAPPER);
  }


}
