package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Private Mark detail business logic.
 *
 * <p>Ports the legacy Oracle private-mark packages to native queries against
 * the shared {@code THE} schema, keyed by timber mark:
 *
 * <ul>
 *   <li>the mark tombstone/application record — {@code FTA_510_PRIVATE_MARK.GET}
 *       (PRIVATE_MARK_CERTIFICATE + PROV_FOREST_USE + HAULING_AUTHORITY + the
 *       main FOREST_FILE_CLIENT 'A' client);
 *   <li>the land index list — {@code FTA_511_MARK_LAND_INDEX.GET};
 *   <li>the associated-client list — {@code FTA_513_PM_CLIENT.GET};
 *   <li>the amendment history — {@code TMBR_MARK_AMEND}, as counted/read by
 *       {@code FTA_510_PRIVATE_MARK.GET}.
 * </ul>
 *
 * <p>The legacy code resolves client names/cities and land-index descriptions
 * via PL/SQL helper functions (Sil_Get_Client_Name, Spr_Get_Client_Locn_City,
 * SUBSTR of the code-table descriptions) and Oracle outer-join {@code (+)}
 * syntax. Those are rewritten here as ANSI joins to the underlying code/client
 * tables. The SQL runs against the BC Gov shared Oracle ({@code THE}); there is
 * no local database, so it is exercised only in a deployed environment.
 */
@Service
public class MarkDetailService {

  private final NamedParameterJdbcTemplate jdbc;

  public MarkDetailService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // NOTE: SQL below is derived from the FTA_510/511/513 package specs+bodies;
  // PL/SQL helper functions and (+) outer joins are rewritten as ANSI joins.
  private static final String MARK_SQL =
      """
      SELECT pmc.timber_mark                       AS timber_mark,
             pmc.certificate                       AS certificate,
             pfu.file_type_code                    AS file_type_code,
             pmc.private_mark_status_code          AS mark_status_code,
             pmc.private_mark_status_date          AS mark_status_date,
             pmc.private_mark_application_date     AS mark_application_date,
             pmc.private_mark_issue_date           AS mark_issue_date,
             pmc.private_mark_expiry_date          AS mark_expiry_date,
             pmc.private_mark_cancel_date          AS mark_cancel_date,
             pmc.private_mark_tenure_term          AS tenure_term,
             pmc.forest_district                   AS forest_district,
             ou.org_unit_code                      AS org_unit_code,
             ffc.client_number                     AS client_number,
             ffc.client_locn_code                  AS client_locn_code,
             cli.client_name                       AS client_name,
             NVL(haa.marking_method_code, 'S')     AS marking_method_code,
             NVL(haa.marking_instrument_code, 'H') AS marking_instrument_code,
             pmc.crown_granted_acq_desc            AS crown_granted_acq_desc,
             pmc.granted_acqrd_date                AS granted_acqrd_date,
             pmc.permit_block_locn                 AS permit_block_locn,
             pmc.permit_block_area                 AS permit_block_area,
             pmc.p_of_c_or_legal                   AS proof_of_crown_or_legal
        FROM the.private_mark_certificate pmc
        LEFT JOIN the.prov_forest_use pfu   ON pfu.forest_file_id = pmc.forest_file_id
        LEFT JOIN the.hauling_authority haa ON haa.timber_mark = pmc.timber_mark
        LEFT JOIN the.org_unit ou           ON ou.org_unit_no = pmc.forest_district
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = pmc.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        LEFT JOIN the.client cli            ON cli.client_number = ffc.client_number
       WHERE pmc.timber_mark = :markNumber
      """;

  private static final String LAND_INDEX_SQL =
      """
      SELECT mli.primary_land_index_code   AS primary_land_index_code,
             mli.secondary_land_index_code AS secondary_land_index_code,
             mli.primary_land_index_code || ' - ' || SUBSTR(li1.description, 1, 50)
               AS primary_land_index_code_desc,
             mli.secondary_land_index_code || ' - ' || SUBSTR(li2.description, 1, 50)
               AS secondary_land_index_code_desc,
             mli.mark_land_index_desc      AS mark_land_index_desc,
             mli.index_deactivate_date     AS index_deactivate_date,
             mli.mark_land_index_skey      AS mark_land_index_skey,
             mli.revision_count            AS revision_count
        FROM the.mark_land_index mli
        JOIN the.primary_land_index_code li1
             ON li1.primary_land_index_code = mli.primary_land_index_code
        LEFT JOIN the.secondary_land_index_code li2
             ON li2.secondary_land_index_code = mli.secondary_land_index_code
       WHERE mli.timber_mark = :markNumber
       ORDER BY mli.primary_land_index_code,
                mli.secondary_land_index_code,
                mli.mark_land_index_desc
      """;

