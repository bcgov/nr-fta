package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.MarkListDto;
import ca.bc.gov.nrs.fta.shared.csv.CsvStreamingJdbc;
import ca.bc.gov.nrs.fta.shared.csv.CsvWriter;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.util.List;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Private Mark Application/Amendment list business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_500_MARK_LIST} (the
 * {@code GET} procedure driven by {@code mainline} with {@code p_action = 'GET'})
 * to a native query against the shared {@code THE} schema. The union mirrors the
 * package's district-level list: pending/issued mark applications
 * ({@code process_type = 'APPL'}) plus outstanding amendments
 * ({@code process_type = 'AMEND'}). Column selection matches the package's
 * {@code rec_mark_list} record.
 *
 * <p>The legacy {@code Sil_Get_Client_Name(client_number)} PL/SQL lookup is
 * replaced with a join to {@code THE.client} (matching the tenure exemplar).
 * Each filter is applied only when its bind value is supplied (NVL-style),
 * matching the legacy {@code LIKE NVL(...,'%')} behaviour — except the district,
 * which is matched exactly (see {@link #list}).
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class MarkListService {

  private final NamedParameterJdbcTemplate jdbc;
  private final CsvStreamingJdbc streamingJdbc;

  public MarkListService(NamedParameterJdbcTemplate jdbc, CsvStreamingJdbc streamingJdbc) {
    this.jdbc = jdbc;
    this.streamingJdbc = streamingJdbc;
  }

  private static final String SELECT_COLUMNS =
      """
      SELECT m.process_type          AS process_type,
             m.certificate           AS certificate,
             m.timber_mark           AS timber_mark,
             m.mark_appl_date        AS mark_appl_date,
             m.org_unit_code         AS org_unit_code,
             m.mark_status_st        AS mark_status_st,
             m.client_name           AS client_name,
             m.disable_print_ind     AS disable_print_ind,
             m.disable_ack_ind       AS disable_ack_ind,
             m.tm_revision_count     AS tm_revision_count,
             m.amend_revision_count  AS amend_revision_count,
             m.idir                  AS idir
      """;

  /**
   * The union of applications and amendments, plus the filters — shared verbatim
   * by the page query and the count. The filters sit outside the inline view, so
   * wrapping this in {@code COUNT(*)} counts exactly the rows the page returns.
   */
  private static final String FROM_WHERE =
      """
        FROM (
              SELECT 'APPL'                                                AS process_type,
                     pmc.certificate                                      AS certificate,
                     pmc.timber_mark                                      AS timber_mark,
                     pmc.private_mark_application_date                    AS mark_appl_date,
                     ou.org_unit_code                                     AS org_unit_code,
                     ou.org_unit_no                                       AS org_unit_no,
                     pmc.private_mark_status_code                         AS mark_status_st,
                     NVL(cli.client_name, 'Client not specified at present') AS client_name,
                     cli.client_number                                    AS client_number,
                     CASE WHEN fcl.client_number IS NOT NULL
                          THEN fcl.client_locn_code ELSE ffc.client_locn_code END AS client_locn_code,
                     CASE WHEN pmc.private_mark_status_code = 'HN' THEN 'N' ELSE 'Y' END AS disable_print_ind,
                     CASE WHEN pmc.private_mark_status_code = 'DV' THEN 'N' ELSE 'Y' END AS disable_ack_ind,
                     pmc.revision_count                                   AS tm_revision_count,
                     CAST(NULL AS NUMBER)                                 AS amend_revision_count,
                     pmc.update_userid                                    AS idir
                FROM the.private_mark_certificate pmc
                JOIN the.org_unit ou              ON ou.org_unit_no = pmc.forest_district
                LEFT JOIN the.private_mark_client fcl
                       ON fcl.certificate = pmc.certificate
                      AND fcl.private_mark_client_type_code = 'A'
                LEFT JOIN the.forest_file_client ffc
                       ON ffc.forest_file_id = pmc.forest_file_id
                      AND ffc.forest_file_client_type_code = 'A'
                LEFT JOIN the.forest_client cli
                       ON cli.client_number = COALESCE(fcl.client_number, ffc.client_number)
               WHERE pmc.private_mark_status_code IN ('HN','PA','PI','DV')
                 AND ( (pmc.timber_mark IS NOT NULL
                        AND pmc.timber_mark NOT IN (SELECT a.timber_mark
                                                      FROM the.tmbr_mark_amend a
                                                     WHERE a.prv_mrk_amd_sts_st IN ('PI','DV','HN')))
                       OR pmc.timber_mark IS NULL )
              UNION
              SELECT 'AMEND'                                              AS process_type,
                     pmc.certificate                                      AS certificate,
                     pmc.timber_mark                                      AS timber_mark,
                     pmc.private_mark_application_date                    AS mark_appl_date,
                     ou.org_unit_code                                     AS org_unit_code,
                     ou.org_unit_no                                       AS org_unit_no,
                     amd.prv_mrk_amd_sts_st                               AS mark_status_st,
                     cli.client_name                                      AS client_name,
                     cli.client_number                                    AS client_number,
                     fcl.client_locn_code                                 AS client_locn_code,
                     CASE WHEN amd.prv_mrk_amd_sts_st = 'HN' THEN 'N' ELSE 'Y' END AS disable_print_ind,
                     CASE WHEN amd.prv_mrk_amd_sts_st = 'DV' THEN 'N' ELSE 'Y' END AS disable_ack_ind,
                     pmc.revision_count                                   AS tm_revision_count,
                     amd.revision_count                                   AS amend_revision_count,
                     -- Whoever touched the row last: the amendment or the mark.
                     CASE WHEN amd.update_timestamp >= pmc.update_timestamp
                          THEN amd.update_userid ELSE pmc.update_userid END AS idir
                FROM the.private_mark_certificate pmc
                JOIN the.prov_forest_use pfu      ON pfu.forest_file_id = pmc.forest_file_id
                JOIN the.org_unit ou              ON ou.org_unit_no = pmc.forest_district
                JOIN the.tmbr_mark_amend amd      ON amd.timber_mark = pmc.timber_mark
                LEFT JOIN the.forest_file_client fcl
                       ON fcl.forest_file_id = pmc.forest_file_id
                      AND fcl.forest_file_client_type_code = 'A'
                LEFT JOIN the.forest_client cli          ON cli.client_number = fcl.client_number
               WHERE pfu.file_type_code IN (SELECT pmt.private_mark_type_code
                                              FROM the.private_mark_type_code pmt)
                 AND pmc.private_mark_status_code IN ('HI','HX')
                 AND amd.prv_mrk_amd_sts_st IN ('PI','HN','DV')
             ) m
       WHERE (:hdrDistrict  IS NULL OR TO_CHAR(m.org_unit_no) = :hdrDistrict)
         AND (:timberMark   IS NULL OR m.timber_mark LIKE :timberMark || '%')
         AND (:markStatusSt IS NULL OR m.mark_status_st = :markStatusSt)
         AND (:orgUnitCode  IS NULL OR m.org_unit_code = :orgUnitCode)
         AND (:clientName   IS NULL OR UPPER(m.client_name) LIKE UPPER(:clientName) || '%')
         AND (:clientNumber IS NULL OR m.client_number = :clientNumber)
         AND (:clientLocnCode IS NULL OR m.client_locn_code = :clientLocnCode)
      """;

  // Newest application first; undated rows last rather than Oracle's default
  // of first for DESC. Certificate and process type break ties so the order is
  // deterministic and a row cannot appear on two pages once OFFSET is applied —
  // an application and its amendment share a certificate.
  private static final String ORDER_BY =
      " ORDER BY m.mark_appl_date DESC NULLS LAST, m.certificate, m.process_type\n";

  /**
   * Private mark application/amendment list — mirrors
   * {@code FTA_500_MARK_LIST.mainline} with {@code p_action = 'GET'}.
   *
   * @param hdrDistrict  administrative district org-unit number, or null. Exact:
   *                     the value comes from a picker, and a prefix match would
   *                     let district 18 also return 1867's marks
   * @param timberMark   partial timber mark (prefix match), or null
   * @param markStatusSt exact mark/amendment status code, or null
   * @param orgUnitCode  exact org-unit code, or null
   * @param clientName   client/holder name (prefix match), or null
   * @param clientNumber exact client number (a picked client), or null
   * @param clientLocnCode exact client location, with a picked client, or null
   * @param page         0-indexed page number
   * @param size         rows per page
   */
  public PagedResponse<MarkListDto> list(
      String hdrDistrict,
      String timberMark,
      String markStatusSt,
      String orgUnitCode,
      String clientName,
      String clientNumber,
      String clientLocnCode,
      int page,
      int size) {
    MapSqlParameterSource params =
        filters(hdrDistrict, timberMark, markStatusSt, orgUnitCode, clientName,
            clientNumber, clientLocnCode);

    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + FROM_WHERE, params, Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(params.getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<MarkListDto> rows = jdbc.query(
        SELECT_COLUMNS + FROM_WHERE + ORDER_BY
            + " OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        (rs, rowNum) -> new MarkListDto(
        rs.getString("process_type"),
        rs.getString("certificate"),
        rs.getString("timber_mark"),
        rs.getObject("mark_appl_date", java.time.LocalDate.class),
        rs.getString("org_unit_code"),
        rs.getString("mark_status_st"),
        rs.getString("client_name"),
        rs.getString("disable_print_ind"),
        rs.getString("disable_ack_ind"),
        rs.getObject("tm_revision_count", Long.class),
            rs.getObject("amend_revision_count", Long.class),
            rs.getString("idir")));

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /**
   * Streams every matching row to a CSV: the rows the list shows, in the same
   * order, with neither a row cap nor paging.
   *
   * <p>Columns track the Private Mark Applications table on screen (the
   * frontend MarkList page's {@code HEADERS}); keep the two in step.
   */
  public void exportCsv(
      String hdrDistrict,
      String timberMark,
      String markStatusSt,
      String orgUnitCode,
      String clientName,
      String clientNumber,
      String clientLocnCode,
      CsvWriter csv) {
    csv.writeRow(
        "Certificate", "Timber mark", "Application date", "District", "Status", "Client",
        "Last updated by");

    streamingJdbc.jdbc().query(
        SELECT_COLUMNS + FROM_WHERE + ORDER_BY,
        filters(hdrDistrict, timberMark, markStatusSt, orgUnitCode, clientName,
            clientNumber, clientLocnCode),
        // Cast required: a void lambda body matches both the RowCallbackHandler
        // and ResultSetExtractor overloads, so the compiler cannot choose.
        (RowCallbackHandler) rs -> csv.writeRow(
            rs.getString("certificate"),
            rs.getString("timber_mark"),
            rs.getObject("mark_appl_date", java.time.LocalDate.class),
            rs.getString("org_unit_code"),
            rs.getString("mark_status_st"),
            rs.getString("client_name"),
            rs.getString("idir")));
  }

  /** The filter binds, shared by the page query, the count and the export. */
  private static MapSqlParameterSource filters(
      String hdrDistrict,
      String timberMark,
      String markStatusSt,
      String orgUnitCode,
      String clientName,
      String clientNumber,
      String clientLocnCode) {
    return new MapSqlParameterSource()
        .addValue("hdrDistrict", blankToNull(hdrDistrict))
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("markStatusSt", blankToNull(markStatusSt))
        .addValue("orgUnitCode", blankToNull(orgUnitCode))
        .addValue("clientName", blankToNull(clientName))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientLocnCode", blankToNull(clientLocnCode));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
