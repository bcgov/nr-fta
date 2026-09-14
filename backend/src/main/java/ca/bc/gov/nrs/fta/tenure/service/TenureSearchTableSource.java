package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tenure search over the {@code THE} tables directly. The default mode.
 *
 * <p>The query is assembled rather than fixed, because the legacy screen's
 * criteria do not all reduce to "AND column = value". Its shape follows
 * {@code FTA_001_TENR_SRCH.BUILD_WHERE_CLAUSE} rather than being invented —
 * several of the obvious guesses are wrong:
 *
 * <ul>
 *   <li>the file header is {@code PROV_FOREST_USE}; there is no
 *       {@code FOREST_FILE} table, and the org unit hangs off
 *       {@code pfu.forest_region}, not an "admin district" column;</li>
 *   <li>issue and expiry dates live on {@code TENURE_TERM}
 *       ({@code legal_effective_dt}, {@code NVL(current_expiry_dt,
 *       initial_expiry_dt)}), and the legacy screen ignores them entirely for
 *       file types {@code B40}/{@code C01} and for recreation files;</li>
 *   <li>Admin Org Unit is not an equality — a <em>region</em> matches every
 *       unit rolling up to it, while a <em>district</em> matches through
 *       {@code FTA_GET_FILE_ORG_CODE} with two file-type exceptions;</li>
 *   <li>naming a client turns the client join from outer to inner, which
 *       changes which files come back at all;</li>
 *   <li>File Type accepts several codes, comma-separated.</li>
 * </ul>
 *
 * <p>Two deliberate simplifications against the legacy SQL, both documented at
 * the point of use: its {@code fcl_filter} self-join collapses into {@code fcl}
 * (the join keys make them the same row), and the client-name filter reads
 * {@code FOREST_CLIENT} rather than {@code V_CLIENT_PUBLIC}.
 */
@Component
@ConditionalOnProperty(name = "fta.data-access.mode", havingValue = "table", matchIfMissing = true)
public class TenureSearchTableSource implements TenureSearchSource {

  private final NamedParameterJdbcTemplate jdbc;

