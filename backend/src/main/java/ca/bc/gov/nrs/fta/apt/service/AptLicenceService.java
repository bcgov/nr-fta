package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptLicenceDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceSearchParams;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceUpdateRequest;
import ca.bc.gov.nrs.fta.shared.dto.PagedResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Licences: APT2's LicenceService, in SQL against the tables the {@code THE.APT_*} routines
 * used, which FTA no longer calls.
 *
 * <ul>
 *   <li>{@link #search} / {@link #get} — {@code APT_LICENCES_GET}. The forest file id is
 *       matched with {@code UPPER(..) LIKE UPPER(in)}, so it is exact unless it holds a
 *       wildcard; {@code APT_MGMT_UNIT_NAMES_V} is written out over {@code CODE_LIST_TABLE}.
 *   <li>{@link #update} — {@code APT_LICENCE_INS_UPD}: inserts APT's row for the licence or
 *       updates it.
 * </ul>
 *
 * <p>{@code APT_LICENCES}' trigger {@code LIC_BR_IUD_TRG} sets {@code ADD_TIMESTAMP} on insert
 * and {@code UPDATE_TIMESTAMP} on update (and a missing user id to the database user), and
 * repeats the tenure and FOA checks. The procedure set {@code ADD_USERID} on insert and
 * {@code UPDATE_USERID} on update itself, and so does this. No revision count.
 */
@Service
public class AptLicenceService {

  /** {@code APT_LICENCES_GET}'s cursor. */
  private static final String SEARCH_SQL =
      """
      WITH mgmt_unit_names AS (
        SELECT DECODE(clt.column_name, 'TFL_NUMBER', 'T', 'TSA_NUMBER', 'U', 'WL_NUMBER', 'F')
                 AS apt_mgmt_unit_st,
               clt.code_argument AS mgmt_unit_number,
               clt.expanded_result AS mgmt_unit_name
          FROM the.code_list_table clt
         WHERE clt.column_name IN ('TFL_NUMBER', 'TSA_NUMBER', 'WL_NUMBER')
        UNION
        SELECT DECODE(clt2.column_name, 'MGMT_UNIT_TYPE', 'Z') AS apt_mgmt_unit_st,
               NULL AS mgmt_unit_number,
               clt2.expanded_result AS mgmt_unit_name
          FROM the.code_list_table clt2
         WHERE clt2.column_name = 'MGMT_UNIT_TYPE'
           AND clt2.code_argument = 'Z'
      )
      SELECT ou.org_unit_name,
             pfu.mgmt_unit_type AS apt_mgmt_unit_st,
             pfu.mgmt_unit_id AS mgmt_unit_number,
             amunv.mgmt_unit_name,
             pfu.file_status_st,
             clt.expanded_result AS file_status,
             pfu.file_status_date,
             pfu.forest_file_id,
             tt.legal_effective_dt,
             tt.initial_expiry_dt,
             tt.current_expiry_dt,
             tt.tenure_term,
             fc.client_number,
             fc.client_name,
             fc1.client_name AS control_company,
             al.apt_foa_st,
             al.replacement_date,
             al.replacement_term,
             al.comments,
             al.add_timestamp,
             al.add_userid,
             al.update_timestamp,
             al.update_userid
        FROM the.prov_forest_use pfu
        JOIN the.for_client_link fcl ON pfu.forest_file_id = fcl.forest_file_id
        JOIN the.forest_client fc ON fc.client_number = fcl.client_number
        LEFT JOIN the.apt_related_clients arc ON fcl.client_number = arc.client_number
        LEFT JOIN the.forest_client fc1 ON arc.client_number_parented_by = fc1.client_number
        JOIN the.code_subset_tbl cst ON pfu.file_status_st = cst.code_argument
        JOIN the.code_list_table clt
          ON clt.column_name = cst.column_name
         AND clt.code_argument = cst.code_argument
        LEFT JOIN mgmt_unit_names amunv
          ON pfu.mgmt_unit_type = amunv.apt_mgmt_unit_st
         AND pfu.mgmt_unit_id = amunv.mgmt_unit_number
        JOIN the.org_unit ou ON pfu.forest_region = ou.org_unit_no
        JOIN the.tenure_term tt ON pfu.forest_file_id = tt.forest_file_id
        LEFT JOIN the.apt_licences al ON pfu.forest_file_id = al.forest_file_id
       WHERE fcl.file_client_type IN ('A', 'B')
         AND cst.code_subset_name = 'FILE_STATUS_ST'
         AND (UPPER(ou.org_unit_name) LIKE '%' || UPPER(:orgUnitName) || '%'
              OR :orgUnitName IS NULL)
         AND (UPPER(pfu.forest_file_id) LIKE UPPER(:forestFileId) OR :forestFileId IS NULL)
         AND (UPPER(fc.client_name) LIKE '%' || UPPER(:clientName) || '%'
              OR :clientName IS NULL)
         AND (UPPER(pfu.mgmt_unit_type) = UPPER(:muType) OR :muType IS NULL)
         AND (pfu.mgmt_unit_id = :muNumber OR :muNumber IS NULL)
         AND (UPPER(amunv.mgmt_unit_name) LIKE '%' || UPPER(:muName) || '%'
              OR :muName IS NULL)
      """;

  private static final String EXISTS_SQL =
      "SELECT COUNT(*) FROM the.apt_licences WHERE forest_file_id = :forestFileId";

  /** {@code APT_LICENCE_INS_UPD}'s {@code check_lic_tt_fk}. */
  private static final String TENURE_EXISTS_SQL =
      "SELECT COUNT(*) FROM the.tenure_term tt WHERE tt.forest_file_id = TRIM(:forestFileId)";

  /** {@code APT_LICENCE_INS_UPD}'s {@code check_lic_clt_fk}. */
  private static final String FOA_EXISTS_SQL =
      """
      SELECT COUNT(*)
        FROM the.code_subset_tbl cs_lic
        JOIN the.code_list_table clt_lic
          ON clt_lic.column_name = cs_lic.column_name
         AND clt_lic.code_argument = cs_lic.code_argument
       WHERE cs_lic.code_argument = TRIM(:foaType)
         AND cs_lic.code_subset_name = 'APT_FOA_ST'
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.apt_licences (
        forest_file_id, apt_foa_st, replacement_date, replacement_term, comments, add_userid
      ) VALUES (
        :forestFileId, :foaType, :replacementDate, :replacementTerm, :comments, :user
      )
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE the.apt_licences
         SET apt_foa_st       = :foaType,
             replacement_date = :replacementDate,
             replacement_term = :replacementTerm,
             comments         = :comments,
             update_userid    = :user
       WHERE forest_file_id = :forestFileId
      """;

  /** The procedure's messages, raised with code {@code 'APT '}, so a 400. */
  static final String NO_LICENCE = "The Licence does not exist.";
  static final String NO_FOA = "The Licence Form of Agreement does not exist.";

  private final NamedParameterJdbcTemplate jdbc;

  public AptLicenceService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * One page of licences matching the criteria. The query has no paging, so the whole result
   * is fetched and sliced, as APT2 did.
   *
   * @throws ResponseStatusException 400 without a forest file id, which APT2 required, or
   *     with a negative page or a size below 1, which APT2 refuses
   */
  public PagedResponse<AptLicenceDto> search(AptLicenceSearchParams p, int page, int size) {
    return search(p, page, size, null);
  }

  /**
   * {@link #search(AptLicenceSearchParams, int, int)} in a results-table column sort,
   * {@code <column>,asc|desc} as {@link ca.bc.gov.nrs.fta.shared.sql.ColumnSort} takes it;
   * null, or a column the table doesn't sort on, keeps the query's own order.
   */
  public PagedResponse<AptLicenceDto> search(
      AptLicenceSearchParams p, int page, int size, String sort) {
    if (page < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Page index must be zero or greater.");
    }
    if (size < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size must be at least 1.");
    }
    if (p.forestFileId() == null || p.forestFileId().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forest File ID is required.");
    }
    return PagedResponse.ofFullList(sorted(all(p), sort), page, size);
  }

  /**
   * The results table's sortable columns. The whole result is in memory (the query is
   * sliced here, not paged in SQL), so the sort is too.
   */
  private static final Map<String, Function<AptLicenceDto, Comparable<?>>> SORT_COLUMNS =
      Map.of(
          "forestFileId", AptLicenceDto::forestFileId,
          "clientName", AptLicenceDto::clientName,
          "mgmtUnitName", AptLicenceDto::mgmtUnitName,
          "orgUnitName", AptLicenceDto::orgUnitName,
          "fileStatus", AptLicenceDto::fileStatus,
          "fileStatusDate", AptLicenceDto::fileStatusDate);

  /**
   * The licences in a column sort: nulls last either way, text case-insensitive, the forest
   * file id breaking ties (each licence appears once, so the order is total).
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  static List<AptLicenceDto> sorted(List<AptLicenceDto> licences, String sort) {
    if (sort == null || sort.isBlank()) {
      return licences;
    }
    String[] parts = sort.trim().split("\\s*,\\s*");
    Function<AptLicenceDto, Comparable<?>> column = SORT_COLUMNS.get(parts[0]);
    String dir = parts.length == 2 ? parts[1].toLowerCase(Locale.ROOT) : "asc";
    if (column == null || parts.length > 2 || !(dir.equals("asc") || dir.equals("desc"))) {
      return licences;
    }
    Comparator<Comparable> natural = (a, b) -> a instanceof String x && b instanceof String y
        ? String.CASE_INSENSITIVE_ORDER.compare(x, y)
        : a.compareTo(b);
    Comparator<Comparable> direction = dir.equals("desc") ? natural.reversed() : natural;
    Comparator<AptLicenceDto> byColumn = Comparator.comparing(
        l -> (Comparable) column.apply(l), Comparator.nullsLast(direction));
    return licences.stream()
        .sorted(byColumn.thenComparing(AptLicenceDto::forestFileId,
            Comparator.nullsLast(Comparator.<String>naturalOrder())))
        .toList();
  }

  /**
   * One licence by its forest file id, matched exactly (case aside).
   *
   * @throws ResponseStatusException 404 when there is none
   */
  public AptLicenceDto get(String forestFileId) {
    String id = forestFileId == null ? "" : forestFileId.trim();
    return all(new AptLicenceSearchParams(id, null, null, null, null, null)).stream()
        .filter(l -> id.equalsIgnoreCase(l.forestFileId()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Licence " + id + " was not found."));
  }

  /**
   * Sets APT's FOA type, replacement and comments for a licence, inserting APT's row when it
   * has none, and returns it as stored.
   *
   * @throws ResponseStatusException 404 for an unknown licence; 400 for a bad request, a
   *                                 licence with no tenure term or an FOA type not in the
   *                                 {@code APT_FOA_ST} code subset
   */
  @Transactional
  public AptLicenceDto update(String forestFileId, AptLicenceUpdateRequest r, String user) {
    String problem = AptLicenceRules.problem(r);
    if (problem != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
    }
    String id = get(forestFileId).forestFileId();
    String foa = r.foaType().trim().toUpperCase(Locale.ROOT);
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", id, Types.VARCHAR)
        .addValue("foaType", foa, Types.VARCHAR)
        .addValue("replacementDate", r.replacementDate(), Types.DATE)
        .addValue("replacementTerm",
            r.replacementTerm() == null ? null : r.replacementTerm().longValue(), Types.NUMERIC)
        .addValue("comments", blankToNull(r.comments()), Types.VARCHAR)
        .addValue("user", blankToNull(user), Types.VARCHAR);

    boolean found = count(EXISTS_SQL, p) > 0;
    if (count(TENURE_EXISTS_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_LICENCE);
    }
    if (count(FOA_EXISTS_SQL, p) == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NO_FOA);
    }
    try {
      jdbc.update(found ? UPDATE_SQL : INSERT_SQL, p);
    } catch (DataAccessException e) {
      throw AptCommitmentRules.refusal(e);
    }
    return get(id);
  }

  private int count(String sql, MapSqlParameterSource p) {
    Integer n = jdbc.queryForObject(sql, p, Integer.class);
    return n == null ? 0 : n;
  }

  private List<AptLicenceDto> all(AptLicenceSearchParams p) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("forestFileId", upper(p.forestFileId()), Types.VARCHAR)
        .addValue("clientName", upper(p.clientName()), Types.VARCHAR)
        .addValue("orgUnitName", upper(p.orgUnitName()), Types.VARCHAR)
        .addValue("muName", upper(p.managementUnitName()), Types.VARCHAR)
        .addValue("muType", upper(p.managementUnitType()), Types.VARCHAR)
        .addValue("muNumber", p.managementUnitNumber() == null
            || p.managementUnitNumber().isBlank() ? null : p.managementUnitNumber().trim(),
            Types.VARCHAR);
    return AptLicenceRules.distinctByForestFileId(
        jdbc.query(SEARCH_SQL, params, AptLicenceService::licence));
  }

  private static AptLicenceDto licence(ResultSet rs, int i) throws SQLException {
    BigDecimal term = rs.getBigDecimal("REPLACEMENT_TERM");
    return new AptLicenceDto(
        rs.getString("ORG_UNIT_NAME"),
        rs.getString("APT_MGMT_UNIT_ST"),
        rs.getString("MGMT_UNIT_NUMBER"),
        rs.getString("MGMT_UNIT_NAME"),
        rs.getString("FILE_STATUS_ST"),
        rs.getString("FILE_STATUS"),
        AptCommitmentService.getDate(rs, "FILE_STATUS_DATE"),
        rs.getString("FOREST_FILE_ID"),
        AptCommitmentService.getDate(rs, "LEGAL_EFFECTIVE_DT"),
        AptCommitmentService.getDate(rs, "INITIAL_EXPIRY_DT"),
        AptCommitmentService.getDate(rs, "CURRENT_EXPIRY_DT"),
        rs.getBigDecimal("TENURE_TERM"),
        rs.getString("CLIENT_NUMBER"),
        rs.getString("CLIENT_NAME"),
        rs.getString("CONTROL_COMPANY"),
        rs.getString("APT_FOA_ST"),
        AptCommitmentService.getDate(rs, "REPLACEMENT_DATE"),
        term == null ? null : term.intValue(),
        rs.getString("COMMENTS"),
        AptCommitmentService.getDateTime(rs, "ADD_TIMESTAMP"),
        rs.getString("ADD_USERID"),
        AptCommitmentService.getDateTime(rs, "UPDATE_TIMESTAMP"),
        rs.getString("UPDATE_USERID"));
  }

  private static String upper(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
