package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import ca.bc.gov.nrs.fta.tenure.dto.TenureSummaryDto;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tenure search over the {@code THE} tables directly.
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
public class TenureSearchTableSource {

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

  /**
   * The file's administering org unit code, in plain SQL.
   *
   * <p>This is {@code THE.FTA_GET_FILE_ORG_CODE} unrolled. Calling the function
   * was the search's whole cost: it runs a lookup per row behind a PL/SQL
   * context switch, so sorting by it (the default sort) evaluated it for every
   * file on record before the first page could be picked, and filtering by
   * district did the same. As joins it is one pass the optimizer can plan.
   *
   * <p>Branch by branch against the function:
   *
   * <ul>
   *   <li>types starting A, P, U or M, a null type, and {@code B10} return the
   *       code of {@code pfu.forest_region} — which is {@code org} here;</li>
   *   <li>E/H, S, C and F look the administering office up in
   *       {@code RANGE_TENURE}, {@code SPEC_USE_PERMIT}, {@code LAND_CLEAR_FILE}
   *       and {@code REC_PROJECT} (project {@code 00}). Each is unique on the
   *       file, so a missing row is the function's {@code NO_DATA_FOUND}, which
   *       falls back to the {@code forest_region} code;</li>
   *   <li>B files read {@code HARVESTING_AUTHORITY}, and the private-mark types
   *       read {@code PRIVATE_MARK_CERTIFICATE} by timber mark. Neither is
   *       unique, and the function's {@code SELECT INTO} only succeeds on exactly
   *       one row — none or several raise, and the handler returns the
   *       {@code forest_region} code. Hence the counts;</li>
   *   <li>any other type runs no lookup and returns null.</li>
   * </ul>
   *
   * <p>A lookup that finds a row whose org unit does not exist yields null, as
   * {@code SIL_GET_ORG_UNIT_CODE} does.
   */
  private static final String ADMIN_ORG_CODE =
      """
      CASE
        WHEN pfu.file_type_code IS NULL
          OR SUBSTR(pfu.file_type_code, 1, 1) IN ('A', 'P', 'U', 'M')
          OR pfu.file_type_code = 'B10'
          THEN org.org_unit_code
        WHEN SUBSTR(pfu.file_type_code, 1, 1) IN ('E', 'H')
          THEN CASE WHEN adm_rt.forest_file_id IS NULL THEN org.org_unit_code ELSE ou_rt.org_unit_code END
        WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'S'
          THEN CASE WHEN adm_sup.forest_file_id IS NULL THEN org.org_unit_code ELSE ou_sup.org_unit_code END
        WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'F'
          THEN CASE WHEN adm_rp.forest_file_id IS NULL THEN org.org_unit_code ELSE ou_rp.org_unit_code END
        WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'C'
          THEN CASE WHEN adm_lcf.forest_file_id IS NULL THEN org.org_unit_code ELSE ou_lcf.org_unit_code END
        WHEN pfu.file_type_code IN ('B08', 'B09', 'B14', 'B15', 'B16')
          THEN CASE WHEN adm_pmc.cnt = 1 THEN ou_pmc.org_unit_code ELSE org.org_unit_code END
        WHEN SUBSTR(pfu.file_type_code, 1, 1) = 'B'
          THEN CASE WHEN adm_hva.cnt = 1 THEN ou_hva.org_unit_code ELSE org.org_unit_code END
        ELSE NULL
      END""";

