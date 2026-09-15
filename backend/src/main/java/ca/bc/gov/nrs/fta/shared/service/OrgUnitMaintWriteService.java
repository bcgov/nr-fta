package ca.bc.gov.nrs.fta.shared.service;

import ca.bc.gov.nrs.fta.shared.dto.OrgUnitMaintRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write operations for the Org Unit Maintenance screen (SIL99).
 *
 * <p>Persists the authenticated user's default org unit. The legacy {@code
 * PKG_SIL_CODE_LISTS} package only READS org units ({@code GET_ORG_UNIT} /
 * {@code GET_ORG_UNIT_CODE_ONLY}) from {@code THE.ORG_UNIT} — there is no
 * insert/update proc in the spec or body. The statement below is therefore
 * derived faithfully from the spec's base table ({@code THE.ORG_UNIT}) plus the
 * form parameter, upserting the user's default keyed by the audit user id.
 * Runs against the shared {@code THE} Oracle schema — there is no local
 * database, so it is exercised only in a deployed environment.
 */
@Service
public class OrgUnitMaintWriteService {

  private final NamedParameterJdbcTemplate jdbc;

  public OrgUnitMaintWriteService(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * WebADE application acronym this app files its per-user default under.
   * {@code MOF_USER_ORG_DEFAULT} is shared across ministry applications, so the
   * acronym is half the primary key — without it we would read and overwrite
   * other applications' defaults for the same user.
   */
  private static final String APPLICATION_ACRONYM = "FTA";

  // The per-user default org unit lives in THE.MOF_USER_ORG_DEFAULT, keyed by
  // (APPLICATION_ACRONYM, USER_ID). Both the table and that key come from
  // THE.SIL_99_USER_ORG_DEFAULT, the legacy package that reads and writes it;
  // this mirrors its GET and CHANGE procedures:
  //
  //   * USER_ID is matched and stored upper-cased (GET does
  //     "USER_ID = UPPER(P_USERID)"), so a mixed-case IDIR login still finds
  //     the row it wrote last time.
  //   * REVISION_COUNT is NOT NULL — 0 on insert, incremented on update, as
  //     CHANGE does. It is the optimistic-locking counter the ministry
  //     packages check, so it must keep moving even when we write directly.
  //
  // The account holds SELECT, INSERT and UPDATE here but not DELETE, which is
  // why this is a MERGE with no delete branch.
  private static final String UPSERT_SQL =
      """
      MERGE INTO the.mof_user_org_default tgt
      USING (
        SELECT
          :applicationAcronym AS application_acronym,
          UPPER(:userId) AS user_id,
          (SELECT org_unit_no FROM the.org_unit WHERE org_unit_code = :orgUnitCode) AS org_unit_no
        FROM dual
      ) src
      ON (tgt.application_acronym = src.application_acronym
          AND tgt.user_id = src.user_id)
      WHEN MATCHED THEN UPDATE SET
        tgt.org_unit_no = src.org_unit_no,
        tgt.update_userid = UPPER(:userId),
        tgt.update_timestamp = SYSDATE,
        tgt.revision_count = tgt.revision_count + 1
      WHEN NOT MATCHED THEN INSERT (
        application_acronym, user_id, org_unit_no,
        entry_userid, entry_timestamp, update_userid, update_timestamp, revision_count
      ) VALUES (
        src.application_acronym, src.user_id, src.org_unit_no,
        UPPER(:userId), SYSDATE, UPPER(:userId), SYSDATE, 0
      )
      """;

  /**
   * Set the authenticated user's default org unit. Returns the number of rows
   * affected.
   *
   * @param request the org unit chosen on the form
   * @param userId  the authenticated user id (key + audit columns)
   */
  @Transactional
  public int setDefaultOrgUnit(OrgUnitMaintRequest request, String userId) {
    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("applicationAcronym", APPLICATION_ACRONYM)
        .addValue("orgUnitCode", request.orgUnitCode())
        .addValue("userId", userId);
    return jdbc.update(UPSERT_SQL, params);
  }
}