  public TenureSearchTableSource(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * File types whose administering office is the region rather than the
   * district. Lifted verbatim from the package's {@code L_REGION_TYPE}.
   */
  private static final String REGION_FILE_TYPES =
      "'A01','A02','A06','A27','A28','A29','A30','A31'";

  /** File types that match the district on {@code forest_region} directly. */
  private static final String DISTRICT_FILE_TYPES = "'M01','B10'";

  /** File types for which the legacy screen suppresses the date filters. */
  private static final List<String> DATELESS_FILE_TYPES = List.of("B40", "C01");

  private static final String SELECT_COLUMNS =
      """
      SELECT the.fta_get_file_org_code(pfu.forest_file_id, pfu.file_type_code, org.org_unit_code)
                                         AS org_unit_code,
             fcl.client_number           AS client_number,
             fcl.client_locn_code        AS client_locn_code,
             cli.client_name             AS client_name,
             pfu.forest_file_id          AS forest_file_id,
             pfu.file_type_code          AS file_type_code,
             fclt.description            AS file_client_type_desc,
             pfu.mgmt_unit_type          AS mgmt_unit_type,
             pfu.mgmt_unit_id            AS mgmt_unit_id,
             pfu.file_status_st          AS file_status_code,
             sts.description             AS file_status_desc,
             tt.legal_effective_dt       AS issue_date,
             NVL(tt.current_expiry_dt, tt.initial_expiry_dt) AS expiry_date
      """;

  private static final RowMapper<TenureSummaryDto> ROW_MAPPER =
      (rs, rowNum) -> new TenureSummaryDto(
          rs.getString("org_unit_code"),
          rs.getString("client_number"),
          rs.getString("client_locn_code"),
          rs.getString("client_name"),
          rs.getString("forest_file_id"),
          rs.getString("file_type_code"),
          rs.getString("file_client_type_desc"),
          rs.getString("mgmt_unit_type"),
          rs.getString("mgmt_unit_id"),
          rs.getString("file_status_code"),
          rs.getString("file_status_desc"),
          rs.getObject("issue_date", java.time.LocalDate.class),
          rs.getObject("expiry_date", java.time.LocalDate.class));

  /** The generated {@code FROM}/{@code WHERE} and the parameters it binds. */
  private record Query(String fromWhere, MapSqlParameterSource params) {}

  @Override
  public PagedResponse<TenureSummaryDto> search(
      TenureSearchCriteria criteria, int page, int size) {
    Query q = build(criteria);

    // Count first, against the same clause. A page past the end then costs only
    // the count rather than a pointless row fetch.
    Long total = jdbc.queryForObject("SELECT COUNT(*)\n" + q.fromWhere(), q.params(), Long.class);
    long totalElements = total == null ? 0L : total;

    String sql = SELECT_COLUMNS + q.fromWhere() + orderBy(criteria.sortBy())
        + "\n OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(q.params().getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<TenureSummaryDto> rows = jdbc.query(sql, pageParams, ROW_MAPPER);
    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /**
   * Sort options offered by the screen. The file id is always the tiebreaker —
   * without a deterministic order, Oracle may return the same row on two pages.
   */
  private static String orderBy(String sortBy) {
    if (TenureSearchCriteria.SORT_CLIENT.equals(sortBy)) {
      return "\n ORDER BY cli.client_name, pfu.forest_file_id DESC";
    }
    if (TenureSearchCriteria.SORT_FILE_TYPE.equals(sortBy)) {
      return "\n ORDER BY pfu.file_type_code, pfu.forest_file_id DESC";
    }
    return "\n ORDER BY org_unit_code, pfu.forest_file_id DESC";
  }

  private Query build(TenureSearchCriteria c) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    StringBuilder from = new StringBuilder();
    StringBuilder where = new StringBuilder(" WHERE 1 = 1\n");

    boolean filtersOnClient = notBlank(c.clientName())
        || notBlank(c.clientNumber())
        || notBlank(c.clientLocnCode());
    boolean clientJoinIsInner = filtersOnClient || notBlank(c.fileClientType());

    from.append("""
          FROM the.prov_forest_use pfu
          JOIN the.org_unit org               ON org.org_unit_no = pfu.forest_region
          JOIN the.tenure_file_status_code sts
                 ON sts.tenure_file_status_code = pfu.file_status_st
          LEFT JOIN the.tenure_term tt        ON tt.forest_file_id = pfu.forest_file_id
        """);

    // Naming a client narrows the result set to files that actually have one;
    // otherwise a file with no client must still appear, showing its main
    // ('A') client if it has one. The legacy SQL expresses this by switching
    // the same joins between (+) and inner.
    if (clientJoinIsInner) {
      from.append("""
            JOIN the.forest_file_client fcl     ON fcl.forest_file_id = pfu.forest_file_id
            JOIN the.file_client_type_code fclt
                   ON fclt.file_client_type_code = fcl.forest_file_client_type_code
          """);
    } else {
      from.append("""
            LEFT JOIN the.forest_file_client fcl
                   ON fcl.forest_file_id = pfu.forest_file_id
                  AND fcl.forest_file_client_type_code = 'A'
            LEFT JOIN the.file_client_type_code fclt
                   ON fclt.file_client_type_code = fcl.forest_file_client_type_code
          """);
    }
    from.append(
        "    LEFT JOIN the.forest_client cli     ON cli.client_number = fcl.client_number\n");

    // Admin Org Unit. A region matches everything rolling up to it; a district
    // matches through the file's administering office, with two file-type
    // exceptions that resolve against forest_region instead. All three helper
    // functions are granted to the application role.
    if (notBlank(c.adminOrgUnitNo())) {
      p.addValue("adminOrgUnitNo", c.adminOrgUnitNo().trim());
      where.append("""
             AND ( ( the.sil_get_org_level(:adminOrgUnitNo) = 'R'
                     AND pfu.forest_region IN (SELECT org_unit_no
                                                 FROM the.org_unit
                                                WHERE rollup_region_no = TO_NUMBER(:adminOrgUnitNo)) )
                   OR
                   ( the.sil_get_org_level(:adminOrgUnitNo) = 'D'
                     AND ( ( pfu.file_type_code NOT IN (%s)
                             AND the.fta_get_file_org_code(pfu.forest_file_id, pfu.file_type_code,
                                                           org.org_unit_code)
                                 = the.sil_get_org_unit_code(:adminOrgUnitNo) )
                           OR ( pfu.file_type_code IN (%s)
                                AND pfu.forest_region = TO_NUMBER(:adminOrgUnitNo) )
                           OR ( pfu.file_type_code IN (%s)
                                AND pfu.forest_region = the.sil_get_region_no(
                                        the.sil_get_org_unit_code(:adminOrgUnitNo)) ) ) ) )
          """.formatted(REGION_FILE_TYPES, DISTRICT_FILE_TYPES, REGION_FILE_TYPES));
    }

    if (notBlank(c.forestFileId())) {
      p.addValue("forestFileId", c.forestFileId().trim());
      where.append("   AND pfu.forest_file_id LIKE :forestFileId || '%'\n");
    }

    // File Type takes several codes, comma-separated, as the legacy screen's
    // multi-select submits them.
    List<String> fileTypes = splitCodes(c.fileTypeCode());
    if (!fileTypes.isEmpty()) {
      p.addValue("fileTypeCodes", fileTypes);
      where.append("   AND pfu.file_type_code IN (:fileTypeCodes)\n");
    }

    // Tenure type constrains the file type to one of three code tables.
    String tenureType = notBlank(c.tenureType()) ? c.tenureType().trim().substring(0, 1) : "";
    switch (tenureType) {
      case "T" -> {
        from.append("    JOIN the.timber_file_type_code ftc"
            + " ON ftc.timber_file_type_code = pfu.file_type_code\n");
      }
      case "R" -> {
        from.append("    JOIN the.range_file_type_code ftc"
            + " ON ftc.range_file_type_code = pfu.file_type_code\n");
      }
      case "F" -> {
        from.append("    JOIN the.recreation_file_type_code ftc"
            + " ON ftc.recreation_file_type_code = pfu.file_type_code\n");
      }
      default -> {
        // No tenure-type filter: the file type stands on its own.
      }
    }

    if (notBlank(c.fileStatus())) {
      p.addValue("fileStatus", c.fileStatus().trim());
      where.append("   AND pfu.file_status_st = :fileStatus\n");
    }
    if (notBlank(c.mgmtUnitType())) {
      p.addValue("mgmtUnitType", c.mgmtUnitType().trim());
      where.append("   AND pfu.mgmt_unit_type = :mgmtUnitType\n");
    }
    if (notBlank(c.mgmtUnitId())) {
      p.addValue("mgmtUnitId", c.mgmtUnitId().trim());
      where.append("   AND pfu.mgmt_unit_id = :mgmtUnitId\n");
    }

    // Client predicates apply to fcl directly. The legacy SQL joins a second
    // copy of FOREST_FILE_CLIENT (fcl_filter) on file id, client number,
    // location and type — which makes it the same row as fcl, so the self-join
    // collapses away.
    if (notBlank(c.clientNumber())) {
      p.addValue("clientNumber", c.clientNumber().trim());
      where.append("   AND fcl.client_number = :clientNumber\n");
    }
    if (notBlank(c.clientLocnCode())) {
      p.addValue("clientLocnCode", c.clientLocnCode().trim());
      where.append("   AND fcl.client_locn_code = :clientLocnCode\n");
    }
    if (notBlank(c.clientName())) {
      // Legacy filters V_CLIENT_PUBLIC here; FOREST_CLIENT is already joined
      // for display and is the same client record, so it serves both.
      p.addValue("clientName", c.clientName().trim());
      where.append("   AND UPPER(cli.client_name) LIKE UPPER(:clientName) || '%'\n");
    }

    // Client type: an explicit choice wins. Failing that, a client number
    // search spans every type, and a search with neither is limited to the
    // main and secondary clients.
    if (notBlank(c.fileClientType())) {
      p.addValue("fileClientType", c.fileClientType().trim());
      where.append("   AND fcl.forest_file_client_type_code = :fileClientType\n");
    } else if (filtersOnClient && !notBlank(c.clientNumber()) && !notBlank(c.clientName())) {
      where.append("   AND fcl.forest_file_client_type_code IN ('A','B')\n");
    }

    if (notBlank(c.assocFileId())) {
      p.addValue("assocFileId", c.assocFileId().trim());
      p.addValue("fileSource", blankToNull(c.fileSource()));
      where.append("""
             AND pfu.forest_file_id IN (SELECT DISTINCT a.forest_file_id
                                          FROM the.associated_use a
                                         WHERE a.associated_file_id LIKE :assocFileId || '%'
                                           AND (:fileSource IS NULL
                                                OR a.file_source_code = :fileSource))
          """);
    }

    if (notBlank(c.fileName())) {
      // Project name lives on the recreation project record; requiring it turns
      // the search into a recreation-file search, which is what legacy does.
      p.addValue("fileName", c.fileName().trim());
      from.append("    JOIN the.rec_project rpj            ON rpj.forest_file_id"
          + " = pfu.forest_file_id\n");
      where.append("   AND rpj.rec_project_id = '00'\n");
      where.append("   AND UPPER(rpj.rec_project_name) LIKE UPPER(:fileName) || '%'\n");
    }

    // Salvage and cash sale both read the harvest sale, outer-joined so a file
    // without one is only excluded when the filter actually fails.
    if (notBlank(c.salvageInd()) || notBlank(c.cashSaleInd())) {
      from.append("    LEFT JOIN the.harvest_sale hs       ON hs.forest_file_id"
          + " = pfu.forest_file_id\n");
      if (notBlank(c.salvageInd())) {
        p.addValue("salvageInd", c.salvageInd().trim());
        where.append("   AND NVL(hs.salvage_ind, 'N') = :salvageInd\n");
      }
      if (notBlank(c.cashSaleInd())) {
        // The screen asks a yes/no question about a payment method: cash is
        // 'C', anything else is treated as 'A'.
        p.addValue("paymentMethod", c.cashSaleInd().trim().startsWith("Y") ? "C" : "A");
        where.append("   AND NVL(hs.payment_method_cd, ' ') = :paymentMethod\n");
      }
    }

    // Map notation only applies to map-notation files.
    if (notBlank(c.mapNotationTypeCode()) && fileTypes.contains("M01")) {
      p.addValue("mapNotationTypeCode", c.mapNotationTypeCode().trim());
      from.append("    JOIN the.map_notation mn            ON mn.forest_file_id"
          + " = pfu.forest_file_id\n");
      where.append("   AND mn.map_notation_type_code = :mapNotationTypeCode\n");
    }

    appendDatePredicates(c, fileTypes, p, where);

    return new Query(from + where.toString(), p);
  }

  /**
   * The date filters, which the legacy screen applies only to tenures that have
   * a term worth dating — not to {@code B40}/{@code C01}, and not to recreation
   * files. The recreation test is the database's own, so a new recreation file
   * type does not silently change behaviour here.
   */
  private static void appendDatePredicates(
      TenureSearchCriteria c,
      List<String> fileTypes,
      MapSqlParameterSource p,
      StringBuilder where) {
    boolean anyDate = notBlank(c.issueDateFrom()) || notBlank(c.issueDateTo())
        || notBlank(c.expiryDateFrom()) || notBlank(c.expiryDateTo());
    if (!anyDate || fileTypes.stream().anyMatch(DATELESS_FILE_TYPES::contains)) {
      return;
    }
    String soleFileType = fileTypes.size() == 1 ? fileTypes.get(0) : " ";
    p.addValue("dateFileType", soleFileType);
    String recreationGuard = "the.fta_valid_recreation_file_type(:dateFileType) = 'Y'";

    if (notBlank(c.issueDateFrom())) {
      p.addValue("issueDateFrom", c.issueDateFrom().trim());
      where.append("   AND (" + recreationGuard
          + " OR tt.legal_effective_dt >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.issueDateTo())) {
      p.addValue("issueDateTo", c.issueDateTo().trim());
      where.append("   AND (" + recreationGuard
          + " OR tt.legal_effective_dt <= TO_DATE(:issueDateTo, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateFrom())) {
      p.addValue("expiryDateFrom", c.expiryDateFrom().trim());
      where.append("   AND (" + recreationGuard
          + " OR NVL(tt.current_expiry_dt, tt.initial_expiry_dt)"
          + " >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD'))\n");
    }
    if (notBlank(c.expiryDateTo())) {
      p.addValue("expiryDateTo", c.expiryDateTo().trim());
      where.append("   AND (" + recreationGuard
          + " OR NVL(tt.current_expiry_dt, tt.initial_expiry_dt)"
          + " <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD'))\n");
    }
  }

  /** Splits the screen's comma-separated file types, dropping blanks. */
  private static List<String> splitCodes(String csv) {
    if (!notBlank(csv)) {
      return List.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
