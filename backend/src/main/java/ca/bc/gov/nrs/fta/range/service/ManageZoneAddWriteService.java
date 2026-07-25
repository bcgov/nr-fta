package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for range zones.
 *
 * <p>Ports the {@code SAVE} path of the legacy {@code THE.FTA_631_RANGE_ZONE}
 * package to an upsert against {@code THE.RANGE_ZONE} via {@link
 * NamedParameterJdbcTemplate}. As in the package body, the record is keyed by
 * (admin_forest_district_no, range_zone_code): if a row exists it is UPDATEd
 * (bumping revision_count), otherwise a new row is INSERTed with revision_count
 * = 1. The audit user id is written to the entry/update_userid columns. Runs
 * against the shared {@code THE} Oracle schema — there is no local database, so
 * it is exercised only in a deployed environment.
 */
@Service
public class ManageZoneAddWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public ManageZoneAddWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String EXISTS_SQL =
      """
      SELECT COUNT(*) FROM the.range_zone
      WHERE admin_forest_district_no = :adminForestDistrictNo
        AND range_zone_code = :rangeZoneCode
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE the.range_zone
         SET zone_description = :zoneDescription,
             contact = :contact,
             contact_user_id = :contactUserId,
             contact_phone_number = :contactPhoneNumber,
             contact_email_address = :contactEmailAddress,
             update_userid = :userId,
             update_timestamp = SYSDATE,
             revision_count = revision_count + 1
       WHERE admin_forest_district_no = :adminForestDistrictNo
         AND range_zone_code = :rangeZoneCode
      """;

  private static final String INSERT_SQL =
      """
      INSERT INTO the.range_zone (
        range_zone_code, zone_description, admin_forest_district_no,
        contact, contact_user_id, contact_phone_number, contact_email_address,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        :rangeZoneCode, :zoneDescription, TO_NUMBER(:adminForestDistrictNo),
        :contact, :contactUserId, :contactPhoneNumber, :contactEmailAddress,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  /**
   * Save (insert or update) a range zone. Returns the number of rows affected.
   *
   * @param request the range zone to save
   * @param userId  the authenticated user id (audit columns)
   */
  @Transactional
  public int save(ManageZoneAddRequest request, String userId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("rangeZoneCode", request.rangeZoneCode())
        .addValue("zoneDescription", request.zoneDescription())
        .addValue("adminForestDistrictNo", request.adminForestDistrictNo())
        .addValue("contact", request.contact())
        .addValue("contactUserId", request.contactUserId())
        .addValue("contactPhoneNumber", request.contactPhoneNumber())
        .addValue("contactEmailAddress", request.contactEmailAddress())
        .addValue("userId", userId);

    Integer existing = jdbc.queryForObject(EXISTS_SQL, params, Integer.class);
    if (existing != null && existing > 0) {
      return jdbc.update(UPDATE_SQL, params);
    }
    return jdbc.update(INSERT_SQL, params);
  }
}
