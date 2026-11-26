package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.user.UserLookupClient;
import ca.bc.gov.nrs.fta.user.UserLookupClient.IdirUser;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write operations for range zones.
 *
 * <p>Ports the {@code SAVE} path of the legacy {@code THE.FTA_631_RANGE_ZONE}
 * package to an upsert against {@code THE.RANGE_ZONE} via {@link
 * NamedParameterJdbcTemplate}. As in the package body, the record is keyed by
 * (admin_forest_district_no, range_zone_code): if a row exists it is UPDATEd
 * (bumping revision_count), otherwise a new row is INSERTed with revision_count
 * = 1. The audit user id is written to the entry/update_userid columns.
 *
 * <p>Before saving: {@code Fta631RangeZoneForm}'s required fields and input lengths, its
 * upper-casing of the zone code and contact ({@link ManageZoneRules}), and the user must
 * administer the zone's district ({@link LoggedUserHelper#administersDistrict}).
 *
 * <p>Legacy picked the contact from the district's District Range Tech users in WebADE and took
 * their name from there. Nothing here lists users by role, so the contact is typed as an IDIR
 * user id, checked against nr-user-lookup-api, and the name stored is the one it returns. Where
 * the lookup is not configured (a local run) the id is taken as typed, with no name. Runs
 * against the shared {@code THE} Oracle schema — there is no local database, so
 * it is exercised only in a deployed environment.
 */
@Service
public class ManageZoneAddWriteService {

  private static final Logger LOG = LoggerFactory.getLogger(ManageZoneAddWriteService.class);

  private static final String DISTRICT_CODE_SQL =
      "SELECT org_unit_code FROM the.org_unit WHERE org_unit_no = :adminForestDistrictNo";

  private final NamedParameterJdbcTemplate jdbc;
  private final LoggedUserHelper auth;
  private final UserLookupClient userLookup;

  public ManageZoneAddWriteService(
      NamedParameterJdbcTemplate jdbc, LoggedUserHelper auth, UserLookupClient userLookup) {
    this.jdbc = jdbc;
    this.auth = auth;
    this.userLookup = userLookup;
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
        :rangeZoneCode, :zoneDescription, :adminForestDistrictNo,
        :contact, :contactUserId, :contactPhoneNumber, :contactEmailAddress,
        :userId, SYSDATE, :userId, SYSDATE, 1
      )
      """;

  /**
   * Save (insert or update) a range zone. Returns the number of rows affected.
   *
   * @param request the range zone to save
   * @param userId  the authenticated user id (audit columns)
   * @throws ResponseStatusException 400 for a missing or invalid field or an unknown contact;
   *     403 if the user does not administer the district; 503 if the contact cannot be checked
   */
  @Transactional
  public int save(ManageZoneAddRequest request, String userId) {
    List<String> errors = ManageZoneRules.validate(request);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    long districtNo = Long.parseLong(request.adminForestDistrictNo().trim());
    List<String> district = jdbc.queryForList(DISTRICT_CODE_SQL,
        new MapSqlParameterSource("adminForestDistrictNo", districtNo), String.class);
    if (district.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ManageZoneRules.DISTRICT_INVALID);
    }
    if (!auth.administersDistrict(district.get(0))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ManageZoneRules.NOT_IN_ADMIN_ORG);
    }
    String contactUserId = ManageZoneRules.contactUserId(request.contactUserId());

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("rangeZoneCode", ManageZoneRules.zoneCode(request.rangeZoneCode()))
        .addValue("zoneDescription", ManageZoneRules.trimToNull(request.zoneDescription()))
        .addValue("adminForestDistrictNo", districtNo)
        .addValue("contact", contactName(contactUserId))
        .addValue("contactUserId", contactUserId)
        .addValue("contactPhoneNumber", request.contactPhoneNumber().trim())
        .addValue("contactEmailAddress", request.contactEmailAddress().trim())
        .addValue("userId", userId);

    Integer existing = jdbc.queryForObject(EXISTS_SQL, params, Integer.class);
    if (existing != null && existing > 0) {
      return jdbc.update(UPDATE_SQL, params);
    }
    return jdbc.update(INSERT_SQL, params);
  }

  /**
   * The contact's name from IDIR, upper-cased; null where the lookup is not configured.
   *
   * @throws ResponseStatusException 400 if IDIR has no such user; 503 if the lookup fails
   */
  private String contactName(String contactUserId) {
    if (!userLookup.isConfigured()) {
      return null;
    }
    Optional<IdirUser> user;
    try {
      user = userLookup.getIdirDetail(ManageZoneRules.bareIdir(contactUserId));
    } catch (RuntimeException ex) {
      LOG.warn("user-lookup failed for range zone contact {}: {}", contactUserId, ex.getMessage());
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "The contact could not be checked against IDIR. Try again later.");
    }
    return user
        .map(u -> ManageZoneRules.contactName(u.firstName(), u.lastName()))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST, ManageZoneRules.contactNotFound(contactUserId)));
  }
}
