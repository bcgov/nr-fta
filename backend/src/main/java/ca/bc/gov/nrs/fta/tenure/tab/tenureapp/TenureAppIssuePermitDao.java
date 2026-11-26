package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The database side of Issue Permit: the application's state, the row's authority and file
 * type, and legacy's {@code FTA_950X_TEN_APP_ISSUE_PERMIT.mainline('ISSUE_PERMIT', …)}.
 */
@Repository
public class TenureAppIssuePermitDao {

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

  public TenureAppIssuePermitDao(NamedParameterJdbcTemplate named) {
    this.named = named;
    this.jdbc = named.getJdbcTemplate();
  }

  /** The application's state code, when it is on the file (directly or by a map feature). */
  public Optional<String> applicationState(String forestFileId, long tenureAppId) {
    List<String> states = named.queryForList(
        APPLICATION_SQL,
        new MapSqlParameterSource()
            .addValue("forestFileId", forestFileId)
            .addValue("tenureAppId", tenureAppId, Types.NUMERIC),
        String.class);
    return states.stream().findFirst();
  }

  /** Whether the harvesting authority is on the file. */
  public boolean hvaOnFile(String forestFileId, long hvaSkey) {
    Long n = named.queryForObject(
        HVA_ON_FILE_SQL,
        new MapSqlParameterSource()
            .addValue("hvaSkey", hvaSkey, Types.NUMERIC)
            .addValue("forestFileId", forestFileId),
        Long.class);
    return n != null && n > 0;
  }

  /** The file's type code, when the file exists. */
  public Optional<String> fileType(String forestFileId) {
    return named.queryForList(FILE_TYPE_SQL, Map.of("forestFileId", forestFileId), String.class)
        .stream()
        .findFirst();
  }

  /**
   * One ISSUE_PERMIT pass: the checks only ({@code validateOnly}), or the checks and the
   * issuance recording {@code documentUri}.
   *
   * @return the package's {@code p_error_message} (blank when it passed)
   */
  public String issuePermit(
      String forestFileId,
      String fileType,
      long tenureAppId,
      Long hvaSkey,
      String documentUri,
      String userId,
      boolean validateOnly) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          cs.setString(1, forestFileId);
          cs.setString(2, fileType);
          cs.setString(3, Long.toString(tenureAppId));
          cs.setString(4, hvaSkey == null ? null : hvaSkey.toString());
          cs.setString(5, documentUri);
          cs.setString(6, userId);
          cs.setString(7, validateOnly ? "TRUE" : "FALSE");
          cs.registerOutParameter(8, Types.VARCHAR);
          cs.execute();
          return cs.getString(8);
        });
  }
}
