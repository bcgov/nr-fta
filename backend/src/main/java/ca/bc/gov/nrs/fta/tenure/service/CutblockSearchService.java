package ca.bc.gov.nrs.fta.tenure.service;

import ca.bc.gov.nrs.fta.shared.csv.CsvStreamingJdbc;
import ca.bc.gov.nrs.fta.shared.csv.CsvWriter;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import ca.bc.gov.nrs.fta.shared.sql.ClientNameSql;
import ca.bc.gov.nrs.fta.tenure.dto.CutblockSearchDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Cut-block search business logic.
 *
 * <p>Ports the legacy Oracle package {@code THE.FTA_003_CUTBLK_SRCH} (the cut
 * block search) to a native query against the shared {@code THE} schema. The
 * legacy body assembles the SQL dynamically from the {@code get} procedure's
 * parameters; here the same joins/columns and per-filter predicates are
 * expressed as a static native query, with each filter applied only when its
 * bind value is supplied (NVL-style), mirroring the package's behaviour. Column
 * selection matches the package's {@code rec_cut_block_results} record.
 *
 * <p>The SQL runs against the BC Gov shared Oracle ({@code THE}) via the
 * configured {@code DataSource}; there is no local database, so it is exercised
 * only in a deployed environment.
 */
@Service
public class CutblockSearchService {

  private final NamedParameterJdbcTemplate jdbc;

  private final CsvStreamingJdbc streamingJdbc;

  public CutblockSearchService(
      NamedParameterJdbcTemplate jdbc, CsvStreamingJdbc streamingJdbc) {
    this.jdbc = jdbc;
    this.streamingJdbc = streamingJdbc;
  }

  /** Sort on the administrative district — the legacy default. */
  public static final String SORT_DISTRICT = "district";

  /** Sort on client name. */
  public static final String SORT_CLIENT = "client";

  /** Sort on file id. */
  public static final String SORT_FILE_ID = "fileId";

  /**
   * One column of the cut block's licensee, as {@code SIL_GET_CP_LICENSEE}
   * chooses it: the permit's own licensee ({@code L}, not tied to a block) ahead
   * of the file's main client ({@code A}, permit id {@code ' '}), first row
   * wins. Legacy leaves the order among several {@code L} rows to chance; the
   * link's key breaks the tie here so both columns always describe one client.
   *
   * @param column the {@code FOR_CLIENT_LINK} column to return
   */
  private static String licensee(String column) {
    return "(SELECT MAX(fcl." + column + ") KEEP (DENSE_RANK FIRST"
        + " ORDER BY fcl.file_client_type DESC, fcl.for_client_link_skey)"
        + " FROM the.for_client_link fcl"
        + " WHERE fcl.forest_file_id = cb.forest_file_id"
        + " AND ((fcl.file_client_type = 'L' AND fcl.cutting_permit_id = cb.cutting_permit_id"
        + " AND fcl.cut_block_id IS NULL)"
        + " OR (fcl.cutting_permit_id = ' ' AND fcl.file_client_type = 'A')))";
  }

  /**
   * The inner, distinct row. The licensee is carried as its raw number and
   * location, and {@link #PAGE_COLUMNS} formats them from one join to
   * {@code FOREST_CLIENT} — so the licensee is looked up once per row rather
   * than once per column that shows it.
   * Distinctness is unaffected: the formatted values follow from the raw ones.
   */
  private static final String SELECT_COLUMNS =
      """
      SELECT DISTINCT cb.cb_skey                                                        AS cb_skey,
             ou.org_unit_code                                                           AS org_unit_code,
      """
          + "       " + licensee("client_number") + " AS lic_client_number,\n"
          + "       " + licensee("client_locn_code") + " AS lic_client_locn_code,\n"
          + """
             pfu.forest_file_id                                                         AS forest_file_id,
             cb.cutting_permit_id                                                       AS cutting_permit_id,
             cb.timber_mark                                                             AS timber_mark,
             cb.cut_block_id                                                            AS cut_block_id,
             cb.block_status_st                                                         AS block_status_st,
             cboa.disturbance_start_date                                                AS disturbance_start_date,
             cboa.disturbance_end_date                                                  AS disturbance_end_date
      """;

