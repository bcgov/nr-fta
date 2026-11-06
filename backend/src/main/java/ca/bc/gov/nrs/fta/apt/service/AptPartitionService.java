package ca.bc.gov.nrs.fta.apt.service;

import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.nextVal;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.reject;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.trim;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.ORA_CHILD_RECORD;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.ORA_DUPLICATE;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.PART_TYPES;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.dbError;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getDateTime;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getLong;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.str;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionRequest;
import java.sql.Types;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * APT2's PartitionService: the partitions of an AAC determination, as direct SQL on the tables
 * {@code APT_PARTITIONS_GET}, {@code APT_PARTITION_INS_UPD} and {@code APT_PARTITION_DEL} used.
 * Partition types are in {@link AptLookupService}.
 *
 * <p>{@code PART_BR_IUD_TRG} checks the partition type is in the {@code APT_PARTITION_ST}
 * subset (a 400 with its ORA-20000 otherwise) and stamps the add/update timestamps; the user id
 * is set here, as the routine did.
 */
@Service
public class AptPartitionService {

  /** {@code APT_PARTITION_INS_UPD}'s message for {@code PART_UK1}. */
  static final String TYPE_USED = "The selected Type has been used by another partition record."
      + " Please Choose the another Partition Type and click save again.";

  /** {@code APT_PARTITION_DEL}'s message for ORA-02292. */
  static final String REFERENCED =
      "Partition cannot be deleted because it is being referenced by another record.";

  /**
   * {@code APT_PARTITIONS_GET}: each partition with its type's description and its
   * apportionments' AAC summed, plus the AAC summed over every partition the filters match
   * (including any whose type is no longer in the subset, which are themselves left out).
   */
  static final String LIST_SQL =
      "SELECT ap.id, ap.muh_id, ap.apt_partition_st, aptyp.part_type_desc,"
          + " ap.allowable_annual_cut, ap.allowable_annual_cut_ha, ap.comments,"
          + " ap.add_timestamp, ap.add_userid, ap.update_timestamp, ap.update_userid,"
          + " ttl.ttl_aac, ttl.ttl_aac_ha,"
          + " SUM(apa.allowable_annual_cut) AS ttl_part_apt_aac,"
          + " SUM(apa.allowable_annual_cut_ha) AS ttl_part_apt_aac_ha"
          + " FROM the.apt_partitions ap"
          + " JOIN " + PART_TYPES + " aptyp ON aptyp.part_type_cd = ap.apt_partition_st"
          + " CROSS JOIN (SELECT SUM(allowable_annual_cut) AS ttl_aac,"
          + " SUM(allowable_annual_cut_ha) AS ttl_aac_ha"
          + " FROM the.apt_partitions"
          + " WHERE (id = :id OR :id IS NULL)"
          + " AND (muh_id = :muhId OR :muhId IS NULL)) ttl"
          + " LEFT JOIN the.apt_part_apportionments apa ON apa.part_id = ap.id"
          + " WHERE (ap.id = :id OR :id IS NULL)"
          + " AND (ap.muh_id = :muhId OR :muhId IS NULL)"
          + " GROUP BY ap.id, ap.muh_id, ap.apt_partition_st, aptyp.part_type_desc,"
          + " ap.allowable_annual_cut, ap.allowable_annual_cut_ha, ap.comments,"
          + " ap.add_timestamp, ap.add_userid, ap.update_timestamp, ap.update_userid,"
          + " ttl.ttl_aac, ttl.ttl_aac_ha"
          + " ORDER BY ap.apt_partition_st";

  static final String INSERT_SQL =
      """
      INSERT INTO the.apt_partitions (
        id, muh_id, apt_partition_st, allowable_annual_cut, allowable_annual_cut_ha, comments,
        add_userid
      ) VALUES (
        :id, :muhId, :type, :aac, :aacHa, :comments, :user
      )
      """;

