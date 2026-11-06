package ca.bc.gov.nrs.fta.apt.service;

import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.nextVal;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.reject;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitService.trim;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.FOA_TYPES;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.ORA_CHILD_RECORD;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.conflict;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.dbError;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getDateTime;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.getLong;
import static ca.bc.gov.nrs.fta.apt.service.AptManagementUnitSql.str;

import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentDto;
import ca.bc.gov.nrs.fta.apt.dto.AptPartitionApportionmentRequest;
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
 * APT2's PartitionApportionmentService: a partition's AAC apportioned by form of agreement, as
 * direct SQL on the tables {@code APT_PART_APPORTIONMENTS_GET}, {@code APT_PART_APRTMT_INS_UPD}
 * and {@code APT_PART_APRTMT_DEL} used.
 *
 * <p>{@code PAPT_BR_IUD_TRG} checks the FOA type is in the {@code APT_FOA_ST} subset (a 400 with
 * its ORA-20000 otherwise) and stamps the add/update timestamps; the user id is set here, as the
 * routine did.
 */
@Service
public class AptPartitionApportionmentService {

  /** {@code APT_PART_APRTMT_INS_UPD}'s message when the partition already has the FOA type. */
  static final String FOA_USED = "The selected FOA Type has been used by another partition"
      + " apportionment record. Please Choose the another FOA Type and click save again.";

  /** {@code APT_PART_APRTMT_DEL}'s message for ORA-02292. */
  static final String REFERENCED = "Partition Apportionment cannot be deleted because it is"
      + " being referenced by another record.";

  /**
   * {@code APT_PART_APPORTIONMENTS_GET}: each apportionment whose FOA type belongs to its
   * management unit's type, with its BCTS distribution volumes summed, plus the AAC summed over
   * every apportionment the filters match. Unordered, as the function was.
   */
  static final String LIST_SQL =
      "SELECT apa.id, apa.part_id, apa.apt_foa_st, aftv.expanded_result AS apt_foa_name,"
          + " apa.allowable_annual_cut, apa.comments, apa.add_timestamp, apa.add_userid,"
          + " apa.allowable_annual_cut_ha, ttl.ttl_aac, ttl.ttl_aac_ha,"
          + " SUM(aad.distribution_volume) AS ttl_apt_dist_dist_vol"
          + " FROM the.apt_management_units mu"
          + " JOIN the.apt_mgmt_unit_history muh ON muh.mu_id = mu.id"
          + " JOIN the.apt_partitions ap ON ap.muh_id = muh.id"
          + " JOIN the.apt_part_apportionments apa ON apa.part_id = ap.id"
          + " JOIN " + FOA_TYPES + " aftv ON aftv.code_argument = apa.apt_foa_st"
          + " AND aftv.apt_mgmt_unit_st = mu.apt_mgmt_unit_st"
          + " CROSS JOIN (SELECT SUM(allowable_annual_cut) AS ttl_aac,"
          + " SUM(allowable_annual_cut_ha) AS ttl_aac_ha"
          + " FROM the.apt_part_apportionments"
          + " WHERE (id = :id OR :id IS NULL)"
          + " AND (part_id = :partId OR :partId IS NULL)) ttl"
          + " LEFT JOIN the.apt_apt_distributions aad ON aad.papt_id = apa.id"
          + " WHERE (apa.id = :id OR :id IS NULL)"
          + " AND (apa.part_id = :partId OR :partId IS NULL)"
          + " GROUP BY apa.id, apa.part_id, apa.apt_foa_st, aftv.expanded_result,"
          + " apa.allowable_annual_cut, apa.comments, apa.add_timestamp, apa.add_userid,"
          + " apa.allowable_annual_cut_ha, ttl.ttl_aac, ttl.ttl_aac_ha";

  /** {@code check_foa_st} / {@code check_update_foa_st} ({@code :id} null on create). */
  static final String FOA_USED_SQL =
      """
      SELECT COUNT(*) FROM the.apt_part_apportionments
       WHERE part_id = :partId
         AND apt_foa_st = :foa
         AND (:id IS NULL OR id <> :id)
      """;

  static final String INSERT_SQL =
      """
      INSERT INTO the.apt_part_apportionments (
        id, part_id, apt_foa_st, allowable_annual_cut, allowable_annual_cut_ha, comments,
        add_userid
      ) VALUES (
        :newId, :partId, :foa, :aac, :aacHa, :comments, :user
      )
      """;

  static final String UPDATE_SQL =
      """
      UPDATE the.apt_part_apportionments
         SET part_id                 = :partId,
             apt_foa_st              = :foa,
             allowable_annual_cut    = :aac,
             allowable_annual_cut_ha = :aacHa,
             comments                = :comments,
             update_userid           = :user
       WHERE id = :id
      """;