  /**
   * The page's columns, over the distinct rows. The licensee number reads
   * {@code "ACRONYM  LC"} — acronym (or number), two spaces, location — and is
   * just the two spaces when there is no licensee, as legacy's concatenation of
   * nulls gives ({@code SIL_GET_CP_LICENSEE_NUMBER}). The name is the client's
   * display name ({@code SIL_GET_CP_LICENSEE}).
   */
  private static final String PAGE_COLUMNS =
      "SELECT b.cb_skey, b.org_unit_code,\n"
          + "       " + ClientNameSql.acronymOrNumber("lic_fc", "b.lic_client_number")
          + " || '  ' || b.lic_client_locn_code AS client_number,\n"
          + "       " + ClientNameSql.displayName("lic_fc") + " AS client_name,\n"
          + "       b.forest_file_id, b.cutting_permit_id, b.timber_mark, b.cut_block_id,\n"
          + "       b.block_status_st, b.disturbance_start_date, b.disturbance_end_date\n";

  /**
   * The tables and predicates, shared verbatim by the page query and the count,
   * so a count can never filter differently from the rows it is counting.
   *
   * <p>Note the count wraps a {@code SELECT DISTINCT}, so it is taken over the
   * distinct rows rather than the join's raw cardinality — see the count query
   * in {@link #search}.
   */
  private static final String FROM_WHERE =
      """
        FROM the.prov_forest_use pfu,
             the.cut_block cb,
             the.cut_block_open_admin cboa,
             the.harvesting_authority hva,
             the.org_unit ou
       WHERE pfu.forest_file_id = hva.forest_file_id
         AND hva.hva_skey = cb.hva_skey
         AND ou.org_unit_no = hva.forest_district
         AND cb.cb_skey = cboa.cb_skey
         AND (:forestFileId IS NULL OR cb.forest_file_id LIKE :forestFileId || '%')
         AND (:cutBlockId IS NULL OR cb.cut_block_id = :cutBlockId)
         AND (:cuttingPermitId IS NULL OR cb.cutting_permit_id = :cuttingPermitId)
         AND (:timberMark IS NULL OR cb.timber_mark = :timberMark)
         AND (:blockStatusSt IS NULL OR cb.block_status_st = :blockStatusSt)
         AND (:districtAdminZone IS NULL OR hva.district_admn_zone = :districtAdminZone)
         AND (:orgUnitNo IS NULL OR ou.org_unit_no = :orgUnitNo)
         AND ((:managedByFile IS NULL OR :managedByCp IS NOT NULL)
              OR hva.forest_file_id = UPPER(:managedByFile))
         AND (:managedByCp IS NULL OR hva.cutting_permit_id = UPPER(:managedByCp))
         AND ((:harvestStartDateFrom IS NULL AND :harvestStartDateTo IS NULL)
              OR cboa.disturbance_start_date
                   BETWEEN TO_DATE(NVL(:harvestStartDateFrom, '0001-01-01'), 'YYYY-MM-DD')
                       AND TO_DATE(NVL(:harvestStartDateTo, '9999-12-31'), 'YYYY-MM-DD'))
         AND (:clientNumber IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_number = :clientNumber))
         AND (:clientLocnCode IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_locn_code = :clientLocnCode))
         AND (:clientName IS NULL OR cb.forest_file_id IN (
                SELECT ffc.forest_file_id
                  FROM the.forest_file_client ffc
                 WHERE ffc.forest_file_client_type_code IN ('A', 'L')
                   AND ffc.client_number IN (
                        SELECT fc.client_number
                          FROM the.forest_client fc
                         WHERE fc.client_name LIKE UPPER(:clientName) || '%')))
      """;

