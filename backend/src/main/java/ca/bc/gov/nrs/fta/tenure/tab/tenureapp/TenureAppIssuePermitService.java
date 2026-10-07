package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitRequest;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppIssuePermitResult;
import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Legacy FTA950's Issue Permit (Fta950xTenAppIssuePermitAction).
 *
 * <p>Legacy uploaded the permit PDF to document management, then ran
 * {@code FTA_950X_TEN_APP_ISSUE_PERMIT.mainline('ISSUE_PERMIT', …)} twice — first with
 * {@code p_validate_only_no_transaction = 'TRUE'} (the issuance checks only), then
 * {@code 'FALSE'} with the document's link — and finally e-mailed the recipients. The package
 * (2,400 lines: the CP / file / range / FSJ / FUP First Nations issuance rules, the permit
 * document records, the authority to HI, the file to HI, the application to ISS) is called
 * here rather than ported, so its rules stay exactly legacy's. Both calls run in one
 * transaction: any error from either rolls everything back (the package deletes the
 * application's earlier permit documents even on the validation pass).
 *
 * <p>Not ported: the document-management upload (this app has no DM client — the caller gives
 * the document's link instead) and the e-mail to recipients (no CMSG client).
 */
@Service
public class TenureAppIssuePermitService {

  private static final String APPLICATION_SQL =
      """
      SELECT ta.tenure_application_state_code
        FROM the.tenure_application ta
       WHERE ta.tenure_app_id = :tenureAppId
         AND (ta.forest_file_id = :forestFileId
              OR EXISTS (SELECT 1 FROM the.tenure_application_map_feature tamf
                          WHERE tamf.tenure_app_id = ta.tenure_app_id
                            AND tamf.forest_file_id = :forestFileId))
      """;

  private static final String FILE_TYPE_SQL =
      "SELECT file_type_code FROM the.prov_forest_use WHERE forest_file_id = :forestFileId";

  private static final String HVA_ON_FILE_SQL =
      """
      SELECT COUNT(*) FROM the.harvesting_authority
       WHERE hva_skey = :hvaSkey AND forest_file_id = :forestFileId
      """;

  private static final String CALL =
      "DECLARE"
          + " v_action VARCHAR2(30) := 'ISSUE_PERMIT';"
          + " v_file VARCHAR2(30) := ?;"
          + " v_type VARCHAR2(30) := ?;"
          + " v_app VARCHAR2(30) := ?;"
          + " v_hva VARCHAR2(30) := ?;"
          + " v_uri VARCHAR2(4000) := ?;"
          + " v_user VARCHAR2(100) := ?;"
          + " v_err VARCHAR2(4000);"
          + " v_validate VARCHAR2(10) := ?;"
          + " BEGIN the.fta_950x_ten_app_issue_permit.mainline("
          + "v_action, v_file, v_type, v_app, v_hva, v_uri, v_user, v_err, v_validate);"
          + " ? := v_err; END;";

  private final NamedParameterJdbcTemplate named;
  private final JdbcTemplate jdbc;

  public TenureAppIssuePermitService(NamedParameterJdbcTemplate named) {
    this.named = named;
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * Issues the permit for one of the file's applications.
   *
   * @throws ResponseStatusException 404 if the application is not on the file; 409 if it is
   *     not approved or issued; 400 on a missing document or a failed issuance check
   */
  @Transactional
  public TenureAppIssuePermitResult issue(
      String forestFileId, long tenureAppId, TenureAppIssuePermitRequest q, String userId) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("forestFileId", forestFileId)
        .addValue("tenureAppId", tenureAppId, Types.NUMERIC);
    List<String> states = named.queryForList(APPLICATION_SQL, p, String.class);
    if (states.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Tenure application " + tenureAppId + " is not on this file.");
    }
    String state = states.get(0);
    if (!TenureAppRules.canIssue(state)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, TenureAppRules.issueBlockedReason(state));
    }
    String documentUri = q == null ? null : q.documentUri();
    List<String> errors = TenureAppRules.validateIssue(documentUri);
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join(" ", errors));
    }
    Long hvaSkey = q.hvaSkey();
    if (hvaSkey != null) {
      Long n = named.queryForObject(
          HVA_ON_FILE_SQL,
          new MapSqlParameterSource()
              .addValue("hvaSkey", hvaSkey, Types.NUMERIC)
              .addValue("forestFileId", forestFileId),
          Long.class);
      if (n == null || n == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "The harvesting authority is not on this file.");
      }
    }
    String fileType;
    try {
      fileType = named.queryForObject(
          FILE_TYPE_SQL, Map.of("forestFileId", forestFileId), String.class);
    } catch (EmptyResultDataAccessException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenure not found.");
    }

    String uri = documentUri.trim();
    String hva = hvaSkey == null ? null : hvaSkey.toString();
    String app = Long.toString(tenureAppId);
    // The checks alone, then the issuance itself — as legacy's two passes.
    fail(call(forestFileId, fileType, app, hva, uri, userId, true));
    fail(call(forestFileId, fileType, app, hva, uri, userId, false));
    return new TenureAppIssuePermitResult(
        tenureAppId, "The Permit has been successfully issued.");
  }

  private String call(
      String forestFileId,
      String fileType,
      String tenureAppId,
      String hvaSkey,
      String uri,
      String userId,
      boolean validateOnly) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          cs.setString(1, forestFileId);
          cs.setString(2, fileType);
          cs.setString(3, tenureAppId);
          cs.setString(4, hvaSkey);
          cs.setString(5, uri);
          cs.setString(6, userId);
          cs.setString(7, validateOnly ? "TRUE" : "FALSE");
          cs.registerOutParameter(8, Types.VARCHAR);
          cs.execute();
          return cs.getString(8);
        });
  }

  /** Throws 400 with the package's message (rolling the transaction back) when it gave one. */
  private static void fail(String error) {
    String message = TenureAppLegacyMessages.readable(error);
    if (message != null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
  }
}
