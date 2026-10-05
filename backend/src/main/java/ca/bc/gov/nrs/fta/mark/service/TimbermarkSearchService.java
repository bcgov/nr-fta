package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchCriteria;
import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchDto;
import ca.bc.gov.nrs.fta.shared.csv.CsvStreamingJdbc;
import ca.bc.gov.nrs.fta.shared.csv.CsvWriter;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import java.util.List;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Timber-mark search business logic.
 *
 * <p>Ports {@code THE.FTA_002_MARK_SRCH}. The query is assembled rather than
 * fixed, because three of the screen's behaviours are structural rather than
 * simple predicates:
 *
 * <ul>
 *   <li><b>Key search.</b> A valid timber mark makes every other criterion
 *       irrelevant — the legacy body calls {@code fta_Edit_Timber_Mark} and,
 *       when it answers {@code Y}, drops the whole rest of the where clause.
 *       That function only asks whether a {@code HAULING_AUTHORITY} row carries
 *       the mark, so the same question is asked here once, up front, and the
 *       query is built for whichever answer comes back.
 *   <li><b>Land index.</b> Land District, Primary ID and Primary Detail do not
 *       filter the mark at all; they add a derived set of certificates from
 *       {@code MARK_LAND_INDEX} which the mark is then joined against.
 *   <li><b>Salvage.</b> The literal {@code ALL} means "carries any salvage
 *       type", i.e. {@code IS NOT NULL} — not an equality against 'ALL'.
 * </ul>
 *
 * <p>Marks come from {@code V_FTA_TIMBER_MARK_VJ}, which exposes thirteen
 * columns and notably <em>not</em> the management unit or file type — those
 * live on {@code PROV_FOREST_USE}, which is why it is outer-joined even when
 * nothing filters on it.
 */
