package ca.bc.gov.nrs.fta.mark.service;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchCriteria;
import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchDto;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.util.List;
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
 *       That test is delegated to the same granted function here, so a mark
 *       that is valid today behaves the same way it does in the legacy screen.
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

  public TimbermarkSearchService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT_COLUMNS =
      """
      SELECT org.org_unit_code                                                        AS org_unit_code,
             the.fta_utils.get_cp_licensee_number(tm.forest_file_id, tm.cutting_permit_id) AS client_number,
             NULL                                                                      AS client_locn_code,
             the.fta_utils.get_cp_licensee_name(tm.forest_file_id, tm.cutting_permit_id)   AS client_name,
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

  /** The generated {@code FROM}/{@code WHERE} and the parameters it binds. */
  private record Query(String fromWhere, MapSqlParameterSource params) {}

  public PagedResponse<TimbermarkSearchDto> search(
      TimbermarkSearchCriteria criteria, int page, int size) {
    Query q = build(criteria);

    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + q.fromWhere(), q.params(), Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(q.params().getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<TimbermarkSearchDto> rows = jdbc.query(
        SELECT_COLUMNS + q.fromWhere() + orderBy(criteria.sortBy())
            + "\n OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        ROW_MAPPER);

    return PagedResponse.ofPage(rows, page, size, totalElements);
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

    // A valid timber mark is a key search: the legacy body asks the database
    // whether the mark exists and, if so, ignores every other criterion. The
    // test runs in SQL so it uses the same answer the legacy screen would get.
    boolean hasMark = notBlank(c.timberMark());
    if (hasMark) {
      p.addValue("timberMark", c.timberMark().trim().toUpperCase());
      where.append("""
             AND ( ( the.fta_edit_timber_mark(:timberMark) = 'Y'
                     AND tm.timber_mark = :timberMark )
                   OR ( the.fta_edit_timber_mark(:timberMark) = 'N'
                        AND tm.timber_mark LIKE :timberMark || '%' ) )
          """);
    }

    // Everything below is skipped on a key search, matching the legacy body's
    // `IF l_ignore_criteria = 'N'` guard. Expressed as a SQL guard rather than
    // a Java branch so one statement covers both cases.
    String guard = hasMark ? "the.fta_edit_timber_mark(:timberMark) = 'Y' OR " : "";

    if (notBlank(c.adminOrgUnitNo())) {
      p.addValue("adminOrgUnitNo", c.adminOrgUnitNo().trim());
      where.append("   AND (" + guard + "tm.forest_district = TO_NUMBER(:adminOrgUnitNo))\n");
    }
    if (notBlank(c.districtAdminZone())) {
      p.addValue("districtAdminZone", c.districtAdminZone().trim());
      where.append("   AND (" + guard + "tm.district_admn_zone = :districtAdminZone)\n");
    }
    if (notBlank(c.forestFileId())) {
      p.addValue("forestFileId", c.forestFileId().trim());
      where.append("   AND (" + guard + "pfu.forest_file_id LIKE :forestFileId || '%')\n");
    }
    if (notBlank(c.cuttingPermitId())) {
      p.addValue("cuttingPermitId", c.cuttingPermitId().trim());
      where.append("   AND (" + guard + "tm.cutting_permit_id = :cuttingPermitId)\n");
    }
    if (notBlank(c.fileTypeCode())) {
      p.addValue("fileTypeCode", c.fileTypeCode().trim());
      where.append("   AND (" + guard + "pfu.file_type_code = :fileTypeCode)\n");
    }
    if (notBlank(c.markStatusSt())) {
      p.addValue("markStatusSt", c.markStatusSt().trim());
      where.append("   AND (" + guard + "tm.mark_status_st = :markStatusSt)\n");
    }
    if (notBlank(c.mgmtUnitType())) {
      p.addValue("mgmtUnitType", c.mgmtUnitType().trim());
      where.append("   AND (" + guard + "pfu.mgmt_unit_type = :mgmtUnitType)\n");
    }
    if (notBlank(c.mgmtUnitId())) {
      p.addValue("mgmtUnitId", c.mgmtUnitId().trim());
      where.append("   AND (" + guard + "pfu.mgmt_unit_id = :mgmtUnitId)\n");
    }
    if (notBlank(c.certificate())) {
      p.addValue("certificate", c.certificate().trim());
      where.append("   AND (" + guard + "tm.certificate = :certificate)\n");
    }

    // Salvage: 'ALL' asks for any salvage type rather than one in particular.
    if (notBlank(c.salvageTypeCode())) {
      if (TimbermarkSearchCriteria.SALVAGE_ALL.equalsIgnoreCase(c.salvageTypeCode().trim())) {
        where.append("   AND (" + guard + "tm.salvage_type_code IS NOT NULL)\n");
      } else {
        p.addValue("salvageTypeCode", c.salvageTypeCode().trim());
        where.append("   AND (" + guard + "tm.salvage_type_code = :salvageTypeCode)\n");
      }
    }

    if (notBlank(c.issueDateFrom())) {
      p.addValue("issueDateFrom", c.issueDateFrom().trim());
      where.append("   AND (" + guard
          + "tm.mark_issue_date >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.issueDateTo())) {
      p.addValue("issueDateTo", c.issueDateTo().trim());
      where.append("   AND (" + guard
          + "tm.mark_issue_date <= TO_DATE(:issueDateTo, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateFrom())) {
      p.addValue("expiryDateFrom", c.expiryDateFrom().trim());
      where.append("   AND (" + guard
          + "NVL(tm.mark_extend_date, tm.mark_expiry_date)"
          + " >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateTo())) {
      p.addValue("expiryDateTo", c.expiryDateTo().trim());
      where.append("   AND (" + guard
          + "NVL(tm.mark_extend_date, tm.mark_expiry_date)"
          + " <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD'))\n");
    }

    // Private marks only: file types drawn from the private-mark code table.
    // A mark with no file header still qualifies, as the legacy clause allows.
    if ("Y".equalsIgnoreCase(nullToEmpty(c.privateMarkOnlyInd()).trim())) {
      where.append("""
             AND ( %spfu.file_type_code IN (SELECT pmt.private_mark_type_code
                                              FROM the.private_mark_type_code pmt)
                   OR pfu.file_type_code IS NULL )
          """.formatted(guard));
    }

    appendClientCriteria(c, guard, p, where);
    appendLandIndexCriteria(c, guard, p, where);

    return new Query(from + where.toString(), p);
  }

  /**
   * Client criteria resolve through {@code FOREST_FILE_CLIENT} rather than
   * filtering the mark directly. With no explicit client type the legacy clause
   * spans the main and secondary clients.
   */
  private static void appendClientCriteria(
      TimbermarkSearchCriteria c, String guard, MapSqlParameterSource p, StringBuilder where) {
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
    where.append("   AND (" + guard + "tm.forest_file_id IN (\n")
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
      TimbermarkSearchCriteria c, String guard, MapSqlParameterSource p, StringBuilder where) {
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
    where.append("   AND (" + guard + "tm.certificate IN (\n")
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
