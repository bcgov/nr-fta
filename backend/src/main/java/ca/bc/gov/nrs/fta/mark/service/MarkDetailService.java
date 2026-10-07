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
 * the shared {@code THE} schema. Legacy keys every one of them on the header's
 * timber mark <em>or</em> certificate: an application that has not been issued
 * yet has a certificate but no timber mark, and the FTA500 list links to it by
 * that certificate.
 *
 * <ul>
 *   <li>the mark tombstone/application record — {@code FTA_510_PRIVATE_MARK.GET}
 *       (PRIVATE_MARK_CERTIFICATE + PROV_FOREST_USE + HAULING_AUTHORITY + the
 *       main FOREST_FILE_CLIENT 'A' client);
 *   <li>the land index list — {@code FTA_511_MARK_LAND_INDEX.GET};
 *   <li>the associated-client list — {@code FTA_513_PM_CLIENT.GET};
 *   <li>the amendment history — {@code TMBR_MARK_AMEND}, as counted/read by
 *       {@code FTA_510_PRIVATE_MARK.GET};
 *   <li>the notes — {@code FTA_970_FOREST_NOTE.GET}, the Notes tab of the
 *       legacy private-mark tab set, keyed by the mark's forest file.
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
             pmc.forest_file_id                    AS forest_file_id,
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
             COALESCE(pmcl.client_number, ffc.client_number)       AS client_number,
             COALESCE(pmcl.client_locn_code, ffc.client_locn_code) AS client_locn_code,
             cli.client_name                       AS client_name,
             NVL(haa.marking_method_code, 'S')     AS marking_method_code,
             NVL(haa.marking_instrument_code, 'H') AS marking_instrument_code,
             pmc.crown_granted_acq_desc            AS crown_granted_acq_desc,
             pmc.granted_acqrd_date                AS granted_acqrd_date,
             pmc.permit_block_locn                 AS permit_block_locn,
             pmc.permit_block_area                 AS permit_block_area,
             pmc.p_of_c_or_legal                   AS proof_of_crown_or_legal,
             -- "<code> - <description>" for display; NULL when the code does not
             -- resolve, so the caller falls back to the bare code.
             CASE WHEN pms.description IS NOT NULL
                  THEN pmc.private_mark_status_code || ' - ' || pms.description END
                                                   AS mark_status_desc,
             CASE WHEN ftc.description IS NOT NULL
                  THEN pfu.file_type_code || ' - ' || ftc.description END AS file_type_desc,
             CASE WHEN ou.org_unit_name IS NOT NULL
                  THEN ou.org_unit_code || ' - ' || ou.org_unit_name END  AS district_desc,
             CASE WHEN rou.org_unit_name IS NOT NULL
                  THEN rou.org_unit_code || ' - ' || rou.org_unit_name END AS region_desc,
             CASE WHEN mmc.description IS NOT NULL
                  THEN mmc.marking_method_code || ' - ' || mmc.description END
                                                   AS marking_method_desc,
             CASE WHEN mic.description IS NOT NULL
                  THEN mic.marking_instrument_code || ' - ' || mic.description END
                                                   AS marking_instrument_desc,
             pmc.bcaa_folio_number                 AS bcaa_folio_number,
             pmc.mgmt_unit_type_code               AS mgmt_unit_type_code,
             pmc.mgmt_unit_id                      AS mgmt_unit_id,
             fmu.description                       AS mgmt_unit_desc,
             pmc.cascade_split_code                AS cascade_split_code,
             CASE WHEN csc.description IS NOT NULL
                  THEN csc.cascade_split_code || ' - ' || csc.description END
                                                   AS cascade_split_desc,
             SUBSTR(pmc.map_reference_id, 1, 2)    AS map_reference_reg,
             SUBSTR(pmc.map_reference_id, 3, 3)    AS map_reference_comp,
             pmc.private_mark_extend_date          AS mark_extend_date,
             pmc.private_mark_extend_count         AS mark_extend_count,
             pmc.private_mark_amend_date           AS mark_amend_date,
             amd.status                            AS outstanding_amend_status,
             amd.revision_count                    AS amend_revision_count,
             pmc.revision_count                    AS revision_count,
             -- TMBR_MARK_AMEND's parent row (TMA_TM_FK): amendments need it.
             CASE WHEN EXISTS (SELECT 1 FROM the.timber_mark tm
                                WHERE tm.timber_mark = pmc.timber_mark)
                  THEN 'Y' ELSE 'N' END            AS timber_mark_record,
             CASE WHEN pmas.description IS NOT NULL
                  THEN amd.status || ' - ' || pmas.description END
                                                   AS outstanding_amend_status_desc
        FROM the.private_mark_certificate pmc
        LEFT JOIN the.prov_forest_use pfu   ON pfu.forest_file_id = pmc.forest_file_id
        LEFT JOIN the.file_type_code ftc    ON ftc.file_type_code = pfu.file_type_code
        -- Not the code-list endpoint: that one drops expired codes, and an old
        -- mark can still carry one (e.g. DD).
        LEFT JOIN the.private_mark_status_code pms
               ON pms.private_mark_status_code = pmc.private_mark_status_code
        LEFT JOIN the.hauling_authority haa ON haa.timber_mark = pmc.timber_mark
        LEFT JOIN the.marking_method_code mmc
               ON mmc.marking_method_code = NVL(haa.marking_method_code, 'S')
        LEFT JOIN the.marking_instrument_code mic
               ON mic.marking_instrument_code = NVL(haa.marking_instrument_code, 'H')
        LEFT JOIN the.org_unit ou           ON ou.org_unit_no = pmc.forest_district
        LEFT JOIN the.org_unit rou          ON rou.org_unit_no = ou.rollup_region_no
        -- FTA_GET_MGMT_UNIT_DESC: the id may be blank, hence the NVLs.
        LEFT JOIN the.forest_mgmt_unit fmu
               ON fmu.mgmt_unit_type_code = pmc.mgmt_unit_type_code
              AND NVL(fmu.mgmt_unit_id, ' ') = NVL(pmc.mgmt_unit_id, ' ')
        LEFT JOIN the.cascade_split_code csc
               ON csc.cascade_split_code = pmc.cascade_split_code
        -- The outstanding amendment, as FTA_510.GET's curAmend: one in PI or HN.
        LEFT JOIN (SELECT a.timber_mark,
                          MAX(a.prv_mrk_amd_sts_st) AS status,
                          MAX(a.revision_count)     AS revision_count
                     FROM the.tmbr_mark_amend a
                    WHERE a.prv_mrk_amd_sts_st IN ('PI', 'HN')
                    GROUP BY a.timber_mark) amd
               ON amd.timber_mark = pmc.timber_mark
        LEFT JOIN the.private_mark_amend_status_code pmas
               ON pmas.private_mark_amend_status_code = amd.status
        LEFT JOIN the.forest_file_client ffc
               ON ffc.forest_file_id = pmc.forest_file_id
              AND ffc.forest_file_client_type_code = 'A'
        -- An unissued application has no forest file yet; its holder sits on
        -- PRIVATE_MARK_CLIENT, keyed by certificate (as the FTA500 list reads it).
        LEFT JOIN the.private_mark_client pmcl
               ON pmcl.certificate = pmc.certificate
              AND pmcl.private_mark_client_type_code = 'A'
        LEFT JOIN the.forest_client cli
               ON cli.client_number = COALESCE(pmcl.client_number, ffc.client_number)
       WHERE (:byCertificate = 'N' AND pmc.timber_mark = :markNumber)
          OR (:byCertificate = 'Y' AND pmc.certificate = :markNumber)
       -- The outer joins can fan out (more than one 'A' client or hauling
       -- authority row); the header is one record, so take the first.
       FETCH FIRST 1 ROW ONLY
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
       WHERE (mli.timber_mark = :timberMark AND :timberMark IS NOT NULL)
          OR (mli.certificate = :certificate AND :certificate IS NOT NULL)
       ORDER BY mli.primary_land_index_code,
                mli.secondary_land_index_code,
                mli.mark_land_index_desc
      """;

  private static final String CLIENTS_SQL =
      """
      SELECT ffc.client_number                  AS client_number,
             ffc.client_locn_code               AS client_locn_code,
             cli.client_name                    AS client_name,
             loc.city                           AS client_city,
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
        LEFT JOIN the.forest_client cli            ON cli.client_number = ffc.client_number
        LEFT JOIN the.client_location loc
             ON loc.client_number = ffc.client_number
            AND loc.client_locn_code = ffc.client_locn_code
       WHERE pmc.timber_mark = :timberMark
      UNION
      SELECT pcl.client_number                  AS client_number,
             pcl.client_locn_code               AS client_locn_code,
             cli.client_name                    AS client_name,
             loc.city                           AS client_city,
             pcl.private_mark_client_skey       AS for_client_link_skey,
             pcl.private_mark_client_type_code  AS file_client_type,
             fct.description                    AS file_client_type_desc,
             pcl.licensee_start_date            AS licensee_start_dt,
             pcl.licensee_end_date              AS licensee_end_date,
             pcl.revision_count                 AS revision_count
        FROM the.private_mark_client pcl
        JOIN the.file_client_type_code fct
             ON fct.file_client_type_code = pcl.private_mark_client_type_code
        LEFT JOIN the.forest_client cli            ON cli.client_number = pcl.client_number
        LEFT JOIN the.client_location loc
             ON loc.client_number = pcl.client_number
            AND loc.client_locn_code = pcl.client_locn_code
       WHERE pcl.certificate = :certificate
       ORDER BY 1, 2
      """;

  private static final String AMENDMENTS_SQL =
      """
      SELECT tma.amend_request_date AS amend_request_date,
             tma.prv_mrk_amd_sts_st AS prv_mrk_amd_sts_st,
             CASE WHEN pmas.description IS NOT NULL
                  THEN tma.prv_mrk_amd_sts_st || ' - ' || pmas.description END
                                    AS prv_mrk_amd_sts_desc,
             tma.revision_count     AS revision_count,
             tma.requesting_userid  AS requesting_userid,
             tma.permit_block_area  AS permit_block_area,
             tma.p_of_c_or_legal    AS requested_changes
        FROM the.tmbr_mark_amend tma
        LEFT JOIN the.private_mark_amend_status_code pmas
               ON pmas.private_mark_amend_status_code = tma.prv_mrk_amd_sts_st
       WHERE tma.timber_mark = :timberMark
       ORDER BY tma.amend_request_date DESC
      """;

  // FTA_970_FOREST_NOTE.GET: newest first. Notes belong to the forest file, so
  // an application with no forest file yet has none.
  private static final String NOTES_SQL =
      """
      SELECT pn.entry_userid    AS entry_userid,
             pn.entry_timestamp AS entry_timestamp,
             pn.note            AS note
        FROM the.provforest_note pn
       WHERE pn.forest_file_id = :forestFileId
       ORDER BY pn.entry_timestamp DESC
      """;

  /**
   * Loads a single private mark by timber mark, or empty when none exists —
   * mirrors the {@code GET}/{@code mainline} flow of the FTA_510/511/513
   * packages.
   *
   * @param markNumber the timber mark (path id)
   */
  public Optional<MarkDetailDto> findByMarkNumber(String markNumber) {
    return find(markNumber, false);
  }

  /**
   * Loads a single private mark application by certificate — the key an
   * application has before a timber mark is issued.
   *
   * @param certificate the certificate number (path id)
   */
  public Optional<MarkDetailDto> findByCertificate(String certificate) {
    return find(certificate, true);
  }

  private Optional<MarkDetailDto> find(String id, boolean byCertificate) {
    MapSqlParameterSource headerParams = new MapSqlParameterSource()
        .addValue("markNumber", id)
        .addValue("byCertificate", byCertificate ? "Y" : "N");

    MarkDetailDto base;
    try {
      base = jdbc.queryForObject(MARK_SQL, headerParams, (rs, rowNum) -> new MarkDetailDto(
          rs.getString("timber_mark"),
          rs.getString("certificate"),
          rs.getString("forest_file_id"),
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
          rs.getString("mark_status_desc"),
          rs.getString("file_type_desc"),
          rs.getString("district_desc"),
          rs.getString("region_desc"),
          rs.getString("marking_method_desc"),
          rs.getString("marking_instrument_desc"),
          rs.getString("bcaa_folio_number"),
          rs.getString("mgmt_unit_type_code"),
          rs.getString("mgmt_unit_id"),
          rs.getString("mgmt_unit_desc"),
          rs.getString("cascade_split_code"),
          rs.getString("cascade_split_desc"),
          rs.getString("map_reference_reg"),
          rs.getString("map_reference_comp"),
          rs.getObject("mark_extend_date", java.time.LocalDate.class),
          rs.getObject("mark_extend_count", Integer.class),
          rs.getObject("mark_amend_date", java.time.LocalDate.class),
          rs.getString("outstanding_amend_status"),
          rs.getString("outstanding_amend_status_desc"),
          rs.getObject("revision_count", Long.class),
          rs.getObject("amend_revision_count", Long.class),
          "Y".equals(rs.getString("timber_mark_record")),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          null));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }

    // The sub-lists key on whichever of the two the header actually holds.
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("timberMark", base.timberMark())
        .addValue("certificate", base.certificate());

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
            rs.getString("prv_mrk_amd_sts_desc"),
            rs.getObject("revision_count", Integer.class),
            rs.getString("requesting_userid"),
            rs.getBigDecimal("permit_block_area"),
            rs.getString("requested_changes")));

    List<MarkDetailDto.Note> notes = base.forestFileId() == null
        ? List.of()
        : jdbc.query(
            NOTES_SQL,
            new MapSqlParameterSource("forestFileId", base.forestFileId()),
            (rs, rowNum) -> new MarkDetailDto.Note(
                rs.getString("entry_userid"),
                rs.getObject("entry_timestamp", java.time.LocalDateTime.class),
                rs.getString("note")));

    return Optional.of(base.withLists(landIndex, clients, amendments, notes));
  }
}