  /**
   * The sort options the legacy screen offers, as its three mutually exclusive
   * indicators ({@code p_r_district} / {@code p_r_client_name} /
   * {@code p_r_file_id}). File id, permit and block always trail as tiebreakers
   * so the order is deterministic under OFFSET.
   */
  private static String orderBy(String sortBy) {
    if (SORT_CLIENT.equals(sortBy)) {
      return "\n ORDER BY client_name, forest_file_id, cutting_permit_id, cut_block_id";
    }
    if (SORT_FILE_ID.equals(sortBy)) {
      return "\n ORDER BY forest_file_id, cutting_permit_id, cut_block_id";
    }
    return "\n ORDER BY org_unit_code, forest_file_id, cutting_permit_id, cut_block_id";
  }

  /**
   * The query behind the results table, everything but the paging: the formatted
   * columns over the distinct rows, sorted.
   *
   * <p>The {@code ORDER BY} references the select aliases, so it is applied
   * outside the DISTINCT rather than inside it — hence the outer
   * {@code SELECT *}.
   *
   * <p>Shared by {@link #search}, which appends {@code OFFSET}/{@code FETCH}, and
   * {@link #exportCsv}, which appends nothing. The two must not diverge: the
   * formatted client columns are built here, so an export running any other
   * shape would show different client values than the table.
   */
  private static String pageQuery(String sortBy) {
    return "SELECT * FROM (" + PAGE_COLUMNS
        + "  FROM (" + SELECT_COLUMNS + FROM_WHERE + ") b\n"
        + "  LEFT JOIN the.forest_client lic_fc ON lic_fc.client_number = b.lic_client_number)"
        + orderBy(sortBy);
  }

  private static final RowMapper<CutblockSearchDto> ROW_MAPPER =
      (rs, rowNum) -> new CutblockSearchDto(
          rs.getObject("cb_skey", Long.class),
          rs.getString("org_unit_code"),
          rs.getString("client_number"),
          rs.getString("client_name"),
          rs.getString("forest_file_id"),
          rs.getString("cutting_permit_id"),
          rs.getString("timber_mark"),
          rs.getString("cut_block_id"),
          rs.getString("block_status_st"),
          rs.getObject("disturbance_start_date", LocalDate.class),
          rs.getObject("disturbance_end_date", LocalDate.class));

  /**
   * Cut-block search — mirrors {@code FTA_003_CUTBLK_SRCH.get}.
   *
   * @param forestFileId partial forest-file id (prefix match), or null
   * @param cuttingPermitId exact cutting-permit id, or null
   * @param timberMark exact timber mark, or null
   * @param cutBlockId exact cut-block id, or null
   * @param blockStatusSt exact block status code, or null
   * @param orgUnitNo administrative org-unit number, or null
   * @param clientNumber exact client number, or null
   * @param clientLocnCode exact client location code, or null
   * @param clientName client name (prefix match), or null
   * @param managedByFile managing forest-file id, or null
   * @param managedByCp managing cutting-permit id, or null
   * @param harvestStartDateFrom disturbance-start lower bound (YYYY-MM-DD), or null
   * @param harvestStartDateTo disturbance-start upper bound (YYYY-MM-DD), or null
   * @param districtAdminZone district admin zone, or null
   * @param sortBy {@code district}, {@code client} or {@code fileId}
   * @param page 0-indexed page number
   * @param size rows per page
   */
  public PagedResponse<CutblockSearchDto> search(
      String forestFileId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String blockStatusSt,
      String orgUnitNo,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String managedByFile,
      String managedByCp,
      String harvestStartDateFrom,
      String harvestStartDateTo,
      String districtAdminZone,
      String sortBy,
      int page,
      int size) {
    MapSqlParameterSource params = criteria(
        forestFileId, cuttingPermitId, timberMark, cutBlockId, blockStatusSt, orgUnitNo,
        clientNumber, clientLocnCode, clientName, managedByFile, managedByCp,
        harvestStartDateFrom, harvestStartDateTo, districtAdminZone);

    // COUNT over the distinct rows, not the join's raw cardinality: the page
    // query is SELECT DISTINCT, so counting the join directly would overstate
    // the total wherever a block joins more than one row.
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM (" + SELECT_COLUMNS + FROM_WHERE + ")", params, Long.class);
    long totalElements = total == null ? 0L : total;

    MapSqlParameterSource pageParams = new MapSqlParameterSource()
        .addValues(params.getValues())
        .addValue("offset", (long) page * size)
        .addValue("size", size);

    List<CutblockSearchDto> rows = jdbc.query(
        pageQuery(sortBy) + "\n OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
        pageParams,
        ROW_MAPPER);

    return PagedResponse.ofPage(rows, page, size, totalElements);
  }