  /** The joins {@link #ADMIN_ORG_CODE} reads. Each lookup is at most one row per file. */
  private static final String ADMIN_ORG_JOINS =
      """
          LEFT JOIN the.range_tenure adm_rt     ON adm_rt.forest_file_id = pfu.forest_file_id
          LEFT JOIN the.org_unit ou_rt          ON ou_rt.org_unit_no = adm_rt.admin_forest_district_no
          LEFT JOIN the.spec_use_permit adm_sup ON adm_sup.forest_file_id = pfu.forest_file_id
          LEFT JOIN the.org_unit ou_sup         ON ou_sup.org_unit_no = adm_sup.admin_forest_dist
          LEFT JOIN the.rec_project adm_rp      ON adm_rp.forest_file_id = pfu.forest_file_id
                                               AND adm_rp.rec_project_id = '00'
          LEFT JOIN the.org_unit ou_rp          ON ou_rp.org_unit_no = adm_rp.org_unit_no
          LEFT JOIN the.land_clear_file adm_lcf ON adm_lcf.forest_file_id = pfu.forest_file_id
          LEFT JOIN the.org_unit ou_lcf         ON ou_lcf.org_unit_no = adm_lcf.admin_forest_dist
          LEFT JOIN (SELECT forest_file_id, COUNT(*) AS cnt, MIN(forest_district) AS district
                       FROM the.harvesting_authority
                      GROUP BY forest_file_id) adm_hva
                 ON adm_hva.forest_file_id = pfu.forest_file_id
          LEFT JOIN the.org_unit ou_hva         ON ou_hva.org_unit_no = adm_hva.district
          LEFT JOIN (SELECT timber_mark, COUNT(*) AS cnt, MIN(forest_district) AS district
                       FROM the.private_mark_certificate
                      WHERE timber_mark IS NOT NULL
                      GROUP BY timber_mark) adm_pmc
                 ON adm_pmc.timber_mark = pfu.forest_file_id
          LEFT JOIN the.org_unit ou_pmc         ON ou_pmc.org_unit_no = adm_pmc.district
      """;

