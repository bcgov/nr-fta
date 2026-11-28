package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppProfDecDto;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Legacy FTA950's Prof Dec popup read: {@code FTA_950X_TEN_APP_PROF_DEC.MAINLINE('GET', …)}.
 *
 * <p>The package runs with its owner's rights, so it reads {@code PROFESSIONAL_PARTY} and
 * {@code DECLARATION_TYPE_CODE} (not granted to the application) — it needs only EXECUTE on the
 * package, granted to PUBLIC. Its client name is {@code SIL_GET_CLIENT_NAME}, the function
 * {@code ClientNameSql.displayName} stands in for elsewhere.
 *
 * <p>Legacy passed the row's submission id as well; the application id is the key, so it is left
 * null here (the package's {@code CONVERT_TO_NUMBER(null, col)} filter keeps every row with a
 * submission id, as every ESF application has).
 */
@Component
public class TenureAppProfDecLegacy {

  /**
   * Binds: 1 tenure app id, 2 forest file id, 3 cutting permit id, 4 hva skey (in); 5 the results
   * cursor, 6 P_ERROR_MESSAGE (out).
   */
  static final String CALL =
      "DECLARE"
          + " v_action VARCHAR2(10) := 'GET';"
          + " v_app VARCHAR2(30) := ?;"
          + " v_file VARCHAR2(100) := ?;"
          + " v_sub VARCHAR2(30);"
          + " v_cp VARCHAR2(30) := ?;"
          + " v_hva VARCHAR2(30) := ?;"
          + " v_err VARCHAR2(4000);"
          + " v_results the.fta_950x_ten_app_prof_dec.ref_cut_perm_prof_dec;"
          + " BEGIN"
          + " the.fta_950x_ten_app_prof_dec.mainline(v_action, v_app, v_file, v_sub, v_cp,"
          + " v_hva, v_err, v_results);"
          + " ? := v_results; ? := v_err;"
          + " END;";

  private final JdbcTemplate jdbc;

  public TenureAppProfDecLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * The declarations on one application of the file, newest first.
   *
   * @param cuttingPermitId the row's CP (for the cut block list), or null
   * @param hvaSkey         the row's harvesting authority (for the cut block list), or null
   * @throws IllegalStateException with the readable text when the package reports an error
   */
  public List<TenureAppProfDecDto> declarations(
      String forestFileId, long tenureAppId, String cuttingPermitId, Long hvaSkey) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          cs.setString(1, Long.toString(tenureAppId));
          cs.setString(2, forestFileId);
          cs.setString(3, cuttingPermitId);
          cs.setString(4, hvaSkey == null ? null : hvaSkey.toString());
          cs.registerOutParameter(5, Types.REF_CURSOR);
          cs.registerOutParameter(6, Types.VARCHAR);
          cs.execute();
          String error = cs.getString(6);
          if (error != null && !error.isBlank()) {
            String readable = TenureAppLegacyMessages.readable(error);
            throw new IllegalStateException(readable == null ? error.trim() : readable);
          }
          List<Row> rows = new ArrayList<>();
          try (ResultSet rs = cs.getObject(5, ResultSet.class)) {
            while (rs != null && rs.next()) {
              rows.add(row(rs));
            }
          }
          // The package has no ORDER BY; newest first, nulls first as Oracle's DESC sorts them.
          rows.sort(Comparator.comparing(Row::declaredAt,
              Comparator.nullsFirst(Comparator.reverseOrder())));
          return rows.stream().map(Row::dto).toList();
        });
  }

  /** One cursor row, with the declaration date's time kept for ordering. */
  record Row(Timestamp declaredAt, TenureAppProfDecDto dto) {}

  /*
   * By position (rec_cut_perm_prof_dec): 1 tenure app id, 2 forest file id, 3 submission id,
   * 4 declaration guid, 5 declaration type code, 6 its description, 7 declaration date,
   * 8 client number, 9 client location, 10 client name, 11 declarant comments,
   * 12 revision count, 13-16 declaration audit, 17 professional party guid,
   * 18 professional identifier code, 19 "LAST, FIRST MIDDLE", 20 certification reference,
   * 21 phone, 22 e-mail, 23 web site, 24 effective, 25 expiry, 26-29 party audit,
   * 30 cut block ids.
   */
  static Row row(ResultSet rs) throws SQLException {
    Timestamp declared = rs.getTimestamp(7);
    return new Row(declared, new TenureAppProfDecDto(
        number(rs, 1),
        rs.getString(2),
        number(rs, 3),
        rs.getString(5),
        rs.getString(6),
        declared == null ? null : declared.toLocalDateTime().toLocalDate(),
        rs.getString(8),
        rs.getString(9),
        rs.getString(10),
        trimToNull(rs.getString(19)),
        rs.getString(20),
        rs.getString(18),
        rs.getString(21),
        rs.getString(22),
        rs.getString(23),
        rs.getString(11),
        rs.getString(30)));
  }

  private static Long number(ResultSet rs, int column) throws SQLException {
    long v = rs.getLong(column);
    return rs.wasNull() ? null : v;
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() || ",".equals(t) ? null : t;
  }
}
