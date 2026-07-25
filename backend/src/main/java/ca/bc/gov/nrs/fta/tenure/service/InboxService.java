package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.tenure.dto.InboxDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * FTA300 Inbox worklist business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_300N_INBOX} inbox query to a
 * native query against the shared {@code THE} schema. The SQL is derived from the
 * package body's {@code GET} procedure — specifically the general (non-BCTS,
 * BCTS-files-included) {@code ELSE} branch — which opens {@code rec_inbox} rows
 * for pending electronic submissions ({@code TENURE_APPLICATION_STATE_CODE =
 * 'INB'}). Each search filter is applied only when its bind value is supplied
 * (NVL-style), matching the legacy behaviour; the user-context flags
 * ({@code msrmInd}, {@code recStaffInd}, etc.) default to 'N' as the legacy Java
 * layer always supplies them.
 *
 * <p>NOTE: the derived, per-row indicator columns (approve/reject enablement,
 * ESF hyperlinks, Exhibit A actions, file action link, HVA keys, licensee name,
 * CP-qualified file id) are produced by the same legacy {@code THE} standalone
 * and {@code THE.FTA_300N_INBOX} package functions the original cursor calls, so
 * the ported result matches column-for-column. The query runs against the BC Gov
 * shared Oracle ({@code THE}); there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class InboxService {

  private final NamedParameterJdbcTemplate jdbc;

  public InboxService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String INBOX_SQL =
      """
      SELECT ta.tenure_app_id      AS tenure_app_id
           , ta.submission_id      AS submission_id
           , ta.max_x              AS max_x
           , ta.min_x              AS min_x
           , ta.max_y              AS max_y
           , ta.min_y              AS min_y
           , ta.client_number      AS client_number
           , ta.revision_count     AS revision_count
           , tai.revision_count    AS tai_revision_count
           , ta.forest_file_id     AS forest_file_id
           , the.fta_300_cp_id(ta.tenure_application_type_code, ta.tenure_app_id, ta.forest_file_id)
                                    AS forest_file_id_display
           , ta.current_assigned_to AS current_assigned_to
           , tatc.file_type_code || ' - ' || tatc.description AS tenure_application_type
           , ou.org_unit_name      AS org_unit_name
           , ta.org_unit_no        AS org_unit_no
           , hs.bcts_org_unit      AS file_bcts_org
           , the.sil_get_org_unit_code(hs.bcts_org_unit) AS bcts_org_code
           , the.sil_get_client_name(fc.client_number)   AS licensee
           , ta.submission_date    AS submission_date
           , DECODE(ta.tenure_application_type_code, 'OIL', 'Y', ta.adjudication_ind) AS adjudication_ind
           , DECODE(the.fta_job_memo(ta.tenure_app_id), 'Y', 'YES', 'NO') AS job_memo
           , the.fta_adj_report_ind(ta.tenure_app_id) AS adj_report_ind
           , hs.sb_fund_ind        AS bcts_file_ind
           , ta.tenure_application_type_code AS application_type_code
           , SUBSTR(purp.description, 1, 5)  AS tenure_app_purp_code
           , ta.image_created_ind  AS exh_a_image_ind
           , DECODE(the.fta_300n_inbox.get_hva_id(ta.tenure_app_id), NULL,
                    DECODE(ta.tenure_application_type_code, 'CP', 'FTA902CPDETAILACTION/Go'
                                                          , 'TL', 'FTA980TLBLOCKACTION/Go'
                                                          , 'TLE', 'FTA980TLBLOCKACTION/Go'
                                                          , 'FTA100TENUREACTION/Go'),
                    'FTA902CPDETAILACTION/Go') AS file_action_link
           , the.fta_300n_inbox.approve_allowed(ta.tenure_application_type_code, ta.adjudication_ind,
                    hs.sb_fund_ind, pfu.file_type_code, the.sil_get_org_level(:userOrgUnitNo),
                    hs.bcts_org_unit, :msrmInd, :mapTechInd, :recStaffInd, :seniorAdminInd,
                    ta.current_assigned_to) AS approve_enabled_ind
           , the.fta_300n_inbox.reject_allowed(ta.tenure_application_type_code, hs.sb_fund_ind,
                    pfu.file_type_code, the.sil_get_org_level(:userOrgUnitNo), hs.bcts_org_unit,
                    :msrmInd, :recStaffInd, :seniorAdminInd, ta.current_assigned_to) AS reject_enabled_ind
           , the.fta_300n_inbox.esf_hyperlink(:msrmInd, ta.current_assigned_to,
                    the.sil_get_org_level(:userOrgUnitNo), ta.tenure_application_type_code)
                                    AS esf_hyperlink_ind
           , the.fta_get_clear_unclear_allowed(the.sil_get_org_level(:userOrgUnitNo), :msrmInd,
                    :recStaffInd, :seniorAdminInd, ta.tenure_application_type_code,
                    ta.current_assigned_to) AS exh_a_action_ind
           , the.fta_300n_inbox.bcts_esf_hyperlink(:msrmInd, ta.current_assigned_to,
                    the.sil_get_org_level(:userOrgUnitNo), ta.tenure_application_type_code)
                                    AS bcts_esf_hyperlink_ind
           , the.fta_300n_inbox.bcts_clear_unclear_allowed(the.sil_get_org_level(:userOrgUnitNo),
                    :msrmInd, :recStaffInd, :seniorAdminInd, ta.tenure_application_type_code,
                    ta.current_assigned_to) AS bcts_exh_a_action_ind
           , DECODE(the.fta_300n_inbox.get_hva_id(ta.tenure_app_id), NULL,
                    DECODE(ta.tenure_application_type_code, 'CP', 'Navigates to FTA902 CP Detail screen'
                                                          , 'TL', 'Navigates to FTA980 TL Block screen'
                                                          , 'TLE', 'Navigates to FTA980 TL Block screen'
                                                          , 'Navigates to FTA100 Tenure screen'),
                    'Navigates to FTA902 CP Detail screen') AS file_bubble_help
           , ta.image_create_in_progress_ind AS regen_in_progress_ind
           , ta.image_mime_type_code AS image_mime_type_code
           , the.fta_300n_inbox.get_hva_skey(ta.tenure_app_id) AS hva_skey
           , the.fta_300n_inbox.get_hva_id(ta.tenure_app_id)   AS hva_id
        FROM the.tenure_application ta
           , the.tenure_application_image tai
           , the.org_unit ou
           , the.v_client_public fc
           , the.file_type_code tatc
           , the.prov_forest_use pfu
           , the.harvest_sale hs
           , the.tenure_application_purp_code purp
       WHERE ta.tenure_application_state_code = 'INB'
         AND ta.tenure_app_id = tai.tenure_app_id (+)
         AND ta.forest_file_id = pfu.forest_file_id
         AND pfu.file_type_code = tatc.file_type_code
         AND pfu.file_type_code LIKE NVL(:fileTypeCode, '%')
         AND purp.tenure_application_purp_code = ta.tenure_app_purp_code
         AND ta.tenure_application_type_code LIKE NVL(:applTypeCode, '%')
         AND ta.org_unit_no = ou.org_unit_no
         AND ta.client_number = fc.client_number (+)
         AND ta.client_number = NVL(:clientNumber, ta.client_number)
         AND ta.client_locn_code = NVL(:clientLocnCode, ta.client_locn_code)
         AND pfu.forest_file_id = hs.forest_file_id (+)
         AND ta.adjudication_ind =
             DECODE(:exACleared, NULL, ta.adjudication_ind, 'Y', 'Y', 'N', 'N')
         AND ta.submission_date BETWEEN TO_DATE(NVL(:dateFrom, '2002-12-01'), 'yyyy-mm-dd')
                                    AND TO_DATE(NVL(:dateTo, '9999-12-31'), 'yyyy-mm-dd')
         AND ((ou.org_level_code = 'D' AND ou.rollup_region_no = TO_NUMBER(:orgUnit))
              OR (ou.org_unit_no = TO_NUMBER(:orgUnit))
              OR (:orgUnit IS NULL))
         AND (ta.forest_file_id LIKE :forestFileId || '%' OR :forestFileId IS NULL)
         AND (((:msrmInd = 'N'
                AND ta.current_assigned_to = DECODE(:excludeSrmInd, 'Y', 'FOR', ta.current_assigned_to))
               OR (:msrmInd = 'Y' AND ta.current_assigned_to = 'SRM')))
         AND ((:harvestTypeCode IS NULL) OR EXISTS (
                 SELECT DISTINCT tamf.tenure_app_id
                   FROM the.tenure_application_map_feature tamf
                      , the.harvest_authority_amend_geom haag
                      , the.harvest_authority_amend haa
                      , the.harvesting_authority hva
                  WHERE tamf.map_feature_id = haag.map_feature_id
                    AND haag.hva_skey = haa.hva_skey
                    AND haag.amendment_id = haa.amendment_id
                    AND haa.hva_skey = hva.hva_skey
                    AND tamf.tenure_app_id = ta.tenure_app_id
                    AND hva.harvest_type_code = :harvestTypeCode))
       ORDER BY DECODE(:sortBy, '2', ta.forest_file_id
                              , '3', ta.adjudication_ind
                              , '4', ta.client_number
                              , TO_CHAR(ta.submission_date, 'yyyy-mm-dd')) ASC
              , ta.submission_id ASC
      """;

  /**
   * Inbox worklist search — mirrors {@code FTA_300N_INBOX.GET} (general branch).
   *
   * @param forestFileId   partial forest-file id (prefix match), or null
   * @param orgUnit        administrative org-unit no (district roll-up aware), or null
   * @param applTypeCode   tenure-application type code (LIKE), or null
   * @param fileTypeCode   provincial-forest-use file-type code (LIKE), or null
   * @param clientNumber   exact client number, or null
   * @param clientLocnCode exact client location code, or null
   * @param harvestTypeCode harvesting-authority harvest-type code, or null
   * @param dateFrom       submission-date lower bound (yyyy-mm-dd), or null
   * @param dateTo         submission-date upper bound (yyyy-mm-dd), or null
   * @param exACleared     Exhibit A cleared filter ('Y'/'N'), or null for any
   * @param sortBy         legacy sort selector ('2'/'3'/'4'), or null for date
   * @param msrmInd        MSRM-user context flag ('Y'/'N'); defaults to 'N'
   * @param mapTechInd     map-tech context flag ('Y'/'N'); defaults to 'N'
   * @param recStaffInd    recreation-staff context flag ('Y'/'N'); defaults to 'N'
   * @param seniorAdminInd senior-admin context flag ('Y'/'N'); defaults to 'N'
   * @param excludeSrmInd  exclude-SRM context flag ('Y'/'N'); defaults to 'N'
   * @param userOrgUnitNo  requesting user's org-unit no (for org-level checks), or null
   */
  public List<InboxDto> search(
      String forestFileId,
      String orgUnit,
      String applTypeCode,
      String fileTypeCode,
      String clientNumber,
      String clientLocnCode,
      String harvestTypeCode,
      String dateFrom,
      String dateTo,
      String exACleared,
      String sortBy,
      String msrmInd,
      String mapTechInd,
      String recStaffInd,
      String seniorAdminInd,
      String excludeSrmInd,
      String userOrgUnitNo) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("orgUnit", blankToNull(orgUnit))
        .addValue("applTypeCode", blankToNull(applTypeCode))
        .addValue("fileTypeCode", blankToNull(fileTypeCode))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientLocnCode", blankToNull(clientLocnCode))
        .addValue("harvestTypeCode", blankToNull(harvestTypeCode))
        .addValue("dateFrom", blankToNull(dateFrom))
        .addValue("dateTo", blankToNull(dateTo))
        .addValue("exACleared", blankToNull(exACleared))
        .addValue("sortBy", blankToNull(sortBy))
        .addValue("msrmInd", defaultInd(msrmInd))
        .addValue("mapTechInd", defaultInd(mapTechInd))
        .addValue("recStaffInd", defaultInd(recStaffInd))
        .addValue("seniorAdminInd", defaultInd(seniorAdminInd))
        .addValue("excludeSrmInd", defaultInd(excludeSrmInd))
        .addValue("userOrgUnitNo", blankToNull(userOrgUnitNo));

    return jdbc.query(INBOX_SQL, params, (rs, rowNum) -> new InboxDto(
        rs.getObject("tenure_app_id", Long.class),
        rs.getObject("submission_id", Long.class),
        rs.getObject("max_x", Double.class),
        rs.getObject("min_x", Double.class),
        rs.getObject("max_y", Double.class),
        rs.getObject("min_y", Double.class),
        rs.getString("client_number"),
        rs.getObject("revision_count", Long.class),
        rs.getObject("tai_revision_count", Long.class),
        rs.getString("forest_file_id"),
        rs.getString("forest_file_id_display"),
        rs.getString("current_assigned_to"),
        rs.getString("tenure_application_type"),
        rs.getString("org_unit_name"),
        rs.getObject("org_unit_no", Long.class),
        rs.getObject("file_bcts_org", Long.class),
        rs.getString("bcts_org_code"),
        rs.getString("licensee"),
        rs.getObject("submission_date", java.time.LocalDate.class),
        rs.getString("adjudication_ind"),
        rs.getString("job_memo"),
        rs.getString("adj_report_ind"),
        rs.getString("bcts_file_ind"),
        rs.getString("application_type_code"),
        rs.getString("tenure_app_purp_code"),
        rs.getString("exh_a_image_ind"),
        rs.getString("file_action_link"),
        rs.getString("approve_enabled_ind"),
        rs.getString("reject_enabled_ind"),
        rs.getString("esf_hyperlink_ind"),
        rs.getString("exh_a_action_ind"),
        rs.getString("bcts_esf_hyperlink_ind"),
        rs.getString("bcts_exh_a_action_ind"),
        rs.getString("file_bubble_help"),
        rs.getString("regen_in_progress_ind"),
        rs.getString("image_mime_type_code"),
        rs.getObject("hva_skey", Long.class),
        rs.getString("hva_id")));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  /** Context flags are never null in the legacy call; default a missing one to 'N'. */
  private static String defaultInd(String s) {
    return (s == null || s.isBlank()) ? "N" : s;
  }
}
