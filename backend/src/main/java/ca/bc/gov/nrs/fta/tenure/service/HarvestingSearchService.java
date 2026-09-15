package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA005 Harvesting Authority search.
 *
 * <p>Ports the standalone Oracle procedure {@code THE.FTA_005_HVA_SEARCH}. An
 * earlier version of this class was written against
 * {@code THE.FTA_HVA_SEARCH.PKS} and warned that its joins "may need
 * confirmation" — rightly so: that package declares only the REF CURSOR record
 * and contains no logic at all, so those joins were guesses and two of them
 * were wrong. This version follows the procedure line by line.
 *
 * <p>Three legacy behaviours are reproduced deliberately, because "fixing" them
 * would change which rows come back:
 *
 * <ul>
 *   <li>{@code hxref.primary_mark_ind = 'Y'} is an unguarded predicate on a
 *       table that is outer-joined. In Oracle that silently collapses the outer
 *       join to an inner join, so harvesting authorities with no
 *       {@code HARVESTING_HAULING_XREF} row never appear. Written as a plain
 *       {@code JOIN} here so the effect is explicit rather than accidental.
 *   <li>The same applies to the invoice-number predicate, which reaches
 *       {@code PIPELINE_SEGMENT} and {@code SEISMIC_LINE} — both outer-joined
 *       through {@code OIL_AND_GAS_AUTHORITY}.
 *   <li>{@code LIKE DECODE(p_search_only_og,'Y','A11','%')} excludes rows whose
 *       {@code file_type_code} is NULL even when the checkbox is off, because
 *       {@code NULL LIKE '%'} is not true.
 * </ul>
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}); there is no
 * local database, so it is exercised only in a deployed environment.
 */
