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

  // NOTE: Derived from the PKG_SIL_CODE_LISTS spec only — the package exposes no
  // write proc. org_unit_no is resolved from THE.ORG_UNIT (the spec's base
  // table); the default is upserted per user with audit columns.
  private static final String UPSERT_SQL =
      """
      MERGE INTO the.user_default_org_unit tgt
      USING (
        SELECT
          :userId AS user_id,
          (SELECT org_unit_no FROM the.org_unit WHERE org_unit_code = :orgUnitCode) AS org_unit_no
        FROM dual
      ) src
      ON (tgt.user_id = src.user_id)
      WHEN MATCHED THEN UPDATE SET
        tgt.org_unit_no = src.org_unit_no,
        tgt.update_userid = :userId,
        tgt.update_timestamp = SYSDATE
      WHEN NOT MATCHED THEN INSERT (
        user_id, org_unit_no, entry_userid, entry_timestamp, update_userid, update_timestamp
      ) VALUES (
        src.user_id, src.org_unit_no, :userId, SYSDATE, :userId, SYSDATE
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
        .addValue("orgUnitCode", request.orgUnitCode())
        .addValue("userId", userId);
    return jdbc.update(UPSERT_SQL, params);
  }
}