@Service
public class TimbermarkSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  private final CsvStreamingJdbc streamingJdbc;

  public TimbermarkSearchService(
      NamedParameterJdbcTemplate jdbc, CsvStreamingJdbc streamingJdbc) {
    this.jdbc = jdbc;
    this.streamingJdbc = streamingJdbc;
  }

  /**
   * The mark's licensee, as {@code THE.FTA_UTILS.GET_LICENSEE} picks it.
   *
   * <p>The permit's own licensee ({@code L} client on the harvesting authority)
   * wins; a blanket mark has none, so the file's main ({@code A}) client is used
   * instead. Legacy returns the first row of that union ordered by preference,
   * called per row through two package functions. Here it is a lateral join,
   * added to the page query only — the count never shows the client.
   */
  private static final String LICENSEE_JOINS =
      """
          OUTER APPLY (SELECT cand.client_number, cand.client_locn_code
                         FROM (SELECT hac.client_number, hac.client_locn_code, 0 AS sort_order
                                 FROM the.harvesting_authority_client hac
                                 JOIN the.harvesting_authority ha ON ha.hva_skey = hac.hva_skey
                                WHERE ha.forest_file_id = tm.forest_file_id
                                  AND ha.cutting_permit_id = tm.cutting_permit_id
                                  AND hac.harvest_auth_client_type_code = 'L'
                               UNION
                               SELECT ffc.client_number, ffc.client_locn_code, 2 AS sort_order
                                 FROM the.forest_file_client ffc
                                WHERE ffc.forest_file_id = tm.forest_file_id
                                  AND ffc.forest_file_client_type_code = 'A') cand
                        ORDER BY cand.sort_order
                        FETCH FIRST 1 ROW ONLY) lic
          LEFT JOIN the.forest_client lic_fc ON lic_fc.client_number = lic.client_number
        """;

  /**
   * The licensee number reads {@code "ACRONYM LC"} — acronym (or number) and
   * location, one space apart — and the name is the client's display name. Both
   * as {@code FTA_UTILS.GET_CP_LICENSEE_NUMBER} and {@code _NAME} build them;
   * with no licensee the number is a lone space, as legacy's concatenation of
   * two nulls produces.
   */
  private static final String SELECT_COLUMNS =
      """
      SELECT org.org_unit_code                                                        AS org_unit_code,
      """
          + "       " + ClientNameSql.acronymOrNumber("lic_fc", "lic.client_number")
          + " || ' ' || lic.client_locn_code AS client_number,\n"
          + "       NULL AS client_locn_code,\n"
          + "       CASE WHEN lic.client_number IS NOT NULL THEN "
          + ClientNameSql.displayName("lic_fc") + " END AS client_name,\n"
          + """
             pfu.file_type_code                                                        AS file_type_code,
             pfu.forest_file_id                                                        AS forest_file_id,
             tm.cutting_permit_id                                                      AS cutting_permit_id,
             tm.timber_mark                                                            AS timber_mark,
             tm.certificate                                                            AS certificate,
             tm.mark_status_st                                                         AS mark_status_st,
             tm.mark_issue_date                                                        AS mark_issue_date,
             NVL(tm.mark_extend_date, tm.mark_expiry_date)                             AS mark_expiry_date,
             tm.salvage_type_code                                                      AS salvage_ind,
             tm.hva_skey                                                               AS hva_skey
      """;

  private static final RowMapper<TimbermarkSearchDto> ROW_MAPPER =
      (rs, rowNum) -> new TimbermarkSearchDto(
          rs.getString("org_unit_code"),
          rs.getString("client_number"),
          rs.getString("client_locn_code"),
          rs.getString("client_name"),
          rs.getString("file_type_code"),
          rs.getString("forest_file_id"),
          rs.getString("cutting_permit_id"),
          rs.getString("timber_mark"),
          rs.getString("certificate"),
          rs.getString("mark_status_st"),
          rs.getObject("mark_issue_date", java.time.LocalDate.class),
          rs.getObject("mark_expiry_date", java.time.LocalDate.class),
          rs.getString("salvage_ind"),
          rs.getObject("hva_skey", Long.class));

  /** The generated {@code FROM}, {@code WHERE} and the parameters they bind. */
  private record Query(String from, String where, MapSqlParameterSource params) {}

  public PagedResponse<TimbermarkSearchDto> search(
      TimbermarkSearchCriteria criteria, int page, int size) {
    Query q = build(criteria);

    Long total = jdbc.queryForObject(
        "SELECT COUNT(*)\n" + q.from() + q.where(), q.params(), Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(q.params().getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<TimbermarkSearchDto> rows = jdbc.query(
        resultsQuery(q, criteria.sortBy()) + "\n OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        ROW_MAPPER);

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /**
   * Streams every matching mark to a CSV: the same rows the results table
   * shows, in the same order, with neither a row cap nor paging.
   *
   * <p>Runs {@link #resultsQuery} — not the count's clause — so
   * {@link #LICENSEE_JOINS} is in play and the client columns carry the same
   * values as on screen rather than coming back empty.
   *
   * <p>Columns track the Timber Mark Search table on screen (the frontend
   * page's {@code HEADERS}); keep the two in step.
   */
  public void exportCsv(TimbermarkSearchCriteria criteria, CsvWriter csv) {
    csv.writeRow(
        "District",
        "Client name",
        "Client number",
        "File type",
        "File ID",
        "Cutting permit",
        "Timber mark",
        "Salvage type",
        "Certificate",
        "Mark status",
        "Issue date",
        "Expiry date");

    Query q = build(criteria);
    streamingJdbc.jdbc().query(
        resultsQuery(q, criteria.sortBy()),
        q.params(),
        // Cast required: a void lambda body matches both the RowCallbackHandler
        // and ResultSetExtractor overloads, so the compiler cannot choose.
        (RowCallbackHandler) rs -> csv.writeRow(
            rs.getString("org_unit_code"),
            rs.getString("client_name"),
            rs.getString("client_number"),
            rs.getString("file_type_code"),
            rs.getString("forest_file_id"),
            rs.getString("cutting_permit_id"),
            rs.getString("timber_mark"),
            rs.getString("salvage_ind"),
            rs.getString("certificate"),
            rs.getString("mark_status_st"),
            rs.getObject("mark_issue_date", java.time.LocalDate.class),
            rs.getObject("mark_expiry_date", java.time.LocalDate.class)));
  }

  /**
   * The displayed rows, unpaged — what the page query and the export share.
   *
   * <p>Sorted outside the join: FOREST_CLIENT has a client_name column of its
   * own, and Oracle's OFFSET/FETCH rewrite can resolve an ORDER BY name
   * against the joined tables instead of the select list (ORA-00918). The
   * export keeps the wrapping even without OFFSET, so both read the same
   * columns.
   */
  private static String resultsQuery(Query q, String sortBy) {
    return "SELECT * FROM (\n" + SELECT_COLUMNS + q.from() + LICENSEE_JOINS + q.where() + ")"
        + orderBy(sortBy);
  }

  /** Cutting permit trails the sort key so a row cannot land on two pages. */
  private static String orderBy(String sortBy) {
    if (TimbermarkSearchCriteria.SORT_CLIENT.equals(sortBy)) {
      return "\n ORDER BY client_name, forest_file_id, cutting_permit_id";
    }
    if (TimbermarkSearchCriteria.SORT_FILE_TYPE.equals(sortBy)) {
      return "\n ORDER BY file_type_code, forest_file_id, cutting_permit_id";
    }
    return "\n ORDER BY org_unit_code, forest_file_id, cutting_permit_id";
  }

  private Query build(TimbermarkSearchCriteria c) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    StringBuilder from = new StringBuilder("""
          FROM the.v_fta_timber_mark_vj tm
          JOIN the.org_unit org             ON org.org_unit_no = tm.forest_district
          LEFT JOIN the.prov_forest_use pfu ON pfu.forest_file_id = tm.forest_file_id
        """);
    StringBuilder where = new StringBuilder(" WHERE 1 = 1\n");

    // A timber mark that exists is a key search: the legacy body asks
    // fta_Edit_Timber_Mark and, on 'Y', matches the mark exactly and ignores
    // every other criterion (its `IF l_ignore_criteria = 'N'` guard). A mark
    // that does not exist is an ordinary prefix filter among the rest.
    if (notBlank(c.timberMark())) {
      String mark = c.timberMark().trim().toUpperCase();
      p.addValue("timberMark", mark);
      if (timberMarkExists(mark)) {
        where.append("   AND tm.timber_mark = :timberMark\n");
        return new Query(from.toString(), where.toString(), p);
      }
      where.append("   AND tm.timber_mark LIKE :timberMark || '%'\n");
    }

    if (notBlank(c.adminOrgUnitNo())) {
      p.addValue("adminOrgUnitNo", c.adminOrgUnitNo().trim());
      where.append("   AND (tm.forest_district = TO_NUMBER(:adminOrgUnitNo))\n");
    }
    if (notBlank(c.districtAdminZone())) {
      p.addValue("districtAdminZone", c.districtAdminZone().trim());
      where.append("   AND (tm.district_admn_zone = :districtAdminZone)\n");
    }
    if (notBlank(c.forestFileId())) {
      p.addValue("forestFileId", c.forestFileId().trim());
      where.append("   AND (pfu.forest_file_id LIKE :forestFileId || '%')\n");
    }
    if (notBlank(c.cuttingPermitId())) {
      p.addValue("cuttingPermitId", c.cuttingPermitId().trim());
      where.append("   AND (tm.cutting_permit_id = :cuttingPermitId)\n");
    }
    if (notBlank(c.fileTypeCode())) {
      p.addValue("fileTypeCode", c.fileTypeCode().trim());
      where.append("   AND (pfu.file_type_code = :fileTypeCode)\n");
    }
    if (notBlank(c.markStatusSt())) {
      p.addValue("markStatusSt", c.markStatusSt().trim());
      where.append("   AND (tm.mark_status_st = :markStatusSt)\n");
    }
    if (notBlank(c.mgmtUnitType())) {
      p.addValue("mgmtUnitType", c.mgmtUnitType().trim());
      where.append("   AND (pfu.mgmt_unit_type = :mgmtUnitType)\n");
    }
    if (notBlank(c.mgmtUnitId())) {
      p.addValue("mgmtUnitId", c.mgmtUnitId().trim());
      where.append("   AND (pfu.mgmt_unit_id = :mgmtUnitId)\n");
    }
    if (notBlank(c.certificate())) {
      p.addValue("certificate", c.certificate().trim());
      where.append("   AND (tm.certificate = :certificate)\n");
    }

    // Salvage: 'ALL' asks for any salvage type rather than one in particular.
    if (notBlank(c.salvageTypeCode())) {
      if (TimbermarkSearchCriteria.SALVAGE_ALL.equalsIgnoreCase(c.salvageTypeCode().trim())) {
        where.append("   AND (tm.salvage_type_code IS NOT NULL)\n");
      } else {
        p.addValue("salvageTypeCode", c.salvageTypeCode().trim());
        where.append("   AND (tm.salvage_type_code = :salvageTypeCode)\n");
      }
    }

    if (notBlank(c.issueDateFrom())) {
      p.addValue("issueDateFrom", c.issueDateFrom().trim());
      where.append("   AND ("
          + "tm.mark_issue_date >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.issueDateTo())) {
      p.addValue("issueDateTo", c.issueDateTo().trim());
      where.append("   AND ("
          + "tm.mark_issue_date <= TO_DATE(:issueDateTo, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateFrom())) {
      p.addValue("expiryDateFrom", c.expiryDateFrom().trim());
      where.append("   AND ("
          + "NVL(tm.mark_extend_date, tm.mark_expiry_date)"
          + " >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateTo())) {
      p.addValue("expiryDateTo", c.expiryDateTo().trim());
      where.append("   AND ("
          + "NVL(tm.mark_extend_date, tm.mark_expiry_date)"
          + " <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD'))\n");
    }

    // Amended date: on the certificate, not TIMBER_MARK, so matched like the land index.
    // A renewal is an approved amendment, which stamps it — so this finds a mark by its
    // latest renewal when its original issue date is unknown. Stamped with SYSDATE, so the
    // upper bound takes the whole day.
    if (notBlank(c.amendDateFrom()) || notBlank(c.amendDateTo())) {
      StringBuilder sub = new StringBuilder("""
             AND (tm.certificate IN (
                    SELECT pmc.certificate FROM the.private_mark_certificate pmc WHERE 1 = 1
          """);
      if (notBlank(c.amendDateFrom())) {
        p.addValue("amendDateFrom", c.amendDateFrom().trim());
        sub.append("              AND pmc.private_mark_amend_date"
            + " >= TO_DATE(:amendDateFrom, 'YYYY-MM-DD')\n");
      }
      if (notBlank(c.amendDateTo())) {
        p.addValue("amendDateTo", c.amendDateTo().trim());
        sub.append("              AND pmc.private_mark_amend_date"
            + " < TO_DATE(:amendDateTo, 'YYYY-MM-DD') + 1\n");
      }
      where.append(sub).append("   ))\n");
    }

    // Private marks only: file types drawn from the private-mark code table.
    // A mark with no file header still qualifies, as the legacy clause allows.
    if ("Y".equalsIgnoreCase(nullToEmpty(c.privateMarkOnlyInd()).trim())) {
      where.append("""
             AND ( pfu.file_type_code IN (SELECT pmt.private_mark_type_code
                                            FROM the.private_mark_type_code pmt)
                   OR pfu.file_type_code IS NULL )
          """);
    }

    appendClientCriteria(c, p, where);
    appendLandIndexCriteria(c, p, where);

    return new Query(from.toString(), where.toString(), p);
  }

  /** {@code THE.FTA_EDIT_TIMBER_MARK}: whether a hauling authority carries the mark. */
  private boolean timberMarkExists(String mark) {
    Integer found = jdbc.queryForObject(
        """
        SELECT COUNT(*) FROM dual
         WHERE EXISTS (SELECT 1 FROM the.hauling_authority WHERE timber_mark = :timberMark)
        """,
        new MapSqlParameterSource("timberMark", mark),
        Integer.class);
    return found != null && found > 0;
  }

  /**
   * Client criteria resolve through {@code FOREST_FILE_CLIENT} rather than
   * filtering the mark directly. With no explicit client type the legacy clause
   * spans the main and secondary clients.
   */
  private static void appendClientCriteria(
      TimbermarkSearchCriteria c, MapSqlParameterSource p, StringBuilder where) {
    boolean any = notBlank(c.clientNumber()) || notBlank(c.clientLocnCode())
        || notBlank(c.clientName()) || notBlank(c.fileClientType());
    if (!any) {
      return;
    }
    StringBuilder sub = new StringBuilder(
        "          SELECT ffc.forest_file_id FROM the.forest_file_client ffc WHERE 1 = 1\n");
    if (notBlank(c.fileClientType())) {
      p.addValue("fileClientType", c.fileClientType().trim());
      sub.append("            AND ffc.forest_file_client_type_code = :fileClientType\n");
    } else {
      sub.append("            AND ffc.forest_file_client_type_code IN ('A','B')\n");
    }
    if (notBlank(c.clientNumber())) {
      p.addValue("clientNumber", c.clientNumber().trim());
      sub.append("            AND ffc.client_number = :clientNumber\n");
    }
    if (notBlank(c.clientLocnCode())) {
      p.addValue("clientLocnCode", c.clientLocnCode().trim());
      sub.append("            AND ffc.client_locn_code = :clientLocnCode\n");
    }
    if (notBlank(c.clientName())) {
      p.addValue("clientName", c.clientName().trim());
      sub.append("""
                      AND ffc.client_number IN (SELECT vcp.client_number
                                                  FROM the.v_client_public vcp
                                                 WHERE UPPER(vcp.client_name)
                                                       LIKE UPPER(:clientName) || '%')
          """);
    }
    where.append("   AND (tm.forest_file_id IN (\n")
        .append(sub)
        .append("   ))\n");
  }

  /**
   * Land District, Primary ID and Primary Detail select a set of certificates
   * from the land index; the mark is then matched on certificate. Note the
   * legacy naming crossover — "Land District" is the <em>primary</em> land
   * index code and "Primary ID" the <em>secondary</em> one.
   */
  private static void appendLandIndexCriteria(
      TimbermarkSearchCriteria c, MapSqlParameterSource p, StringBuilder where) {
    boolean any = notBlank(c.landDistrict()) || notBlank(c.primaryId())
        || notBlank(c.primaryDetail());
    if (!any) {
      return;
    }
    StringBuilder sub = new StringBuilder(
        "          SELECT DISTINCT mli.certificate FROM the.mark_land_index mli WHERE 1 = 1\n");
    if (notBlank(c.landDistrict())) {
      p.addValue("landDistrict", c.landDistrict().trim());
      sub.append("            AND mli.primary_land_index_code = :landDistrict\n");
    }
    if (notBlank(c.primaryId())) {
      p.addValue("primaryId", c.primaryId().trim());
      sub.append("            AND mli.secondary_land_index_code = :primaryId\n");
    }
    if (notBlank(c.primaryDetail())) {
      p.addValue("primaryDetail", c.primaryDetail().trim());
      sub.append("            AND NVL(mli.mark_land_index_desc, ' ') = :primaryDetail\n");
    }
    where.append("   AND (tm.certificate IN (\n")
        .append(sub)
        .append("   ))\n");
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