@Service
public class HarvestingSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  public HarvestingSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * The client sub-select, verbatim from the procedure.
   *
   * <p>A UNION of the cutting permit's own clients and the file's licensee. The
   * first branch is gated on a client number or type actually being supplied;
   * the second only contributes when no client type was given. The legacy
   * comment explains the intent: return the harvesting authority client if one
   * exists, otherwise fall back to the file's {@code A} client.
   */
  private static final String CLIENT_SUBQUERY =
      """
      (SELECT *
         FROM (SELECT ha2.forest_file_id,
                      ha2.cutting_permit_id,
                      hac.client_number,
                      hac.harvest_auth_client_type_code AS client_type_code
                 FROM the.harvesting_authority ha2,
                      the.harvesting_authority_client hac
                WHERE ha2.hva_skey = hac.hva_skey
                  AND (ha2.forest_file_id LIKE :forestFileId || '%' OR :forestFileId IS NULL)
                  AND (hac.client_locn_code = :clientLocationCode
                       OR :clientLocationCode IS NULL)
                  AND hac.harvest_auth_client_type_code = NVL(:clientTypeCode, 'L')
                  AND (:clientNumber IS NOT NULL OR :clientTypeCode IS NOT NULL)
                UNION
               SELECT pfu2.forest_file_id,
                      NULL,
                      ffc2.client_number,
                      ffc2.forest_file_client_type_code AS client_type_code
                 FROM the.prov_forest_use pfu2,
                      the.forest_file_client ffc2
                WHERE pfu2.forest_file_id = ffc2.forest_file_id
                  AND (pfu2.forest_file_id LIKE :forestFileId || '%' OR :forestFileId IS NULL)
                  AND (ffc2.client_locn_code = :clientLocationCode
                       OR :clientLocationCode IS NULL)
                  AND ffc2.forest_file_client_type_code = 'A'
                  AND :clientTypeCode IS NULL)) hac_ffc_client
      """;

  /** The selected columns, matching the legacy cursor record's order. */
  private static final String SELECT_LIST =
      """
      SELECT hva.hva_skey AS hva_skey,
             org.org_unit_code AS org_unit_code,
             SUBSTR(the.sil_get_client_name(hac_ffc_client.client_number), 1, 55)
               || ' (' || the.sil_get_client_acronym(hac_ffc_client.client_number) || ')'
               AS client_name,
             hac_ffc_client.client_number AS client_number,
             pfu.file_type_code AS file_type_code,
             pfu.forest_file_id AS forest_file_id,
             hva.cutting_permit_id AS cutting_permit_id,
             hxref.timber_mark AS timber_mark,
             og.ogc_number AS ogc_number,
             og.nts_mapblock AS nts_mapblock,
             og.nts_mapunit AS nts_mapunit,
             og.nts_quarter AS nts_mapquarter,
             og.mapsheet_grid AS nts_mapsheet_grid,
             og.mapsheet_letter AS nts_mapsheet_letter,
             og.mapsheet_square AS nts_mapsheet_square,
             og.program_number AS program_number,
             og.geographic_identifier AS geographic_identifier
      """;

  /**
   * The {@code GROUP BY}, which must repeat the client-name expression rather
   * than its alias — Oracle does not accept a select alias here.
   */
  private static final String GROUP_BY =
      """
       GROUP BY hva.hva_skey,
                org.org_unit_code,
                SUBSTR(the.sil_get_client_name(hac_ffc_client.client_number), 1, 55)
                  || ' (' || the.sil_get_client_acronym(hac_ffc_client.client_number) || ')',
                hac_ffc_client.client_number,
                pfu.file_type_code,
                pfu.forest_file_id,
                hva.cutting_permit_id,
                hxref.timber_mark,
                og.ogc_number,
                og.nts_mapblock,
                og.nts_mapunit,
                og.nts_quarter,
                og.mapsheet_grid,
                og.mapsheet_letter,
                og.mapsheet_square,
                og.program_number,
                og.geographic_identifier
      """;

  private static final String FROM_AND_WHERE =
      """
        FROM the.prov_forest_use pfu,
             the.harvesting_authority hva,
             the.org_unit org,
             the.oil_and_gas_authority og,
             the.harvesting_hauling_xref hxref,
             the.pipeline_segment ps,
             the.seismic_line sl,
             the.v_client_public vcp,
      """
          // Concatenated rather than interpolated with String.formatted: this SQL
          // is full of LIKE patterns ending in '%', and formatted() reads every
          // '%' as a format specifier — "|| '%'" parses as the conversion "'"
          // and throws UnknownFormatConversionException from the static
          // initializer, so the class never loads.
          + CLIENT_SUBQUERY
          + """
       WHERE pfu.forest_file_id = hva.forest_file_id
         AND hva.forest_district = org.org_unit_no
         AND hva.hva_skey = og.hva_skey (+)
         AND hva.hva_skey = hxref.hva_skey (+)
         AND hva.forest_file_id = hac_ffc_client.forest_file_id
         AND (hva.cutting_permit_id = hac_ffc_client.cutting_permit_id
              OR hac_ffc_client.cutting_permit_id IS NULL)
         AND og.hva_skey = ps.hva_skey (+)
         AND og.hva_skey = sl.hva_skey (+)
         AND hac_ffc_client.client_number = vcp.client_number
         AND hxref.primary_mark_ind = 'Y'
         AND (pfu.file_type_code = :fileTypeCode OR :fileTypeCode IS NULL)
         AND (pfu.file_type_code LIKE DECODE(:searchOnlyOg, 'Y', 'A11', '%'))
         AND (hva.forest_district = :forestDistrict OR :forestDistrict IS NULL)
         AND (hva.mgmt_unit_type_code = :mgmtUnitType
              OR pfu.mgmt_unit_type = :mgmtUnitType
              OR :mgmtUnitType IS NULL)
         AND (hva.mgmt_unit_id = :mgmtUnitId
              OR pfu.mgmt_unit_id = :mgmtUnitId
              OR :mgmtUnitId IS NULL)
         AND (hva.forest_file_id LIKE :forestFileId || '%' OR :forestFileId IS NULL)
         AND (hva.cutting_permit_id = :cuttingPermitId OR :cuttingPermitId IS NULL)
         AND (hva.harvesting_authority_id = :hvaId OR :hvaId IS NULL)
         AND (hva.harvest_auth_status_code = :harvestAuthStatusCode
              OR :harvestAuthStatusCode IS NULL)
         AND (hva.issue_date >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD')
              OR :issueDateFrom IS NULL)
         AND (hva.issue_date <= TO_DATE(:issueDateTo, 'YYYY-MM-DD')
              OR :issueDateTo IS NULL)
         AND (hva.expiry_date >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD')
              OR :expiryDateFrom IS NULL)
         AND (hva.expiry_date <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD')
              OR :expiryDateTo IS NULL)
         AND (hva.salvage_type_code = :salvageTypeCode OR :salvageTypeCode IS NULL)
         AND (hva.district_admn_zone = :zone OR :zone IS NULL)
         AND (hva.licence_to_cut_code = :purposeCode OR :purposeCode IS NULL)
         AND (hac_ffc_client.client_number = :clientNumber OR :clientNumber IS NULL)
         AND (hxref.timber_mark LIKE :timberMark || '%' OR :timberMark IS NULL)
         AND (og.ogc_number = TO_NUMBER(:ogcNumber) OR :ogcNumber IS NULL)
         AND (og.geographic_identifier = :geographicIdentifier
              OR :geographicIdentifier IS NULL)
         AND (og.nts_mapblock = :ntsMapBlock OR :ntsMapBlock IS NULL)
         AND (og.nts_mapunit = :ntsMapUnit OR :ntsMapUnit IS NULL)
         AND (og.nts_quarter = :ntsQuarter OR :ntsQuarter IS NULL)
         AND (og.mapsheet_grid = :ntsMapsheetGrid OR :ntsMapsheetGrid IS NULL)
         AND (og.mapsheet_letter = :ntsMapsheetLetter OR :ntsMapsheetLetter IS NULL)
         AND (og.mapsheet_square = :ntsMapsheetSquare OR :ntsMapsheetSquare IS NULL)
         AND (og.orig_invoice_number = :invoiceNumber
              OR ps.invoice_number = :invoiceNumber
              OR sl.invoice_number = :invoiceNumber
              OR :invoiceNumber IS NULL)
         AND (vcp.client_name LIKE :clientName || '%' OR :clientName IS NULL)
      """;

  /**
   * The legacy sort. {@code '3'} is the {@code DECODE} default rather than an
   * explicit branch, so any unrecognised value also sorts by file type.
   *
   * <p>A second, stable key is appended. The legacy single-key sort is not
   * deterministic, which does not matter for a cursor read straight through but
   * does matter here, where {@code OFFSET}/{@code FETCH} can otherwise repeat or
   * skip rows between pages.
   */
  private static final String ORDER_BY =
      """
       ORDER BY DECODE(:sortBy, '1', org.org_unit_code,
                                '2', SUBSTR(the.sil_get_client_name(
                                       hac_ffc_client.client_number), 1, 55),
                                pfu.file_type_code),
                pfu.forest_file_id,
                hva.cutting_permit_id,
                hva.hva_skey
      """;

  /**
   * Validates the file id / cutting permit / HVA id combination.
   *
   * <p>The procedure calls {@code FTA_EDIT_CP_HVA} before opening its cursor
   * whenever a file id is supplied, and returns an error message instead of
   * results when the combination is invalid.
   *
   * @return the error message, or null when the combination is acceptable
   */
  public String validateFileKeys(String forestFileId, String cuttingPermitId, String hvaId) {
    if (blankToNull(forestFileId) == null) {
      return null;
    }
    return jdbc.queryForObject(
        "SELECT the.fta_edit_cp_hva(:forestFileId, :cuttingPermitId, :hvaId) FROM dual",
        new MapSqlParameterSource()
            .addValue("forestFileId", blankToNull(forestFileId))
            .addValue("cuttingPermitId", blankToNull(cuttingPermitId))
            .addValue("hvaId", blankToNull(hvaId)),
        String.class);
  }

  /**
   * Whether a timber mark exists and is well-formed.
   *
   * <p>Legacy's key search only short-circuits on a mark that validates against
   * the database ({@code HarvestingAuthoritySearchBOImpl.isValidTimberMark});
   * an invalid mark falls through to an ordinary search rather than erroring.
   */
  public boolean isTimberMarkValid(String timberMark) {
    if (blankToNull(timberMark) == null) {
      return false;
    }
    String result = jdbc.queryForObject(
        "SELECT the.fta_edit_timber_mark(:timberMark) FROM dual",
        new MapSqlParameterSource().addValue("timberMark", timberMark.trim()),
        String.class);
    return "Y".equals(result);
  }

  /** Runs the search, returning one page of results. */
  public PagedResponse<HarvestingSearchDto> search(
      HarvestingSearchCriteria criteria, int page, int size) {
    MapSqlParameterSource params = bind(criteria);

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM (" + SELECT_LIST + FROM_AND_WHERE + GROUP_BY + ")",
        params,
        Long.class);
    long totalElements = total == null ? 0L : total;

    if (totalElements == 0) {
      return PagedResponse.ofPage(List.of(), page, size, 0);
    }

    String sql = SELECT_LIST + FROM_AND_WHERE + GROUP_BY + ORDER_BY
        + " OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY";
    params.addValue("offset", (long) page * size).addValue("limit", size);

    List<HarvestingSearchDto> rows = jdbc.query(sql, params, (rs, rowNum) ->
        new HarvestingSearchDto(
            rs.getObject("hva_skey", Long.class),
            rs.getString("org_unit_code"),
            rs.getString("client_name"),
            rs.getString("client_number"),
            rs.getString("file_type_code"),
            rs.getString("forest_file_id"),
            rs.getString("cutting_permit_id"),
            rs.getString("timber_mark"),
            rs.getString("ogc_number"),
            rs.getString("nts_mapblock"),
            rs.getString("nts_mapunit"),
            rs.getString("nts_mapquarter"),
            rs.getString("nts_mapsheet_grid"),
            rs.getString("nts_mapsheet_letter"),
            rs.getString("nts_mapsheet_square"),
            rs.getString("program_number"),
            rs.getString("geographic_identifier")));

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  private static MapSqlParameterSource bind(HarvestingSearchCriteria c) {
    return new MapSqlParameterSource()
        .addValue("forestDistrict", blankToNull(c.forestDistrict()))
        .addValue("mgmtUnitType", blankToNull(c.mgmtUnitType()))
        .addValue("mgmtUnitId", blankToNull(c.mgmtUnitId()))
        .addValue("forestFileId", blankToNull(c.forestFileId()))
        .addValue("cuttingPermitId", blankToNull(c.cuttingPermitId()))
        .addValue("timberMark", blankToNull(c.timberMark()))
        .addValue("hvaId", blankToNull(c.hvaId()))
        .addValue("fileTypeCode", blankToNull(c.fileTypeCode()))
        .addValue("harvestAuthStatusCode", blankToNull(c.harvestAuthStatusCode()))
        .addValue("clientNumber", blankToNull(c.clientNumber()))
        .addValue("clientLocationCode", blankToNull(c.clientLocationCode()))
        .addValue("clientName", blankToNull(c.clientName()))
        .addValue("clientTypeCode", blankToNull(c.clientTypeCode()))
        .addValue("issueDateFrom", blankToNull(c.issueDateFrom()))
        .addValue("issueDateTo", blankToNull(c.issueDateTo()))
        .addValue("expiryDateFrom", blankToNull(c.expiryDateFrom()))
        .addValue("expiryDateTo", blankToNull(c.expiryDateTo()))
        .addValue("salvageTypeCode", blankToNull(c.salvageTypeCode()))
        .addValue("zone", blankToNull(c.zone()))
        .addValue("invoiceNumber", blankToNull(c.invoiceNumber()))
        .addValue("searchOnlyOg", blankToNull(c.searchOnlyOg()))
        .addValue("ogcNumber", blankToNull(c.ogcNumber()))
        .addValue("geographicIdentifier", blankToNull(c.geographicIdentifier()))
        .addValue("purposeCode", blankToNull(c.purposeCode()))
        .addValue("ntsQuarter", blankToNull(c.ntsQuarter()))
        .addValue("ntsMapUnit", blankToNull(c.ntsMapUnit()))
        .addValue("ntsMapBlock", blankToNull(c.ntsMapBlock()))
        .addValue("ntsMapsheetGrid", blankToNull(c.ntsMapsheetGrid()))
        .addValue("ntsMapsheetLetter", blankToNull(c.ntsMapsheetLetter()))
        .addValue("ntsMapsheetSquare", blankToNull(c.ntsMapsheetSquare()))
        .addValue("sortBy", c.sortBy() == null ? "1" : c.sortBy());
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
