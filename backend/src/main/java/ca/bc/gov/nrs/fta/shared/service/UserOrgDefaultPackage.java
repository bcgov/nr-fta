package ca.bc.gov.nrs.fta.shared.service;

import java.sql.CallableStatement;
import java.sql.Types;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls to the legacy {@code THE.SIL_99_USER_ORG_DEFAULT} package, which keeps each user's
 * default org unit per application in {@code THE.MOF_USER_ORG_DEFAULT}. {@code MAINLINE}'s six
 * parameters are all {@code IN OUT VARCHAR2}: action, application acronym, org unit number,
 * user id, revision count and error message.
 *
 * <p>{@code GET} reads the row for {@code UPPER(p_userid)}; {@code SAVE} inserts it (revision
 * count 1) or, when it exists, updates it (revision count + 1). The package never checks the
 * revision count, so there is no optimistic lock: the last save wins, as in legacy. It does not
 * commit; the call joins the caller's transaction.
 */
@Component
public class UserOrgDefaultPackage {

  private static final String CALL = "{call the.sil_99_user_org_default.mainline(?, ?, ?, ?, ?, ?)}";

  /** What {@code MAINLINE} hands back: the org unit number and the error message. */
  public record Result(String orgUnitNo, String errorMessage) {}

  private final JdbcTemplate jdbc;

  public UserOrgDefaultPackage(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc.getJdbcTemplate();
  }

  /** {@code GET}: the user's saved default; NO_DATA_FOUND comes back as an error message. */
  public Result get(String userId) {
    return call("GET", null, userId);
  }

  /** {@code SAVE}: make {@code orgUnitNo} the user's default. */
  public Result save(String orgUnitNo, String userId) {
    return call("SAVE", orgUnitNo, userId);
  }

  private Result call(String action, String orgUnitNo, String userId) {
    String[] values = {action, OrgUnitMaintRules.APPLICATION_ACRONYM, orgUnitNo, userId, null, ""};
    Result r = jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          for (int i = 0; i < values.length; i++) {
            cs.setString(i + 1, values[i]);
            cs.registerOutParameter(i + 1, Types.VARCHAR);
          }
          cs.execute();
          return new Result(cs.getString(3), cs.getString(6));
        });
    return r == null ? new Result(null, null) : r;
  }
}
