package ca.bc.gov.nrs.fta.apt.service;

import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentHistoryDto;
import ca.bc.gov.nrs.fta.apt.dto.AptCommitmentRequest;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Commitments: APT2's CommitmentService, in SQL against the tables the {@code THE.APT_*}
 * routines used, which FTA no longer calls.
 *
 * <ul>
 *   <li>{@link #searchByManagementUnit} — {@code APT_COMMITMENTS_GET_BY_MU}. Its "management
 *       unit id" is compared with the unit's <em>number</em> ({@code PROV_FOREST_USE.MGMT_UNIT_ID}
 *       / {@code APT_MANAGEMENT_UNITS.MGMT_UNIT_NUMBER}), which is what APT2 passed.
 *   <li>{@link #byLicence} — {@code APT_COMMITMENTS_GET_BY_FFID}
 *   <li>{@link #get} — {@code APT_COMMITMENT_GET}
 *   <li>{@link #history} — {@code APT_COMMITMENTS_JN_GET}
 *   <li>{@link #create} / {@link #update} — {@code APT_COMMITMENT_INS_UPD}, which journals
 *       through {@code APT_COMMITMENT_JN_INS}
 *   <li>{@link #delete} — {@code APT_COMMITMENT_DEL}, journalling the same way
 * </ul>
 *
 * <p>The reads are the functions' cursors, with {@code APT_PART_TYPES_V} written out over its
 * code tables. Neither {@code APT_COMMITMENTS} nor {@code APT_COMMITMENTS_JN} has a trigger, so
 * the id ({@code COMM_SEQ}) and every journal column are set here, as the procedures set them.
 * Neither table has a revision count; the procedures did no optimistic locking.
 */
@Service
public class AptCommitmentService {

  /** APT2's screen code, which the journal records as {@code JN_APPLN}. */
  static final String APPLN = "APTM6300";

  /**
   * {@code APT_COMMITMENT_INS_UPD}'s message for any ORA-00001 on the commitment itself. It is
   * about partition apportionments, but it is what APT2 showed.
   */
  static final String DUPLICATE =
      "The selected FOA Type has been used by another partition apportionment record. "
          + "Please Choose the another FOA Type and click save again.";

  /** {@code APT_PART_TYPES_V}: the partition type codes and their descriptions. */
  private static final String PART_TYPES =
      """
      part_types AS (
        SELECT clt.code_argument AS part_type_cd, clt.expanded_result AS part_type_desc
          FROM the.code_list_table clt
          JOIN the.code_subset_tbl cst
            ON clt.column_name = cst.column_name
           AND clt.code_argument = cst.code_argument
         WHERE cst.code_subset_name = 'APT_PARTITION_ST'
      )
      """;

  /**
   * {@code APT_COMMITMENTS_GET_BY_MU}: a held licence's commitments, plus the counted
   * commitments of partition apportionments to licences outside a management unit (type Z).
   * Each half carries its own totals, summed over the same joins (so a licence with two A/B
   * clients counts twice, as the function did).
   */
  private static final String BY_MU_SQL =
      "WITH " + PART_TYPES
          + """
          SELECT ac.id, ac.forest_file_id, fc.client_name, ac.apt_partition_st,
                 aptv.part_type_desc AS apt_partition, ac.commit_type_st,
                 ac.allowable_annual_cut, ac.include_in_lic_aac_ind, ac.determination_date,
                 ac.effective_date, ac.expiry_date, ac.papt_id, ttl.ttl_aac, ttl.ttl_incl_aac
            FROM the.apt_commitments ac
            JOIN the.apt_licences al ON ac.forest_file_id = al.forest_file_id
            JOIN the.for_client_link fcl ON al.forest_file_id = fcl.forest_file_id
            JOIN the.forest_client fc ON fcl.client_number = fc.client_number
            JOIN part_types aptv ON aptv.part_type_cd = ac.apt_partition_st
            JOIN the.prov_forest_use pfu ON pfu.forest_file_id = al.forest_file_id
           CROSS JOIN (
             SELECT SUM(ac1.allowable_annual_cut) AS ttl_aac,
                    SUM(DECODE(ac1.include_in_lic_aac_ind, 'Y', ac1.allowable_annual_cut, 0))
                      AS ttl_incl_aac
               FROM the.apt_commitments ac1
               JOIN the.apt_licences al1 ON ac1.forest_file_id = al1.forest_file_id
               JOIN the.for_client_link fcl1 ON al1.forest_file_id = fcl1.forest_file_id
               JOIN the.prov_forest_use pfu1 ON pfu1.forest_file_id = al1.forest_file_id
               JOIN the.forest_client fc1 ON fcl1.client_number = fc1.client_number
               JOIN part_types aptv1 ON aptv1.part_type_cd = ac1.apt_partition_st
              WHERE pfu1.file_status_st IN ('HI', 'HS')
                AND fcl1.file_client_type IN ('A', 'B')
                AND (al1.apt_foa_st = :foaType OR :foaType IS NULL)
                AND (pfu1.mgmt_unit_type = :muType OR :muType IS NULL)
                AND (pfu1.mgmt_unit_id = :muNumber OR :muNumber IS NULL)
                AND (ac1.apt_partition_st = :partitionType OR :partitionType IS NULL)
           ) ttl
           WHERE pfu.file_status_st IN ('HI', 'HS')
             AND fcl.file_client_type IN ('A', 'B')
             AND (al.apt_foa_st = :foaType OR :foaType IS NULL)
             AND (pfu.mgmt_unit_type = :muType OR :muType IS NULL)
             AND (pfu.mgmt_unit_id = :muNumber OR :muNumber IS NULL)
             AND (ac.apt_partition_st = :partitionType OR :partitionType IS NULL)
          UNION
          SELECT comm.id, comm.forest_file_id, fc.client_name, comm.apt_partition_st,
                 aptv.part_type_desc AS apt_partition, comm.commit_type_st,
                 comm.allowable_annual_cut, comm.include_in_lic_aac_ind,
                 comm.determination_date, comm.effective_date, comm.expiry_date, comm.papt_id,
                 ttl1.ttl_aac, ttl1.ttl_incl_aac
            FROM the.prov_forest_use pfu
            JOIN the.apt_licences lic ON lic.forest_file_id = pfu.forest_file_id
            JOIN the.for_client_link fcl ON lic.forest_file_id = fcl.forest_file_id
            JOIN the.forest_client fc ON fcl.client_number = fc.client_number
            JOIN the.apt_commitments comm ON comm.forest_file_id = lic.forest_file_id
            JOIN the.apt_part_apportionments papt ON comm.papt_id = papt.id
            JOIN the.apt_partitions part ON papt.part_id = part.id
            JOIN the.apt_mgmt_unit_history muh ON part.muh_id = muh.id
            JOIN the.apt_management_units mu ON muh.mu_id = mu.id
            JOIN part_types aptv ON aptv.part_type_cd = comm.apt_partition_st
           CROSS JOIN (
             SELECT SUM(comm2.allowable_annual_cut) AS ttl_aac,
                    SUM(DECODE(comm2.include_in_lic_aac_ind, 'Y', comm2.allowable_annual_cut, 0))
                      AS ttl_incl_aac
               FROM the.prov_forest_use pfu2
               JOIN the.apt_licences lic2 ON lic2.forest_file_id = pfu2.forest_file_id
               JOIN the.for_client_link fcl2 ON lic2.forest_file_id = fcl2.forest_file_id
               JOIN the.forest_client fc2 ON fcl2.client_number = fc2.client_number
               JOIN the.apt_commitments comm2 ON comm2.forest_file_id = lic2.forest_file_id
               JOIN the.apt_part_apportionments papt2 ON comm2.papt_id = papt2.id
               JOIN the.apt_partitions part2 ON papt2.part_id = part2.id
               JOIN the.apt_mgmt_unit_history muh2 ON part2.muh_id = muh2.id
               JOIN the.apt_management_units mu2 ON muh2.mu_id = mu2.id
               JOIN part_types aptv2 ON aptv2.part_type_cd = comm2.apt_partition_st
              WHERE pfu2.mgmt_unit_type = 'Z'
                AND pfu2.file_status_st IN ('HI', 'HS')
                AND fcl2.file_client_type IN ('A', 'B')
                AND comm2.include_in_lic_aac_ind = 'Y'
                AND (lic2.apt_foa_st = :foaType OR :foaType IS NULL)
                AND (mu2.apt_mgmt_unit_st = :muType OR :muType IS NULL)
                AND (mu2.mgmt_unit_number = :muNumber OR :muNumber IS NULL)
                AND (comm2.apt_partition_st = :partitionType OR :partitionType IS NULL)
           ) ttl1
           WHERE pfu.mgmt_unit_type = 'Z'
             AND pfu.file_status_st IN ('HI', 'HS')
             AND fcl.file_client_type IN ('A', 'B')
             AND comm.include_in_lic_aac_ind = 'Y'
             AND (lic.apt_foa_st = :foaType OR :foaType IS NULL)
             AND (mu.apt_mgmt_unit_st = :muType OR :muType IS NULL)
             AND (mu.mgmt_unit_number = :muNumber OR :muNumber IS NULL)
             AND (comm.apt_partition_st = :partitionType OR :partitionType IS NULL)
           ORDER BY forest_file_id, include_in_lic_aac_ind DESC
          """;

  /**
   * {@code APT_COMMITMENTS_GET_BY_FFID}: the same two halves for one licence, without the held
   * status filter; the first half's totals are not limited to held licences either.
   */
  private static final String BY_FFID_SQL =
      "WITH " + PART_TYPES
          + """
          SELECT ac.id, ac.forest_file_id, fc.client_name, ac.apt_partition_st,
                 aptv.part_type_desc AS apt_partition, ac.commit_type_st,
                 ac.allowable_annual_cut, ac.include_in_lic_aac_ind, ac.determination_date,
                 ac.effective_date, ac.expiry_date, ac.papt_id, ttl.ttl_aac, ttl.ttl_incl_aac
            FROM the.apt_commitments ac
            JOIN the.apt_licences al ON ac.forest_file_id = al.forest_file_id
            JOIN the.for_client_link fcl ON al.forest_file_id = fcl.forest_file_id
            JOIN the.forest_client fc ON fcl.client_number = fc.client_number
            JOIN part_types aptv ON aptv.part_type_cd = ac.apt_partition_st
            JOIN the.prov_forest_use pfu ON pfu.forest_file_id = al.forest_file_id
           CROSS JOIN (
             SELECT SUM(ac1.allowable_annual_cut) AS ttl_aac,
                    SUM(DECODE(ac1.include_in_lic_aac_ind, 'Y', ac1.allowable_annual_cut, 0))
                      AS ttl_incl_aac
               FROM the.apt_commitments ac1
               JOIN the.apt_licences al1 ON ac1.forest_file_id = al1.forest_file_id
               JOIN the.for_client_link fcl1 ON al1.forest_file_id = fcl1.forest_file_id
               JOIN the.prov_forest_use pfu1 ON pfu1.forest_file_id = al1.forest_file_id
               JOIN the.forest_client fc1 ON fcl1.client_number = fc1.client_number
               JOIN part_types aptv1 ON aptv1.part_type_cd = ac1.apt_partition_st
              WHERE fcl1.file_client_type IN ('A', 'B')
                AND ac1.forest_file_id = :forestFileId
           ) ttl
           WHERE fcl.file_client_type IN ('A', 'B')
             AND ac.forest_file_id = :forestFileId
          UNION
          SELECT comm.id, comm.forest_file_id, fc.client_name, comm.apt_partition_st,
                 aptv.part_type_desc AS apt_partition, comm.commit_type_st,
                 comm.allowable_annual_cut, comm.include_in_lic_aac_ind,
                 comm.determination_date, comm.effective_date, comm.expiry_date, comm.papt_id,
                 ttl1.ttl_aac, ttl1.ttl_incl_aac
            FROM the.prov_forest_use pfu
            JOIN the.apt_licences lic ON lic.forest_file_id = pfu.forest_file_id
            JOIN the.for_client_link fcl ON lic.forest_file_id = fcl.forest_file_id
            JOIN the.forest_client fc ON fcl.client_number = fc.client_number
            JOIN the.apt_commitments comm ON comm.forest_file_id = lic.forest_file_id
            JOIN the.apt_part_apportionments papt ON comm.papt_id = papt.id
            JOIN the.apt_partitions part ON papt.part_id = part.id
            JOIN the.apt_mgmt_unit_history muh ON part.muh_id = muh.id
            JOIN the.apt_management_units mu ON muh.mu_id = mu.id
            JOIN part_types aptv ON aptv.part_type_cd = comm.apt_partition_st
           CROSS JOIN (
             SELECT SUM(comm2.allowable_annual_cut) AS ttl_aac,
                    SUM(DECODE(comm2.include_in_lic_aac_ind, 'Y', comm2.allowable_annual_cut, 0))
                      AS ttl_incl_aac
               FROM the.prov_forest_use pfu2
               JOIN the.apt_licences lic2 ON lic2.forest_file_id = pfu2.forest_file_id
               JOIN the.for_client_link fcl2 ON lic2.forest_file_id = fcl2.forest_file_id
               JOIN the.forest_client fc2 ON fcl2.client_number = fc2.client_number
               JOIN the.apt_commitments comm2 ON comm2.forest_file_id = lic2.forest_file_id
               JOIN the.apt_part_apportionments papt2 ON comm2.papt_id = papt2.id
               JOIN the.apt_partitions part2 ON papt2.part_id = part2.id
               JOIN the.apt_mgmt_unit_history muh2 ON part2.muh_id = muh2.id
               JOIN the.apt_management_units mu2 ON muh2.mu_id = mu2.id
               JOIN part_types aptv2 ON aptv2.part_type_cd = comm2.apt_partition_st
              WHERE pfu2.mgmt_unit_type = 'Z'
                AND fcl2.file_client_type IN ('A', 'B')
                AND comm2.include_in_lic_aac_ind = 'Y'
                AND lic2.forest_file_id = :forestFileId
           ) ttl1
           WHERE pfu.mgmt_unit_type = 'Z'
             AND fcl.file_client_type IN ('A', 'B')
             AND comm.include_in_lic_aac_ind = 'Y'
             AND lic.forest_file_id = :forestFileId
           ORDER BY forest_file_id, include_in_lic_aac_ind DESC
          """;

  /**
   * {@code APT_COMMITMENT_GET}: one row per A or B client of the licence, and none when the
   * licence has no such client or no {@code PROV_FOREST_USE} row.
   */
  private static final String GET_SQL =
      "WITH " + PART_TYPES
          + """
          SELECT ac.id, ac.forest_file_id, fc.client_name, ac.apt_partition_st,
                 aptv.part_type_desc AS apt_partition, ac.commit_type_st,
                 ac.allowable_annual_cut, ac.include_in_lic_aac_ind, ac.determination_date,
                 ac.effective_date, ac.expiry_date, ac.papt_id
            FROM the.apt_commitments ac
            JOIN the.apt_licences al ON ac.forest_file_id = al.forest_file_id
            JOIN the.for_client_link fcl ON al.forest_file_id = fcl.forest_file_id
            JOIN the.forest_client fc ON fcl.client_number = fc.client_number
            JOIN part_types aptv ON aptv.part_type_cd = ac.apt_partition_st
            JOIN the.prov_forest_use pfu ON pfu.forest_file_id = al.forest_file_id
           WHERE fcl.file_client_type IN ('A', 'B')
             AND ac.id = :id
          """;

  /**
   * {@code APT_COMMITMENTS_JN_GET}, newest first. The function concatenated the row id into
   * dynamic SQL; here it is a bind, compared with {@code ROWID} as before.
   */
  private static final String HISTORY_SQL =
      """
      SELECT ROWIDTOCHAR(acj.ROWID) AS row_id, acj.jn_operation, acj.jn_oracle_user,
             acj.jn_datetime, acj.jn_notes, acj.jn_appln, acj.jn_session, acj.id,
             acj.forest_file_id, acj.apt_partition_st, acj.commit_type_st,
             acj.allowable_annual_cut, acj.include_in_lic_aac_ind, acj.determination_date,
             acj.effective_date, acj.expiry_date, acj.papt_id
        FROM the.apt_commitments_jn acj
       WHERE acj.id = :id
         AND (:rowId IS NULL OR acj.ROWID = :rowId)
       ORDER BY acj.jn_datetime DESC
      """;

  private static final String NEXT_ID_SQL = "SELECT the.comm_seq.NEXTVAL FROM dual";

  private static final String INSERT_SQL =
      """
      INSERT INTO the.apt_commitments (
        id, forest_file_id, apt_partition_st, commit_type_st, allowable_annual_cut,
        include_in_lic_aac_ind, determination_date, effective_date, expiry_date, papt_id
      ) VALUES (
        :id, :forestFileId, :partition, :commitType, :aac,
        :incl, :determinationDate, :effectiveDate, :expiryDate, :paptId
      )
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE the.apt_commitments
         SET forest_file_id         = :forestFileId,
             apt_partition_st       = :partition,
             commit_type_st         = :commitType,
             allowable_annual_cut   = :aac,
             include_in_lic_aac_ind = :incl,
             determination_date     = :determinationDate,
             effective_date         = :effectiveDate,
             expiry_date            = :expiryDate,
             papt_id                = :paptId
       WHERE id = :id
      """;

  /** {@code APT_COMMITMENT_DEL}'s before image, which its journal row records. */
  private static final String BEFORE_IMAGE_SQL =
      """
      SELECT id, papt_id, forest_file_id, apt_partition_st, commit_type_st,
             allowable_annual_cut, include_in_lic_aac_ind, determination_date, effective_date,
             expiry_date
        FROM the.apt_commitments
       WHERE id = :id
      """;

  private static final String DELETE_SQL = "DELETE FROM the.apt_commitments WHERE id = :id";

  /**
   * {@code APT_COMMITMENT_JN_INS}. {@code JN_SESSION} was never set, and still is not.
   */
  private static final String JOURNAL_SQL =
      """
      INSERT INTO the.apt_commitments_jn (
        jn_operation, jn_oracle_user, jn_datetime, jn_notes, jn_appln,
        id, papt_id, forest_file_id, apt_partition_st, commit_type_st, allowable_annual_cut,
        include_in_lic_aac_ind, determination_date, effective_date, expiry_date
      ) VALUES (
        :operation, :user, SYSDATE, :reason, :appln,
        :id, :paptId, :forestFileId, :partition, :commitType, :aac,
        :incl, :determinationDate, :effectiveDate, :expiryDate
      )
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public AptCommitmentService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Commitments of a management unit's licences ({@code APT_COMMITMENTS_GET_BY_MU}); every
   * argument is an optional filter.
   */
  public List<AptCommitmentDto> searchByManagementUnit(String managementUnitType,
      String managementUnitNumber, String foaType, String partitionType) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("muType", upper(managementUnitType), Types.VARCHAR)
        .addValue("muNumber", trim(managementUnitNumber), Types.VARCHAR)
        .addValue("foaType", upper(foaType), Types.VARCHAR)
        .addValue("partitionType", upper(partitionType), Types.VARCHAR);
    return AptCommitmentRules.distinctById(jdbc.query(BY_MU_SQL, p, commitment(true)));
  }

  /** A licence's commitments ({@code APT_COMMITMENTS_GET_BY_FFID}). */
  public List<AptCommitmentDto> byLicence(String forestFileId) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", trim(forestFileId), Types.VARCHAR);
    return AptCommitmentRules.distinctById(jdbc.query(BY_FFID_SQL, p, commitment(true)));
  }

  /**
   * One commitment ({@code APT_COMMITMENT_GET}).
   *
   * @throws ResponseStatusException 404 when there is none
   */
  public AptCommitmentDto get(long id) {
    return find(id).stream().findFirst()
        .orElseThrow(() -> notFound(id));
  }

  /**
   * A commitment's journal, newest first ({@code APT_COMMITMENTS_JN_GET}), or one entry of it.
   * A deleted commitment still has its journal.
   *
   * @throws ResponseStatusException 400 for a malformed row id; 404 when there is no journal
   *                                 and no such commitment
   */
  public List<AptCommitmentHistoryDto> history(long id, String rowId) {
    String row = rowId == null || rowId.isBlank() ? null : rowId.trim();
    if (row != null && !AptCommitmentRules.isRowId(row)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a history row id.");
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("rowId", row, Types.VARCHAR);
    List<AptCommitmentHistoryDto> rows;
    try {
      rows = jdbc.query(HISTORY_SQL, p, AptCommitmentService::history);
    } catch (DataAccessException e) {
      // A well-formed but impossible ROWID (ORA-01410): the function reported it as a 400.
      throw AptCommitmentRules.refusal(e);
    }
    if (rows.isEmpty() && row == null) {
      get(id);
    }
    return rows;
  }

  /**
   * Creates a commitment, journals it ({@code INS}) and returns it as stored, with the id
   * {@code COMM_SEQ} gave it.
   */
  @Transactional
  public AptCommitmentDto create(AptCommitmentRequest request, String user) {
    refuse(AptCommitmentRules.problem(request, false));
    Long id = jdbc.getJdbcTemplate().queryForObject(NEXT_ID_SQL, Long.class);
    if (id == null) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
          "COMM_SEQ returned no commitment id.");
    }
    MapSqlParameterSource p = values(id, request);
    write(INSERT_SQL, p);
    journal("INS", p, reasonOf(request), user);
    return readBack(id, request);
  }

  /**
   * Changes a commitment, journals it ({@code UPD}) and returns it as stored.
   *
   * @throws ResponseStatusException 404 when there is none; {@code APT_COMMITMENT_INS_UPD}
   *                                 would have inserted a new one under a new id
   */
  @Transactional
  public AptCommitmentDto update(long id, AptCommitmentRequest request, String user) {
    refuse(AptCommitmentRules.problem(request, true));
    get(id);
    MapSqlParameterSource p = values(id, request);
    if (write(UPDATE_SQL, p) == 0) {
      throw notFound(id);
    }
    journal("UPD", p, reasonOf(request), user);
    return readBack(id, request);
  }

  /**
   * Deletes a commitment, journalling its last values with the reason ({@code DEL}).
   *
   * @throws ResponseStatusException 404 when it was already gone; 409 when another record
   *                                 (an enforcement AAC reduction) still refers to it
   */
  @Transactional
  public void delete(long id, String reason, String user) {
    refuse(AptCommitmentRules.reasonProblem(reason));
    List<MapSqlParameterSource> before = jdbc.query(BEFORE_IMAGE_SQL,
        new MapSqlParameterSource("id", id), AptCommitmentService::beforeImage);
    if (before.isEmpty()) {
      throw notFound(id);
    }
    int deleted;
    try {
      deleted = jdbc.update(DELETE_SQL, new MapSqlParameterSource("id", id));
    } catch (DataAccessException e) {
      throw AptCommitmentRules.refusal(e);
    }
    if (deleted == 0) {
      throw notFound(id);
    }
    journal("DEL", before.get(0), reason == null || reason.isBlank() ? null : reason.trim(),
        user);
  }

  private List<AptCommitmentDto> find(long id) {
    return jdbc.query(GET_SQL, new MapSqlParameterSource("id", id), commitment(false));
  }

  /**
   * The commitment as stored. {@code APT_COMMITMENT_GET} only finds a commitment whose licence
   * has an A or B client; failing the request there would roll back a save that succeeded, so
   * the saved values stand in.
   */
  private AptCommitmentDto readBack(long id, AptCommitmentRequest r) {
    return find(id).stream().findFirst()
        .orElseGet(() -> new AptCommitmentDto(id, r.forestFileId().trim().toUpperCase(Locale.ROOT),
            null, r.aptPartitionSt().trim(), null, r.commitTypeSt().trim(), r.aac(),
            r.inclInLicAacInd(), r.determinationDate(), r.effectiveDate(), r.expiryDate(),
            r.paptId(), null, null));
  }

  /**
   * The commitment's columns as {@code APT_COMMITMENT_INS_UPD} received them: the licence
   * trimmed and upper-cased, the partition and type trimmed (not upper-cased).
   */
  private static MapSqlParameterSource values(long id, AptCommitmentRequest r) {
    return new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("paptId", r.paptId(), Types.NUMERIC)
        .addValue("forestFileId", trim(r.forestFileId().toUpperCase(Locale.ROOT)), Types.VARCHAR)
        .addValue("partition", trim(r.aptPartitionSt()), Types.VARCHAR)
        .addValue("commitType", trim(r.commitTypeSt()), Types.VARCHAR)
        .addValue("aac", r.aac(), Types.NUMERIC)
        .addValue("incl", blankToNull(r.inclInLicAacInd()), Types.VARCHAR)
        .addValue("determinationDate", r.determinationDate(), Types.DATE)
        .addValue("effectiveDate", r.effectiveDate(), Types.DATE)
        .addValue("expiryDate", r.expiryDate(), Types.DATE);
  }

  /**
   * Inserts or updates the commitment. Any ORA-00001 gets the procedure's own message and a
   * 409; any other database refusal is a 400 (409 for a child-record block), with Oracle's
   * message, as the procedure returned it.
   */
  private int write(String sql, MapSqlParameterSource p) {
    try {
      return jdbc.update(sql, p);
    } catch (DataAccessException e) {
      if (AptCommitmentRules.oracleCode(e) == AptCommitmentRules.ORA_UNIQUE) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE);
      }
      throw AptCommitmentRules.refusal(e);
    }
  }

  /** {@code APT_COMMITMENT_JN_INS}: the journal row, in the same transaction. */
  private void journal(String operation, MapSqlParameterSource values, String reason,
      String user) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValues(values.getValues())
        .addValue("operation", operation, Types.VARCHAR)
        .addValue("user", blankToNull(user), Types.VARCHAR)
        .addValue("reason", reason, Types.VARCHAR)
        .addValue("appln", APPLN, Types.VARCHAR);
    for (String name : values.getParameterNames()) {
      p.registerSqlType(name, values.getSqlType(name));
    }
    try {
      jdbc.update(JOURNAL_SQL, p);
    } catch (DataAccessException e) {
      throw AptCommitmentRules.refusal(e);
    }
  }

  private static String reasonOf(AptCommitmentRequest r) {
    return r.reason() == null || r.reason().isBlank() ? null : r.reason().trim();
  }

  private static MapSqlParameterSource beforeImage(ResultSet rs, int i) throws SQLException {
    return new MapSqlParameterSource()
        .addValue("id", getLong(rs, "ID"), Types.NUMERIC)
        .addValue("paptId", getLong(rs, "PAPT_ID"), Types.NUMERIC)
        .addValue("forestFileId", rs.getString("FOREST_FILE_ID"), Types.VARCHAR)
        .addValue("partition", rs.getString("APT_PARTITION_ST"), Types.VARCHAR)
        .addValue("commitType", rs.getString("COMMIT_TYPE_ST"), Types.VARCHAR)
        .addValue("aac", rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"), Types.NUMERIC)
        .addValue("incl", rs.getString("INCLUDE_IN_LIC_AAC_IND"), Types.VARCHAR)
        // TIMESTAMP keeps any time of day the DATE columns hold.
        .addValue("determinationDate", rs.getTimestamp("DETERMINATION_DATE"), Types.TIMESTAMP)
        .addValue("effectiveDate", rs.getTimestamp("EFFECTIVE_DATE"), Types.TIMESTAMP)
        .addValue("expiryDate", rs.getTimestamp("EXPIRY_DATE"), Types.TIMESTAMP);
  }

  private static RowMapper<AptCommitmentDto> commitment(boolean withTotals) {
    return (rs, i) -> new AptCommitmentDto(
        getLong(rs, "ID"),
        rs.getString("FOREST_FILE_ID"),
        rs.getString("CLIENT_NAME"),
        rs.getString("APT_PARTITION_ST"),
        rs.getString("APT_PARTITION"),
        rs.getString("COMMIT_TYPE_ST"),
        rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"),
        rs.getString("INCLUDE_IN_LIC_AAC_IND"),
        getDate(rs, "DETERMINATION_DATE"),
        getDate(rs, "EFFECTIVE_DATE"),
        getDate(rs, "EXPIRY_DATE"),
        getLong(rs, "PAPT_ID"),
        withTotals ? rs.getBigDecimal("TTL_AAC") : null,
        withTotals ? rs.getBigDecimal("TTL_INCL_AAC") : null);
  }

  private static AptCommitmentHistoryDto history(ResultSet rs, int i) throws SQLException {
    return new AptCommitmentHistoryDto(
        rs.getString("ROW_ID"),
        rs.getString("JN_OPERATION"),
        rs.getString("JN_ORACLE_USER"),
        getDateTime(rs, "JN_DATETIME"),
        rs.getString("JN_NOTES"),
        rs.getString("JN_APPLN"),
        getLong(rs, "JN_SESSION"),
        getLong(rs, "ID"),
        rs.getString("FOREST_FILE_ID"),
        rs.getString("APT_PARTITION_ST"),
        rs.getString("COMMIT_TYPE_ST"),
        rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"),
        rs.getString("INCLUDE_IN_LIC_AAC_IND"),
        getDate(rs, "DETERMINATION_DATE"),
        getDate(rs, "EFFECTIVE_DATE"),
        getDate(rs, "EXPIRY_DATE"),
        getLong(rs, "PAPT_ID"));
  }

  static Long getLong(ResultSet rs, String col) throws SQLException {
    BigDecimal v = rs.getBigDecimal(col);
    return v == null ? null : v.longValue();
  }

  static LocalDate getDate(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }

  static LocalDateTime getDateTime(ResultSet rs, String col) throws SQLException {
    Timestamp t = rs.getTimestamp(col);
    return t == null ? null : t.toLocalDateTime();
  }

  private static ResponseStatusException notFound(long id) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND,
        "Commitment " + id + " was not found.");
  }

  private static void refuse(String problem) {
    if (problem != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problem);
    }
  }

  private static String upper(String s) {
    return s == null || s.isBlank() ? null : s.trim().toUpperCase(Locale.ROOT);
  }

  private static String trim(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