  static final String UPDATE_SQL =
      """
      UPDATE the.apt_partitions
         SET muh_id                  = :muhId,
             apt_partition_st        = :type,
             allowable_annual_cut    = :aac,
             allowable_annual_cut_ha = :aacHa,
             comments                = :comments,
             update_userid           = :user
       WHERE id = :id
      """;

  static final String DELETE_SQL = "DELETE FROM the.apt_partitions WHERE id = :id";

  private static final RowMapper<AptPartitionDto> PARTITION = (rs, i) -> new AptPartitionDto(
      getLong(rs, "ID"),
      getLong(rs, "MUH_ID"),
      rs.getString("APT_PARTITION_ST"),
      rs.getString("PART_TYPE_DESC"),
      rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"),
      rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT_HA"),
      rs.getString("COMMENTS"),
      getDateTime(rs, "ADD_TIMESTAMP"),
      rs.getString("ADD_USERID"),
      getDateTime(rs, "UPDATE_TIMESTAMP"),
      rs.getString("UPDATE_USERID"),
      rs.getBigDecimal("TTL_AAC"),
      rs.getBigDecimal("TTL_AAC_HA"),
      rs.getBigDecimal("TTL_PART_APT_AAC"),
      rs.getBigDecimal("TTL_PART_APT_AAC_HA"));

  private final NamedParameterJdbcTemplate jdbc;

  public AptPartitionService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@code APT_PARTITIONS_GET}, ordered by partition type. One of the two is required: with
   * neither, it would return every partition in APT.
   */
  public List<AptPartitionDto> list(Long id, Long managementUnitHistoryId) {
    if (id == null && managementUnitHistoryId == null) {
      reject("Give a partition id or a management unit history id.");
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("muhId", managementUnitHistoryId, Types.NUMERIC);
    return jdbc.query(LIST_SQL, p, PARTITION);
  }

  /** One partition; 404 when there is none. */
  public AptPartitionDto get(Long id) {
    return list(id, null).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Partition " + id + " not found."));
  }

  /** Adds a partition; 409 when its type is already used in the determination. */
  @Transactional
  public AptPartitionDto create(AptPartitionRequest r, String user) {
    reject(AptPartitionRules.partitionProblem(r, true));
    Long id = nextVal(jdbc, "the.part_seq");
    save(INSERT_SQL, id, r.muhId(), r, user);
    return get(id);
  }

  /** Updates a partition, which stays with its determination; 404 when there is none. */
  @Transactional
  public AptPartitionDto update(Long id, AptPartitionRequest r, String user) {
    AptPartitionDto existing = get(id);
    reject(AptPartitionRules.partitionProblem(r, false));
    save(UPDATE_SQL, id, existing.muhId(), r, user);
    return get(id);
  }

  /** Deletes a partition; 404 when there is none, 409 while apportionments refer to it. */
  @Transactional
  public void delete(Long id) {
    get(id);
    try {
      jdbc.update(DELETE_SQL, new MapSqlParameterSource().addValue("id", id, Types.NUMERIC));
    } catch (DataAccessException ex) {
      throw dbError(ex, ORA_CHILD_RECORD, REFERENCED);
    }
  }

  private void save(String sql, Long id, Long muhId, AptPartitionRequest r, String user) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("muhId", muhId, Types.NUMERIC)
        .addValue("type", str(trim(r.partitionCode())), Types.VARCHAR)
        .addValue("aac", r.allowableAnnualCut(), Types.NUMERIC)
        .addValue("aacHa", r.allowableAnnualCutHa(), Types.NUMERIC)
        .addValue("comments", str(r.comments()), Types.VARCHAR)
        .addValue("user", str(user), Types.VARCHAR);
    try {
      jdbc.update(sql, p);
    } catch (DataAccessException ex) {
      throw dbError(ex, ORA_DUPLICATE, TYPE_USED);
    }
  }
}
