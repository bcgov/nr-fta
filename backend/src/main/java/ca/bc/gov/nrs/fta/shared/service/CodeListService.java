package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
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

  /** The uniform code-table query, for the tables that follow the standard shape. */
  private static String codeSql(String column, String table) {
    return """
        SELECT %s AS code,
               description AS description
          FROM %s
         WHERE SYSDATE BETWEEN effective_date AND expiry_date
         ORDER BY %s
        """.formatted(column, table, column);
  }

  private static final String ORG_UNITS_SQL =
      """
      SELECT TO_CHAR(org_unit_no)                   AS code,
             org_unit_code || ' - ' || org_unit_name AS description
        FROM the.org_unit
       WHERE SYSDATE BETWEEN effective_date AND expiry_date
       ORDER BY org_unit_code
      """;

  /** Administrative org units, keyed by the numeric id the search package expects. */
  public List<CodeOptionDto> orgUnits() {
    return jdbc.query(ORG_UNITS_SQL, MAPPER);
  }

  public List<CodeOptionDto> fileTypes() {
    return jdbc.query(codeSql("file_type_code", "the.file_type_code"), MAPPER);
  }

  /**
   * File statuses. {@code TENURE_FILE_STATUS_CODE}, not {@code FILE_STATUS_CODE}
   * — the latter is a different table with a different key, and the search
   * filters {@code pfu.file_status_st}, which is the tenure one.
   */
  public List<CodeOptionDto> fileStatuses() {
    return jdbc.query(
        codeSql("tenure_file_status_code", "the.tenure_file_status_code"), MAPPER);
  }

  public List<CodeOptionDto> fileClientTypes() {
    return jdbc.query(codeSql("file_client_type_code", "the.file_client_type_code"), MAPPER);
  }

  public List<CodeOptionDto> fileSources() {
    return jdbc.query(codeSql("file_source_code", "the.file_source_code"), MAPPER);
  }

  public List<CodeOptionDto> mapNotationTypes() {
    return jdbc.query(
        codeSql("map_notation_type_code", "the.map_notation_type_code"), MAPPER);
  }

  /** Range unit statuses, for the FTA006 range unit / pasture search. */
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
  public List<CodeOptionDto> landDistricts() {
    return jdbc.query(
        codeSql("primary_land_index_code", "the.primary_land_index_code"), MAPPER);
  }

  /** Primary IDs, for the FTA002 timber mark search. Reads the secondary index — see above. */
  public List<CodeOptionDto> primaryIds() {
    return jdbc.query(
        codeSql("secondary_land_index_code", "the.secondary_land_index_code"), MAPPER);
  }

  /** Salvage types, for the FTA002 and FTA005 searches. */
  public List<CodeOptionDto> salvageTypes() {
    return jdbc.query(codeSql("salvage_type_code", "the.salvage_type_code"), MAPPER);
  }

  /**
   * Harvest authority statuses — the FTA002 "Mark Status" list and the FTA005
   * "CP Status" list both resolve to {@code fta.lookup.harvestAuthStatusCode}.
   */
  public List<CodeOptionDto> harvestAuthStatuses() {
    return jdbc.query(
        codeSql("harvest_auth_status_code", "the.harvest_auth_status_code"), MAPPER);
  }

  /** Cut block statuses, for the FTA003 cut block search. */
  public List<CodeOptionDto> blockStatuses() {
    return jdbc.query(codeSql("block_status_code", "the.block_status_code"), MAPPER);
  }

  /**
   * Private mark statuses, for the FTA500 application/amendment list.
   *
   * <p>Not {@code MARK_STATUS_CODE} — that one belongs to
   * {@code TIMBER_MARK.mark_status_st}. This list backs
   * {@code PRIVATE_MARK_CERTIFICATE.private_mark_status_code} and the amendment
   * status beside it, whose values are HN, PA, PI, DV, HI and HX.
   */
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
  public List<CodeOptionDto> harvestAuthClientTypes() {
    return jdbc.query(
        codeSql("harvest_auth_client_type_code", "the.harvest_auth_client_type_code"), MAPPER);
  }

  /** Recreation file statuses, for the FTA007 search. */
  public List<CodeOptionDto> recreationFileStatuses() {
    return jdbc.query(
        codeSql("recreation_file_status_code", "the.recreation_file_status_code"), MAPPER);
  }

  /**
   * Recreation project types — the FTA007 "Project Type" list.
   *
   * <p>The only code list here that is filtered rather than taken whole:
   * {@code FTA_MAP_FEATURE_CODE} covers every map feature, and the legacy
   * lookup narrows it to the eight recreation ones. Ordered by description, as
   * legacy does, rather than by code.
   */
  public List<CodeOptionDto> recreationProjectTypes() {
    return jdbc.query(
        """
        SELECT fta_map_feature_code AS code,
               description AS description
          FROM the.fta_map_feature_code
         WHERE fta_map_feature_code IN ('RTR', 'RR', 'SIT', 'IF', 'IFT', 'TRB', 'TBL', 'RTE')
           AND SYSDATE BETWEEN effective_date AND expiry_date
         ORDER BY description
        """,
        MAPPER);
  }

  /** Recreation risk ratings, for the FTA007 search and FTA701 detail. */
  public List<CodeOptionDto> recreationRiskRatings() {
    return jdbc.query(
        codeSql("recreation_risk_rating_code", "the.recreation_risk_rating_code"), MAPPER);
  }

  /** Recreation controlled-access types. */
  public List<CodeOptionDto> recreationControlAccessTypes() {
    return jdbc.query(
        codeSql("recreation_control_access_code", "the.recreation_control_access_code"), MAPPER);
  }

  /** Recreation maintenance standards. */
  public List<CodeOptionDto> recreationMaintainStandards() {
    return jdbc.query(
        codeSql("recreation_maintain_std_code", "the.recreation_maintain_std_code"), MAPPER);
  }

  /** Recreation districts — distinct from the administrative org units. */
  public List<CodeOptionDto> recreationDistricts() {
    return jdbc.query(
        codeSql("recreation_district_code", "the.recreation_district_code"), MAPPER);
  }
}