  private static final String CLIENTS_SQL =
      """
      SELECT ffc.client_number                  AS client_number,
             ffc.client_locn_code               AS client_locn_code,
             cli.client_name                    AS client_name,
             loc.city_name                      AS client_city,
             ffc.forest_file_client_skey        AS for_client_link_skey,
             ffc.forest_file_client_type_code   AS file_client_type,
             fct.description                    AS file_client_type_desc,
             ffc.licensee_start_date            AS licensee_start_dt,
             ffc.licensee_end_date              AS licensee_end_date,
             ffc.revision_count                 AS revision_count
        FROM the.private_mark_certificate pmc
        JOIN the.forest_file_client ffc     ON ffc.forest_file_id = pmc.forest_file_id
        JOIN the.file_client_type_code fct
             ON fct.file_client_type_code = ffc.forest_file_client_type_code
        LEFT JOIN the.client cli            ON cli.client_number = ffc.client_number
        LEFT JOIN the.client_location loc
             ON loc.client_number = ffc.client_number
            AND loc.client_locn_code = ffc.client_locn_code
       WHERE pmc.timber_mark = :markNumber
       ORDER BY ffc.client_number, ffc.client_locn_code
      """;

  private static final String AMENDMENTS_SQL =
      """
      SELECT tma.amend_request_date AS amend_request_date,
             tma.prv_mrk_amd_sts_st AS prv_mrk_amd_sts_st,
             tma.revision_count     AS revision_count
        FROM the.tmbr_mark_amend tma
       WHERE tma.timber_mark = :markNumber
       ORDER BY tma.amend_request_date DESC
      """;

  /**
   * Loads a single private mark by timber-mark number, or empty when none
   * exists — mirrors the {@code GET}/{@code mainline} flow of the FTA_510/511/513
   * packages.
   *
   * @param markNumber the timber mark (path id)
   */
  public Optional<MarkDetailDto> findByMarkNumber(String markNumber) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("markNumber", markNumber);

    MarkDetailDto base;
    try {
      base = jdbc.queryForObject(MARK_SQL, params, (rs, rowNum) -> new MarkDetailDto(
          rs.getString("timber_mark"),
          rs.getString("certificate"),
          rs.getString("file_type_code"),
          rs.getString("mark_status_code"),
          rs.getObject("mark_status_date", java.time.LocalDate.class),
          rs.getObject("mark_application_date", java.time.LocalDate.class),
          rs.getObject("mark_issue_date", java.time.LocalDate.class),
          rs.getObject("mark_expiry_date", java.time.LocalDate.class),
          rs.getObject("mark_cancel_date", java.time.LocalDate.class),
          rs.getObject("tenure_term", Integer.class),
          rs.getString("forest_district"),
          rs.getString("org_unit_code"),
          rs.getString("client_number"),
          rs.getString("client_locn_code"),
          rs.getString("client_name"),
          rs.getString("marking_method_code"),
          rs.getString("marking_instrument_code"),
          rs.getString("crown_granted_acq_desc"),
          rs.getObject("granted_acqrd_date", java.time.LocalDate.class),
          rs.getString("permit_block_locn"),
          rs.getBigDecimal("permit_block_area"),
          rs.getString("proof_of_crown_or_legal"),
          List.of(),
          List.of(),
          List.of()));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }

    List<MarkDetailDto.LandIndex> landIndex =
        jdbc.query(LAND_INDEX_SQL, params, (rs, rowNum) -> new MarkDetailDto.LandIndex(
            rs.getString("primary_land_index_code"),
            rs.getString("secondary_land_index_code"),
            rs.getString("primary_land_index_code_desc"),
            rs.getString("secondary_land_index_code_desc"),
            rs.getString("mark_land_index_desc"),
            rs.getObject("index_deactivate_date", java.time.LocalDate.class),
            rs.getObject("mark_land_index_skey", Long.class),
            rs.getObject("revision_count", Integer.class)));

    List<MarkDetailDto.AssociatedClient> clients =
        jdbc.query(CLIENTS_SQL, params, (rs, rowNum) -> new MarkDetailDto.AssociatedClient(
            rs.getString("client_number"),
            rs.getString("client_locn_code"),
            rs.getString("client_name"),
            rs.getString("client_city"),
            rs.getObject("for_client_link_skey", Long.class),
            rs.getString("file_client_type"),
            rs.getString("file_client_type_desc"),
            rs.getObject("licensee_start_dt", java.time.LocalDate.class),
            rs.getObject("licensee_end_date", java.time.LocalDate.class),
            rs.getObject("revision_count", Integer.class)));

    List<MarkDetailDto.Amendment> amendments =
        jdbc.query(AMENDMENTS_SQL, params, (rs, rowNum) -> new MarkDetailDto.Amendment(
            rs.getObject("amend_request_date", java.time.LocalDate.class),
            rs.getString("prv_mrk_amd_sts_st"),
            rs.getObject("revision_count", Integer.class)));

    return Optional.of(new MarkDetailDto(
        base.timberMark(),
        base.certificate(),
        base.fileTypeCode(),
        base.markStatusCode(),
        base.markStatusDate(),
        base.markApplicationDate(),
        base.markIssueDate(),
        base.markExpiryDate(),
        base.markCancelDate(),
        base.tenureTerm(),
        base.forestDistrict(),
        base.orgUnitCode(),
        base.clientNumber(),
        base.clientLocnCode(),
        base.clientName(),
        base.markingMethodCode(),
        base.markingInstrumentCode(),
        base.crownGrantedAcqDesc(),
        base.grantedAcqrdDate(),
        base.permitBlockLocn(),
        base.permitBlockArea(),
        base.proofOfCrownOrLegal(),
        landIndex,
        clients,
        amendments));
  }
}