  /**
   * Streams every matching cut block to a CSV — the same criteria and order as
   * {@link #search}, with no paging.
   *
   * <p>Runs {@link #pageQuery}, not the count's shape: the two client columns are
   * formatted by the page query's outer select, so only that shape yields the
   * values the table shows.
   *
   * <p>Columns track the Cut Block Search page's {@code HEADERS}; keep the two in
   * step. The dates are read as {@link LocalDate} so the writer renders them ISO
   * — the screen's "Apr 10, 2021" is for reading, whereas a spreadsheet can sort
   * and filter ISO.
   */
  public void exportCsv(
      String forestFileId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String blockStatusSt,
      String orgUnitNo,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String managedByFile,
      String managedByCp,
      String harvestStartDateFrom,
      String harvestStartDateTo,
      String districtAdminZone,
      String sortBy,
      CsvWriter csv) {

    csv.writeRow(
        "District",
        "Client name",
        "Client number",
        "File ID",
        "Cutting permit",
        "Timber mark",
        "Cut block",
        "Block status",
        "Start date",
        "End date");

    streamingJdbc.jdbc().query(
        pageQuery(sortBy),
        criteria(
            forestFileId, cuttingPermitId, timberMark, cutBlockId, blockStatusSt, orgUnitNo,
            clientNumber, clientLocnCode, clientName, managedByFile, managedByCp,
            harvestStartDateFrom, harvestStartDateTo, districtAdminZone),
        // Cast required: a void lambda body matches both the RowCallbackHandler
        // and ResultSetExtractor overloads, so the compiler cannot choose.
        (RowCallbackHandler) rs -> csv.writeRow(
            rs.getString("org_unit_code"),
            rs.getString("client_name"),
            rs.getString("client_number"),
            rs.getString("forest_file_id"),
            rs.getString("cutting_permit_id"),
            rs.getString("timber_mark"),
            rs.getString("cut_block_id"),
            rs.getString("block_status_st"),
            rs.getObject("disturbance_start_date", LocalDate.class),
            rs.getObject("disturbance_end_date", LocalDate.class)));
  }

  /** The bind values, shared by the count, the page and the export. */
  private static MapSqlParameterSource criteria(
      String forestFileId,
      String cuttingPermitId,
      String timberMark,
      String cutBlockId,
      String blockStatusSt,
      String orgUnitNo,
      String clientNumber,
      String clientLocnCode,
      String clientName,
      String managedByFile,
      String managedByCp,
      String harvestStartDateFrom,
      String harvestStartDateTo,
      String districtAdminZone) {
    return new MapSqlParameterSource()
        .addValue("forestFileId", blankToNull(forestFileId))
        .addValue("cuttingPermitId", blankToNull(cuttingPermitId))
        .addValue("timberMark", blankToNull(timberMark))
        .addValue("cutBlockId", blankToNull(cutBlockId))
        .addValue("blockStatusSt", blankToNull(blockStatusSt))
        .addValue("orgUnitNo", blankToNull(orgUnitNo))
        .addValue("clientNumber", blankToNull(clientNumber))
        .addValue("clientLocnCode", blankToNull(clientLocnCode))
        .addValue("clientName", blankToNull(clientName))
        .addValue("managedByFile", blankToNull(managedByFile))
        .addValue("managedByCp", blankToNull(managedByCp))
        .addValue("harvestStartDateFrom", blankToNull(harvestStartDateFrom))
        .addValue("harvestStartDateTo", blankToNull(harvestStartDateTo))
        .addValue("districtAdminZone", blankToNull(districtAdminZone));
  }

  private static String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
