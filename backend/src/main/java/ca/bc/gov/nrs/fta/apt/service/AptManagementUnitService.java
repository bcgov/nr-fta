package ca.bc.gov.nrs.fta.apt.service;

import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.MU_NAMES;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.MU_SOURCE;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.MU_TYPE_CODES;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.ORA_DUPLICATE;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.badRequest;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.conflict;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.date;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.dbError;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getDate;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getDateTime;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getLong;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.isOracleError;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.str;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitSearchCriteria;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitUnassignedDto;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * APT2's ManagementUnitService, as direct SQL on the tables the {@code THE.APT_*} routines
 * used: management units ({@code APT_MANAGEMENT_UNITS_GET}, {@code APT_MANAGEMENT_UNIT_INS_UPD}),
 * the code-table units not yet in APT ({@code APT_MGMT_UNIT_UNASSIGNED_GET}) and their AAC
 * determinations ({@code APT_MGMT_UNIT_HISTORIES_GET}, {@code APT_MGMT_UNIT_HISTORY_INS_UPD},
 * with {@code APT_PKG.INSERT_MUH_CHILD_RECORDS}'s copy).
 *
 * <p>{@code APT_MANAGEMENT_UNITS} has no trigger, and the routine never set its audit columns
 * (it ignored its user), so neither does this. {@code MUH_BR_IU_TRG} stamps the history's
 * timestamps and falls back to the database user when no user id is given; the user id is
 * given, as the routine did. None of these tables has a revision count.
 *
 * <p>An update of an unknown id is a 404, never a new record.
 */
@Service
public class AptManagementUnitService {

  /** The routine's own message for a type and number already in APT. */
  static final String UNIT_EXISTS = "Management Unit is already exist.";

  /** {@code APT_MGMT_UNIT_HISTORY_INS_UPD}'s message for {@code MUH_UK}. */
  static final String SAME_DATES =
      "Error: 2 records have the same Effective and Apportionment Dates.";

  private static final String COPY_ERROR = "Error: %s in APT_PKG.INSERT_MUH_CHILD_RECORDS ";

  /** {@code APT_MANAGEMENT_UNITS_GET}. A unit with no code-table name or org unit is left out. */
  static final String SEARCH_SQL =
      "SELECT mu.id,"
          + " musv.mgmt_unit_name,"
          + " mu.org_unit_no,"
          + " ou.org_unit_code || ' - ' || ou.org_unit_name AS org_unit_name,"
          + " mu.apt_mgmt_unit_st,"
          + " mu.mgmt_unit_number,"
          + " mu.mgmt_unit_status_st,"
          + " mu.status_effective_date,"
          + " mu.mu_id,"
          + " musv1.mgmt_unit_name AS src_mgmt_unit_name,"
          + " mu.orcs_file_number,"
          + " mu.comments,"
          + " mu.add_timestamp,"
          + " mu.add_userid"
          + " FROM the.apt_management_units mu"
          + " JOIN " + MU_SOURCE + " musv ON musv.id = mu.id"
          + " LEFT JOIN " + MU_SOURCE + " musv1 ON musv1.id = mu.mu_id"
          + " LEFT JOIN the.org_unit ou ON ou.org_unit_no = mu.org_unit_no"
          + " WHERE (mu.id = :id OR :id IS NULL)"
          + " AND (mu.apt_mgmt_unit_st = :type OR :type IS NULL)"
          + " AND (mu.mgmt_unit_number = :number OR :number IS NULL)"
          + " AND (mu.mgmt_unit_status_st = :status OR :status IS NULL)"
          + " AND (mu.status_effective_date = :effectiveDate OR :effectiveDate IS NULL)"
          + " AND (mu.org_unit_no = :orgUnitNo OR :orgUnitNo IS NULL)"
          // The routine always applied this one: '%%' with no name, so a unit must have an org
          // unit with a name or code.
          + " AND mu.org_unit_no IN (SELECT org_unit_no FROM the.org_unit"
          + " WHERE UPPER(org_unit_name) LIKE '%' || UPPER(:orgUnitName) || '%'"
          + " OR UPPER(org_unit_code) LIKE '%' || UPPER(:orgUnitName) || '%')"
          + " AND (UPPER(mu.orcs_file_number) LIKE '%' || UPPER(:orcs) || '%' OR :orcs IS NULL)"
          + " AND (mu.mu_id = :sourceId OR :sourceId IS NULL)"
          + " ORDER BY musv.apt_mgmt_unit_st, musv.mgmt_unit_name";

  /** {@code APT_MGMT_UNIT_UNASSIGNED_GET}. */
  static final String UNASSIGNED_SQL =
      "SELECT amus.apt_mgmt_unit_st || amus.mgmt_unit_number AS mgmt_unit_code,"
          + " amus.mgmt_unit_name"
          + " FROM " + MU_NAMES + " amus"
          + " LEFT JOIN the.apt_management_units amu"
          + " ON amu.apt_mgmt_unit_st = amus.apt_mgmt_unit_st"
          + " AND amu.mgmt_unit_number = amus.mgmt_unit_number"
          + " WHERE amus.apt_mgmt_unit_st IN " + MU_TYPE_CODES
          + " AND amu.apt_mgmt_unit_st IS NULL"
          + " AND amu.mgmt_unit_number IS NULL"
          + " ORDER BY amus.mgmt_unit_name";

  /** {@code cur_chk_mu_exist}. */
  static final String UNIT_EXISTS_SQL =
      """
      SELECT COUNT(*) FROM the.apt_management_units
       WHERE apt_mgmt_unit_st = :type AND mgmt_unit_number = :number
      """;

  static final String INSERT_UNIT_SQL =
      """
      INSERT INTO the.apt_management_units (
        id, org_unit_no, apt_mgmt_unit_st, mgmt_unit_number, mgmt_unit_status_st,
        status_effective_date, mu_id, orcs_file_number, comments
      ) VALUES (
        :id, :orgUnitNo, :type, :number, :status,
        :effectiveDate, :muId, :orcs, :comments
      )
      """;

  static final String UPDATE_UNIT_SQL =
      """
      UPDATE the.apt_management_units
         SET org_unit_no           = :orgUnitNo,
             apt_mgmt_unit_st      = :type,
             mgmt_unit_number      = :number,
             mgmt_unit_status_st   = :status,
             status_effective_date = :effectiveDate,
             mu_id                 = :muId,
             orcs_file_number      = :orcs,
             comments              = :comments
       WHERE id = :id
      """;

  /** {@code APT_MGMT_UNIT_HISTORIES_GET}, newest effective date first. */
  static final String HISTORY_SQL =
      """
      SELECT muh.id, muh.mu_id, muh.determination_date, muh.effective_date,
             muh.allowable_annual_cut, muh.inventory_rev_date,
             muh.apportionment_determination_dt, muh.apportionment_effective_date,
             muh.total_area, muh.productive_area, muh.long_term_harvest_level,
             muh.current_net_operating_area, muh.future_harvest_landbase_area,
             muh.comments, muh.add_timestamp, muh.add_userid, muh.allowable_annual_cut_ha
        FROM the.apt_mgmt_unit_history muh
       WHERE (muh.id = :historyId OR :historyId IS NULL)
         AND (muh.mu_id = :muId OR :muId IS NULL)
       ORDER BY muh.effective_date DESC
      """;

  static final String INSERT_HISTORY_SQL =
      """
      INSERT INTO the.apt_mgmt_unit_history (
        id, mu_id, determination_date, effective_date, allowable_annual_cut,
        allowable_annual_cut_ha, inventory_rev_date, apportionment_determination_dt,
        apportionment_effective_date, total_area, productive_area, long_term_harvest_level,
        current_net_operating_area, future_harvest_landbase_area, comments, add_userid
      ) VALUES (
        :id, :muId, :determinationDate, :effectiveDate, :aac,
        :aacHa, :inventoryRevDate, :aptDeterminationDate,
        :aptEffectiveDate, :totalArea, :productiveArea, :longTermHarvestLevel,
        :currentNetOpArea, :futureHarvestLbaseArea, :comments, :user
      )
      """;

  static final String UPDATE_HISTORY_SQL =
      """
      UPDATE the.apt_mgmt_unit_history
         SET determination_date             = :determinationDate,
             effective_date                 = :effectiveDate,
             allowable_annual_cut           = :aac,
             allowable_annual_cut_ha        = :aacHa,
             inventory_rev_date             = :inventoryRevDate,
             apportionment_determination_dt = :aptDeterminationDate,
             apportionment_effective_date   = :aptEffectiveDate,
             total_area                     = :totalArea,
             productive_area                = :productiveArea,
             long_term_harvest_level        = :longTermHarvestLevel,
             current_net_operating_area     = :currentNetOpArea,
             future_harvest_landbase_area   = :futureHarvestLbaseArea,
             comments                       = :comments,
             update_userid                  = :user
       WHERE id = :id
      """;

  // ── APT_PKG.INSERT_MUH_CHILD_RECORDS: its cursors and inserts. Like the package, the copies
  //    carry no AAC (ha) and no user id (the triggers stamp the database user). ──

  static final String COPY_PARTITIONS_SQL =
      """
      SELECT the.part_seq.NEXTVAL AS new_id, id AS old_id, apt_partition_st AS code,
             allowable_annual_cut, comments
        FROM the.apt_partitions
       WHERE muh_id = :prevId
      """;

  static final String INSERT_PARTITION_COPY_SQL =
      """
      INSERT INTO the.apt_partitions (id, muh_id, apt_partition_st, allowable_annual_cut, comments)
      VALUES (:id, :muhId, :type, :aac, :comments)
      """;

  static final String COPY_APPORTIONMENTS_SQL =
      """
      SELECT the.prtaprtmt_seq.NEXTVAL AS new_id, id AS old_id, apt_foa_st AS code,
             allowable_annual_cut, comments
        FROM the.apt_part_apportionments
       WHERE part_id = :oldId
      """;

  static final String INSERT_APPORTIONMENT_COPY_SQL =
      """
      INSERT INTO the.apt_part_apportionments (id, part_id, apt_foa_st, allowable_annual_cut,
                                               comments)
      VALUES (:id, :partId, :foa, :aac, :comments)
      """;

  static final String COPY_DISTRIBUTIONS_SQL =
      """
      SELECT the.aptd_seq.NEXTVAL AS new_id, id AS old_id, muouo_id, distribution_volume,
             effective_date
        FROM the.apt_apt_distributions
       WHERE papt_id = :oldId
      """;

  static final String INSERT_DISTRIBUTION_COPY_SQL =
      """
      INSERT INTO the.apt_apt_distributions (id, papt_id, muouo_id, distribution_volume,
                                             effective_date)
      VALUES (:id, :paptId, :muouoId, :volume, :effectiveDate)
      """;

  /** {@code aptdj_cur} and its insert: the old distribution's latest journal rows, unless DEL. */
  static final String INSERT_DISTRIBUTION_JN_COPY_SQL =
      """
      INSERT INTO the.apt_apt_distributions_jn (
        id, jn_datetime, jn_operation, jn_oracle_user, papt_id, muouo_id,
        distribution_volume, effective_date, jn_notes, jn_appln, jn_session
      )
      SELECT :id, SYSDATE, adj.jn_operation, adj.jn_oracle_user, :paptId, adj.muouo_id,
             adj.distribution_volume, adj.effective_date,
             '(Inserted via copy function) ' || adj.jn_notes, adj.jn_appln, adj.jn_session
        FROM the.apt_apt_distributions_jn adj
       WHERE adj.id = :oldId
         AND adj.jn_operation <> 'DEL'
         AND adj.jn_datetime IN (SELECT MAX(jn_datetime) FROM the.apt_apt_distributions_jn
                                  WHERE id = adj.id)
      """;

  private static final RowMapper<AptManagementUnitDto> UNIT = (rs, i) -> new AptManagementUnitDto(
      getLong(rs, "ID"),
      rs.getString("MGMT_UNIT_NAME"),
      getLong(rs, "ORG_UNIT_NO"),
      rs.getString("ORG_UNIT_NAME"),
      rs.getString("APT_MGMT_UNIT_ST"),
      rs.getString("MGMT_UNIT_NUMBER"),
      rs.getString("MGMT_UNIT_STATUS_ST"),
      getDate(rs, "STATUS_EFFECTIVE_DATE"),
      getLong(rs, "MU_ID"),
      rs.getString("SRC_MGMT_UNIT_NAME"),
      rs.getString("ORCS_FILE_NUMBER"),
      rs.getString("COMMENTS"),
      getDateTime(rs, "ADD_TIMESTAMP"),
      rs.getString("ADD_USERID"),
      null,
      null);

  private static final RowMapper<AptManagementUnitHistoryDto> HISTORY =
      (rs, i) -> new AptManagementUnitHistoryDto(
          getLong(rs, "ID"),
          getLong(rs, "MU_ID"),
          getDate(rs, "DETERMINATION_DATE"),
          getDate(rs, "EFFECTIVE_DATE"),
          rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"),
          getDate(rs, "INVENTORY_REV_DATE"),
          getDate(rs, "APPORTIONMENT_DETERMINATION_DT"),
          getDate(rs, "APPORTIONMENT_EFFECTIVE_DATE"),
          rs.getBigDecimal("TOTAL_AREA"),
          rs.getBigDecimal("PRODUCTIVE_AREA"),
          rs.getBigDecimal("LONG_TERM_HARVEST_LEVEL"),
          rs.getBigDecimal("CURRENT_NET_OPERATING_AREA"),
          rs.getBigDecimal("FUTURE_HARVEST_LANDBASE_AREA"),
          rs.getString("COMMENTS"),
          getDateTime(rs, "ADD_TIMESTAMP"),
          rs.getString("ADD_USERID"),
          null,
          null,
          rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT_HA"));

  private static final RowMapper<AptManagementUnitUnassignedDto> UNASSIGNED = (rs, i) -> {
    String code = rs.getString("MGMT_UNIT_CODE");
    String[] parts = AptManagementUnitRules.splitUnassignedCode(code);
    return new AptManagementUnitUnassignedDto(code, rs.getString("MGMT_UNIT_NAME"), parts[0],
        parts[1]);
  };

  /** A row the copy reads: its new id (from the sequence), its old id and what is copied. */
  private record CopyRow(Long newId, Long oldId, String code, BigDecimal aac, String comments) {}

  private record DistributionRow(Long newId, Long oldId, Long muouoId, BigDecimal volume,
      Timestamp effectiveDate) {}

  private final NamedParameterJdbcTemplate jdbc;

  public AptManagementUnitService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── management units ──

  /** {@code APT_MANAGEMENT_UNITS_GET}, ordered by type then name. */
  public List<AptManagementUnitDto> search(AptManagementUnitSearchCriteria c) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", c.id(), Types.NUMERIC)
        .addValue("type", str(trim(c.managementUnitType())), Types.VARCHAR)
        .addValue("number", str(trim(c.managementUnitNumber())), Types.VARCHAR)
        .addValue("status", str(trim(c.status())), Types.VARCHAR)
        .addValue("effectiveDate", date(c.effectiveDate()), Types.DATE)
        .addValue("orgUnitNo", c.orgUnitNumber(), Types.NUMERIC)
        .addValue("orgUnitName", str(trim(c.orgUnitName())), Types.VARCHAR)
        .addValue("orcs", str(trim(c.orcsFileNumber())), Types.VARCHAR)
        .addValue("sourceId", c.sourceManagementUnitId(), Types.NUMERIC);
    return jdbc.query(SEARCH_SQL, p, UNIT);
  }

  /** One management unit; 404 when there is none. */
  public AptManagementUnitDto get(Long id) {
    return search(AptManagementUnitSearchCriteria.byId(id)).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Management unit " + id + " not found."));
  }

  /** Creates a management unit; 409 when one of that type and number already exists. */
  @Transactional
  public AptManagementUnitDto create(AptManagementUnitRequest r, String user) {
    reject(AptManagementUnitRules.managementUnitProblem(r));
    MapSqlParameterSource p = unitParams(r);
    Long existing = jdbc.queryForObject(UNIT_EXISTS_SQL, p, Long.class);
    if (existing != null && existing > 0) {
      throw conflict(UNIT_EXISTS);
    }
    Long id = nextVal(jdbc, "the.mu_seq");
    p.addValue("id", id, Types.NUMERIC);
    try {
      jdbc.update(INSERT_UNIT_SQL, p);
    } catch (DataAccessException ex) {
      throw dbError(ex);
    }
    return get(id);
  }

  /** Updates a management unit; 404 when there is none. */
  @Transactional
  public AptManagementUnitDto update(Long id, AptManagementUnitRequest r, String user) {
    get(id);
    reject(AptManagementUnitRules.managementUnitProblem(r));
    if (id.equals(r.muId())) {
      reject("A management unit cannot be its own source management unit.");
    }
    try {
      jdbc.update(UPDATE_UNIT_SQL, unitParams(r).addValue("id", id, Types.NUMERIC));
    } catch (DataAccessException ex) {
      // A type and number another unit has: MU_MUNV_FK_I's ORA-00001, a 409.
      throw dbError(ex);
    }
    return get(id);
  }

  /** The code-table management units APT has no record of ({@code APT_MGMT_UNIT_UNASSIGNED_GET}). */
  public List<AptManagementUnitUnassignedDto> unassigned() {
    return jdbc.query(UNASSIGNED_SQL, Map.of(), UNASSIGNED);
  }

  private static MapSqlParameterSource unitParams(AptManagementUnitRequest r) {
    return new MapSqlParameterSource()
        .addValue("orgUnitNo", r.orgUnitNo(), Types.NUMERIC)
        .addValue("type", str(trim(r.muType())), Types.VARCHAR)
        .addValue("number", str(trim(r.muNo())), Types.VARCHAR)
        .addValue("status", str(trim(r.status())), Types.VARCHAR)
        .addValue("effectiveDate", date(r.effectiveDate()), Types.DATE)
        .addValue("muId", r.muId(), Types.NUMERIC)
        .addValue("orcs", str(trim(r.orcsFileNumber())), Types.VARCHAR)
        .addValue("comments", str(r.comments()), Types.VARCHAR);
  }

  // ── history (AAC determinations) ──

  /**
   * A management unit's determinations, newest effective date first
   * ({@code APT_MGMT_UNIT_HISTORIES_GET}); 404 when the unit does not exist.
   *
   * @param historyId just this determination, or null for all
   */
  public List<AptManagementUnitHistoryDto> history(Long muId, Long historyId) {
    get(muId);
    return historyRows(muId, historyId);
  }

  /**
   * Adds a determination. With {@code prevId}, that determination's partitions, apportionments
   * and BCTS distributions are copied onto the new one, as {@code APT_PKG.INSERT_MUH_CHILD_RECORDS}
   * did; all in one transaction.
   */
  @Transactional
  public AptManagementUnitHistoryDto createHistory(Long muId, AptManagementUnitHistoryRequest r,
      String user) {
    get(muId);
    reject(AptManagementUnitRules.historyProblem(r));
    if (r.prevId() != null && historyRows(muId, r.prevId()).isEmpty()) {
      reject("The determination to copy from is not one of this management unit's.");
    }
    Long id = nextVal(jdbc, "the.muh_seq");
    try {
      jdbc.update(INSERT_HISTORY_SQL, historyParams(id, muId, r, user));
    } catch (DataAccessException ex) {
      throw dbError(ex, ORA_DUPLICATE, SAME_DATES);
    }
    if (r.prevId() != null) {
      copyChildRecords(id, r.prevId());
    }
    return oneHistory(muId, id);
  }

  /** Updates a determination; 404 when the unit has no such determination. */
  @Transactional
  public AptManagementUnitHistoryDto updateHistory(Long muId, Long historyId,
      AptManagementUnitHistoryRequest r, String user) {
    oneHistory(muId, historyId);
    reject(AptManagementUnitRules.historyProblem(r));
    try {
      jdbc.update(UPDATE_HISTORY_SQL, historyParams(historyId, muId, r, user));
    } catch (DataAccessException ex) {
      throw dbError(ex, ORA_DUPLICATE, SAME_DATES);
    }
    return oneHistory(muId, historyId);
  }

  private List<AptManagementUnitHistoryDto> historyRows(Long muId, Long historyId) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("historyId", historyId, Types.NUMERIC)
        .addValue("muId", muId, Types.NUMERIC);
    return jdbc.query(HISTORY_SQL, p, HISTORY);
  }

  private AptManagementUnitHistoryDto oneHistory(Long muId, Long historyId) {
    return historyRows(muId, historyId).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Management unit history " + historyId + " not found."));
  }

  private static MapSqlParameterSource historyParams(Long id, Long muId,
      AptManagementUnitHistoryRequest r, String user) {
    return new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("muId", muId, Types.NUMERIC)
        .addValue("determinationDate", date(r.determinationDate()), Types.DATE)
        .addValue("effectiveDate", date(r.effectiveDate()), Types.DATE)
        .addValue("aac", r.allowableAnnualCut(), Types.NUMERIC)
        .addValue("aacHa", r.allowableAnnualCutHa(), Types.NUMERIC)
        .addValue("inventoryRevDate", date(r.inventoryRevDate()), Types.DATE)
        .addValue("aptDeterminationDate", date(r.apportionmentDeterminationDt()), Types.DATE)
        .addValue("aptEffectiveDate", date(r.apportionmentEffectiveDate()), Types.DATE)
        .addValue("totalArea", r.totalArea(), Types.NUMERIC)
        .addValue("productiveArea", r.productiveArea(), Types.NUMERIC)
        .addValue("longTermHarvestLevel", r.longTermHarvestLevel(), Types.NUMERIC)
        .addValue("currentNetOpArea", r.currentNetOperatingArea(), Types.NUMERIC)
        .addValue("futureHarvestLbaseArea", r.futureHarvestLandbaseArea(), Types.NUMERIC)
        .addValue("comments", str(r.comments()), Types.VARCHAR)
        .addValue("user", str(user), Types.VARCHAR);
  }

  // ── APT_PKG.INSERT_MUH_CHILD_RECORDS ──

  /**
   * Copies determination {@code prevId}'s partitions, their apportionments, their BCTS
   * distributions and each distribution's latest journal entry onto determination {@code muhId}.
   * A failure is the package's 400, worded as it worded it.
   */
  void copyChildRecords(Long muhId, Long prevId) {
    List<CopyRow> partitions = jdbc.query(COPY_PARTITIONS_SQL,
        new MapSqlParameterSource("prevId", prevId), AptManagementUnitService::copyRow);
    for (CopyRow part : partitions) {
      copyInsert(INSERT_PARTITION_COPY_SQL, new MapSqlParameterSource()
          .addValue("id", part.newId(), Types.NUMERIC)
          .addValue("muhId", muhId, Types.NUMERIC)
          .addValue("type", part.code(), Types.VARCHAR)
          .addValue("aac", part.aac(), Types.NUMERIC)
          .addValue("comments", part.comments(), Types.VARCHAR),
          "when inserting APT_PARTITION record with ID = " + part.newId(),
          "when inserting APT_PARTITION record.");
      List<CopyRow> apportionments = jdbc.query(COPY_APPORTIONMENTS_SQL,
          new MapSqlParameterSource("oldId", part.oldId()), AptManagementUnitService::copyRow);
      for (CopyRow papt : apportionments) {
        // The package's OTHERS message here names APT_APT_DISTRIBUTIONS; kept as it was.
        copyInsert(INSERT_APPORTIONMENT_COPY_SQL, new MapSqlParameterSource()
            .addValue("id", papt.newId(), Types.NUMERIC)
            .addValue("partId", part.newId(), Types.NUMERIC)
            .addValue("foa", papt.code(), Types.VARCHAR)
            .addValue("aac", papt.aac(), Types.NUMERIC)
            .addValue("comments", papt.comments(), Types.VARCHAR),
            "when inserting APT_PART_APPORTIONMENTS record with ID = " + papt.newId(),
            "when inserting APT_APT_DISTRIBUTIONS record.");
        copyDistributions(papt);
      }
    }
  }

  private void copyDistributions(CopyRow papt) {
    List<DistributionRow> distributions = jdbc.query(COPY_DISTRIBUTIONS_SQL,
        new MapSqlParameterSource("oldId", papt.oldId()), (rs, i) -> new DistributionRow(
            getLong(rs, "NEW_ID"), getLong(rs, "OLD_ID"), getLong(rs, "MUOUO_ID"),
            rs.getBigDecimal("DISTRIBUTION_VOLUME"), rs.getTimestamp("EFFECTIVE_DATE")));
    for (DistributionRow d : distributions) {
      copyInsert(INSERT_DISTRIBUTION_COPY_SQL, new MapSqlParameterSource()
          .addValue("id", d.newId(), Types.NUMERIC)
          .addValue("paptId", papt.newId(), Types.NUMERIC)
          .addValue("muouoId", d.muouoId(), Types.NUMERIC)
          .addValue("volume", d.volume(), Types.NUMERIC)
          .addValue("effectiveDate", d.effectiveDate(), Types.TIMESTAMP),
          "when inserting APT_APT_DISTRIBUTIONS record with ID = " + d.newId(),
          "when inserting APT_APT_DISTRIBUTIONS record.");
      copyInsert(INSERT_DISTRIBUTION_JN_COPY_SQL, new MapSqlParameterSource()
          .addValue("id", d.newId(), Types.NUMERIC)
          .addValue("paptId", papt.newId(), Types.NUMERIC)
          .addValue("oldId", d.oldId(), Types.NUMERIC),
          "when inserting APT_APT_DISTRIBUTIONS_JN record with ID = " + d.newId(),
          "when inserting APT_APT_DISTRIBUTIONS_JN record.");
    }
  }

  private static CopyRow copyRow(ResultSet rs, int i) throws SQLException {
    return new CopyRow(getLong(rs, "NEW_ID"), getLong(rs, "OLD_ID"), rs.getString("CODE"),
        rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"), rs.getString("COMMENTS"));
  }

  /** One of the package's inserts, with its DUP_VAL_ON_INDEX and OTHERS messages. */
  private void copyInsert(String sql, MapSqlParameterSource p, String onDuplicate,
      String onOther) {
    try {
      jdbc.update(sql, p);
    } catch (DuplicateKeyException ex) {
      throw badRequest(COPY_ERROR.formatted("DUP_VAL_ON_INDEX") + onDuplicate);
    } catch (DataAccessException ex) {
      if (!isOracleError(ex)) {
        throw ex;
      }
      throw badRequest(COPY_ERROR.formatted("OTHERS") + onOther);
    }
  }

  // ── helpers shared with the partition services ──

  /** The next value of {@code sequence} (schema-qualified), as the routines took their ids. */
  static Long nextVal(NamedParameterJdbcTemplate jdbc, String sequence) {
    return jdbc.queryForObject("SELECT " + sequence + ".NEXTVAL FROM dual", Map.of(), Long.class);
  }

  static void reject(String problem) {
    if (problem != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
    }
  }

  static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