  // Concatenated rather than String.formatted: the CASE text is safe today, but
  // formatted() reads any '%' as a specifier, which has broken a search here before.
  private static final String SELECT_COLUMNS =
      "SELECT " + ADMIN_ORG_CODE + " AS org_unit_code,\n"
      + """
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

  /**
   * The generated query pieces and the parameters they bind.
   *
   * @param from            the base joins
   * @param where           the predicates
   * @param params          the bind values
   * @param readsAdminOrg   whether {@code where} reads {@link #ADMIN_ORG_CODE},
   *                        and so needs {@link #ADMIN_ORG_JOINS} even to count
   */
  private record Query(
      String from, String where, MapSqlParameterSource params, boolean readsAdminOrg) {}

  /**
   * Common tenure search — the legacy {@code FTA_001_TENR_SRCH} screen.
   *
   * @param criteria the screen's criteria; any field may be null or blank
   * @param page     0-indexed page number
   * @param size     rows per page
   */
  public PagedResponse<TenureSummaryDto> search(
      TenureSearchCriteria criteria, int page, int size) {
    Query q = build(criteria);

    // Count first, against the same clause. A page past the end then costs only
    // the count rather than a pointless row fetch. The admin-org lookups are
    // left out unless a predicate reads them: the count never displays or
    // sorts by the column.
    String countFrom = q.readsAdminOrg() ? q.from() + ADMIN_ORG_JOINS : q.from();
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*)\n" + countFrom + q.where(), q.params(), Long.class);
    long totalElements = total == null ? 0L : total;
    if (totalElements == 0) {
      return PagedResponse.ofPage(List.of(), page, size, 0);
    }

    // Sorted outside the join. Several of the lookup tables carry an
    // org_unit_code column of their own, and Oracle's rewrite of OFFSET/FETCH
    // can resolve an ORDER BY name against the joined tables rather than the
    // select list — ORA-00918. Outside, each name means one column.
    String sql = "SELECT * FROM (\n" + SELECT_COLUMNS + q.from() + ADMIN_ORG_JOINS + q.where()
        + ")" + orderBy(criteria.sortBy())
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
      return "\n ORDER BY client_name, forest_file_id DESC";
    }
    if (TenureSearchCriteria.SORT_FILE_TYPE.equals(sortBy)) {
      return "\n ORDER BY file_type_code, forest_file_id DESC";
    }
    return "\n ORDER BY org_unit_code, forest_file_id DESC";
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

    boolean readsAdminOrg = false;
    if (notBlank(c.adminOrgUnitNo())) {
      readsAdminOrg = appendAdminOrgPredicate(c.adminOrgUnitNo().trim(), p, where);
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

    return new Query(from.toString(), where.toString(), p, readsAdminOrg);
  }

  /** The one org unit row the Admin Org Unit filter needs, read once. */
  private record OrgUnit(String levelCode, String unitCode, long rollupRegionNo) {}

  /**
   * Admin Org Unit. A region matches everything rolling up to it; a district
   * matches through the file's administering office, with two file-type
   * exceptions that resolve against {@code forest_region} instead.
   *
   * <p>Legacy resolves the org unit's level, code and region once, in PL/SQL,
   * and concatenates them into the statement. An earlier port called
   * {@code SIL_GET_ORG_LEVEL}, {@code SIL_GET_ORG_UNIT_CODE} and
   * {@code SIL_GET_REGION_NO} inside the predicate instead, which evaluated all
   * three per row. They are read once here and bound as constants.
   *
   * <p>The district test is then two steps. First a candidate set, which is
   * cheap and indexed: a file can only be administered by district N if its
   * {@code forest_region} is N (or its region, for region-administered types),
   * or if one of the tables {@link #ADMIN_ORG_CODE} reads names N. Every
   * lookup column is foreign-key indexed. Then the exact rule, evaluated on
   * those candidates alone rather than on every file.
   *
   * @return whether the predicate reads {@link #ADMIN_ORG_CODE}
   */
  private boolean appendAdminOrgPredicate(
      String adminOrgUnitNo, MapSqlParameterSource p, StringBuilder where) {
    OrgUnit org = findOrgUnit(adminOrgUnitNo);
    if (org == null) {
      // Legacy's level lookup returns null for an unknown unit, and neither the
      // region nor the district branch matches: no rows.
      where.append("   AND 1 = 0\n");
      return false;
    }
    p.addValue("adminOrgUnitNo", Long.valueOf(adminOrgUnitNo));
    p.addValue("adminRegionNo", org.rollupRegionNo());
    p.addValue("adminOrgUnitCode", org.unitCode());

    if ("R".equals(org.levelCode())) {
      where.append("""
             AND pfu.forest_region IN (SELECT org_unit_no
                                         FROM the.org_unit
                                        WHERE rollup_region_no = :adminOrgUnitNo)
          """);
      return false;
    }
    if (!"D".equals(org.levelCode())) {
      where.append("   AND 1 = 0\n");
      return false;
    }

    where.append("""
           AND pfu.forest_file_id IN (
                 SELECT forest_file_id FROM the.prov_forest_use
                  WHERE forest_region = :adminOrgUnitNo
                 UNION ALL
                 SELECT forest_file_id FROM the.prov_forest_use
                  WHERE forest_region = :adminRegionNo AND file_type_code IN (""")
        .append(REGION_FILE_TYPES)
        .append("""
        )
                 UNION ALL
                 SELECT forest_file_id FROM the.range_tenure
                  WHERE admin_forest_district_no = :adminOrgUnitNo
                 UNION ALL
                 SELECT forest_file_id FROM the.spec_use_permit
                  WHERE admin_forest_dist = :adminOrgUnitNo
                 UNION ALL
                 SELECT forest_file_id FROM the.rec_project
                  WHERE org_unit_no = :adminOrgUnitNo AND rec_project_id = '00'
                 UNION ALL
                 SELECT forest_file_id FROM the.land_clear_file
                  WHERE admin_forest_dist = :adminOrgUnitNo
                 UNION ALL
                 SELECT forest_file_id FROM the.harvesting_authority
                  WHERE forest_district = :adminOrgUnitNo
                 UNION ALL
                 SELECT timber_mark FROM the.private_mark_certificate
                  WHERE forest_district = :adminOrgUnitNo AND timber_mark IS NOT NULL)
          """)
        .append("   AND ( ( pfu.file_type_code NOT IN (").append(REGION_FILE_TYPES).append(")\n")
        .append("           AND ").append(ADMIN_ORG_CODE).append(" = :adminOrgUnitCode )\n")
        .append("         OR ( pfu.file_type_code IN (").append(DISTRICT_FILE_TYPES).append(")\n")
        .append("              AND pfu.forest_region = :adminOrgUnitNo )\n")
        .append("         OR ( pfu.file_type_code IN (").append(REGION_FILE_TYPES).append(")\n")
        .append("              AND pfu.forest_region = :adminRegionNo ) )\n");
    return true;
  }

  private OrgUnit findOrgUnit(String adminOrgUnitNo) {
    long orgUnitNo;
    try {
      orgUnitNo = Long.parseLong(adminOrgUnitNo);
    } catch (NumberFormatException e) {
      return null;
    }
    List<OrgUnit> found = jdbc.query(
        """
        SELECT org_level_code, org_unit_code, rollup_region_no
          FROM the.org_unit
         WHERE org_unit_no = :orgUnitNo
        """,
        new MapSqlParameterSource("orgUnitNo", orgUnitNo),
        (rs, n) -> new OrgUnit(
            rs.getString("org_level_code"),
            rs.getString("org_unit_code"),
            rs.getLong("rollup_region_no")));
    return found.isEmpty() ? null : found.get(0);
  }

  /**
   * The date filters, which the legacy screen applies only to tenures that have
   * a term worth dating — not to {@code B40}/{@code C01}, and not to recreation
   * files.
   *
   * <p>Legacy tests the recreation case with
   * {@code FTA_VALID_RECREATION_FILE_TYPE}, which is only ever asked about the
   * single file type the user picked (or a blank). Its answer is the same for
   * every row, so it is looked up once here: a recreation file type drops the
   * date filters entirely, anything else applies them as plain predicates. The
   * lookup reads the code table rather than a hard-coded list, so a new
   * recreation file type does not silently change behaviour.
   */
  private void appendDatePredicates(
      TenureSearchCriteria c,
      List<String> fileTypes,
      MapSqlParameterSource p,
      StringBuilder where) {
    boolean anyDate = notBlank(c.issueDateFrom()) || notBlank(c.issueDateTo())
        || notBlank(c.expiryDateFrom()) || notBlank(c.expiryDateTo());
    if (!anyDate || fileTypes.stream().anyMatch(DATELESS_FILE_TYPES::contains)) {
      return;
    }
    if (fileTypes.size() == 1 && isRecreationFileType(fileTypes.get(0))) {
      return;
    }

    if (notBlank(c.issueDateFrom())) {
      p.addValue("issueDateFrom", c.issueDateFrom().trim());
      where.append("   AND tt.legal_effective_dt >= TO_DATE(:issueDateFrom, 'YYYY-MM-DD')\n");
    }
    if (notBlank(c.issueDateTo())) {
      p.addValue("issueDateTo", c.issueDateTo().trim());
      where.append("   AND tt.legal_effective_dt <= TO_DATE(:issueDateTo, 'YYYY-MM-DD')\n");
    }
    if (notBlank(c.expiryDateFrom())) {
      p.addValue("expiryDateFrom", c.expiryDateFrom().trim());
      where.append("   AND NVL(tt.current_expiry_dt, tt.initial_expiry_dt)"
          + " >= TO_DATE(:expiryDateFrom, 'YYYY-MM-DD')\n");
    }
    if (notBlank(c.expiryDateTo())) {
      p.addValue("expiryDateTo", c.expiryDateTo().trim());
      where.append("   AND NVL(tt.current_expiry_dt, tt.initial_expiry_dt)"
          + " <= TO_DATE(:expiryDateTo, 'YYYY-MM-DD')\n");
    }
  }

  /** {@code THE.FTA_VALID_RECREATION_FILE_TYPE}: whether the code is a recreation file type. */
  private boolean isRecreationFileType(String fileTypeCode) {
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM the.recreation_file_type_code WHERE recreation_file_type_code = :code",
        new MapSqlParameterSource("code", fileTypeCode),
        Integer.class);
    return count != null && count > 0;
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