  static final String DELETE_SQL = "DELETE FROM the.apt_part_apportionments WHERE id = :id";

  private static final RowMapper<AptPartitionApportionmentDto> APPORTIONMENT =
      (rs, i) -> new AptPartitionApportionmentDto(
          getLong(rs, "ID"),
          getLong(rs, "PART_ID"),
          rs.getString("APT_FOA_ST"),
          rs.getString("APT_FOA_NAME"),
          rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT"),
          rs.getBigDecimal("ALLOWABLE_ANNUAL_CUT_HA"),
          rs.getString("COMMENTS"),
          getDateTime(rs, "ADD_TIMESTAMP"),
          rs.getString("ADD_USERID"),
          null,
          null,
          rs.getBigDecimal("TTL_AAC"),
          rs.getBigDecimal("TTL_AAC_HA"),
          rs.getBigDecimal("TTL_APT_DIST_DIST_VOL"));

  private final NamedParameterJdbcTemplate jdbc;

  public AptPartitionApportionmentService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@code APT_PART_APPORTIONMENTS_GET}. One of the two is required: with neither, it would
   * return every apportionment in APT.
   */
  public List<AptPartitionApportionmentDto> list(Long id, Long partitionId) {
    if (id == null && partitionId == null) {
      reject("Give a partition apportionment id or a partition id.");
    }
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("partId", partitionId, Types.NUMERIC);
    return jdbc.query(LIST_SQL, p, APPORTIONMENT);
  }

  /** One apportionment; 404 when there is none. */
  public AptPartitionApportionmentDto get(Long id) {
    return list(id, null).stream().findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Partition apportionment " + id + " not found."));
  }

  /** Adds an apportionment; 409 when its FOA type is already used in the partition. */
  @Transactional
  public AptPartitionApportionmentDto create(AptPartitionApportionmentRequest r, String user) {
    reject(AptPartitionRules.apportionmentProblem(r, true));
    MapSqlParameterSource p = params(null, r.partitionId(), r, user);
    rejectUsedFoa(p);
    Long id = nextVal(jdbc, "the.prtaprtmt_seq");
    save(INSERT_SQL, p.addValue("newId", id, Types.NUMERIC));
    return get(id);
  }

  /** Updates an apportionment, which stays with its partition; 404 when there is none. */
  @Transactional
  public AptPartitionApportionmentDto update(Long id, AptPartitionApportionmentRequest r,
      String user) {
    AptPartitionApportionmentDto existing = get(id);
    reject(AptPartitionRules.apportionmentProblem(r, false));
    MapSqlParameterSource p = params(id, existing.partitionId(), r, user);
    rejectUsedFoa(p);
    save(UPDATE_SQL, p);
    return get(id);
  }

  /**
   * Deletes an apportionment; 404 when there is none, 409 while BCTS distributions refer to it
   * (APTD_PAPT_FK). Commitments keep its id in {@code APT_COMMITMENTS.PAPT_ID} with no foreign
   * key, so it is deleted from under them, as the routine and APT2 did.
   */
  @Transactional
  public void delete(Long id) {
    get(id);
    try {
      jdbc.update(DELETE_SQL, new MapSqlParameterSource().addValue("id", id, Types.NUMERIC));
    } catch (DataAccessException ex) {
      throw dbError(ex, ORA_CHILD_RECORD, REFERENCED);
    }
  }

  private void rejectUsedFoa(MapSqlParameterSource p) {
    Long used = jdbc.queryForObject(FOA_USED_SQL, p, Long.class);
    if (used != null && used > 0) {
      throw conflict(FOA_USED);
    }
  }

  private void save(String sql, MapSqlParameterSource p) {
    try {
      jdbc.update(sql, p);
    } catch (DataAccessException ex) {
      throw dbError(ex);
    }
  }

  private static MapSqlParameterSource params(Long id, Long partitionId,
      AptPartitionApportionmentRequest r, String user) {
    return new MapSqlParameterSource()
        .addValue("id", id, Types.NUMERIC)
        .addValue("partId", partitionId, Types.NUMERIC)
        .addValue("foa", str(trim(r.foaTypeCode())), Types.VARCHAR)
        .addValue("aac", r.allowableAnnualCut(), Types.NUMERIC)
        .addValue("aacHa", r.allowableAnnualCutHa(), Types.NUMERIC)
        .addValue("comments", str(r.comments()), Types.VARCHAR)
        .addValue("user", str(user), Types.VARCHAR);
  }
}
