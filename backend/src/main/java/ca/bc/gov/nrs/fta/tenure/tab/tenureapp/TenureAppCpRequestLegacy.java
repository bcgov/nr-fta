package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppCpRequestDto;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Legacy FTA950's own read of the smart-form CP requests:
 * {@code FTA_950X_TEN_APP_CUT_PERM_REQ.MAINLINE('GET', …)}.
 *
 * <p>The package runs with its owner's rights, so it reads {@code CUTTING_PERMIT_REQUEST} (and
 * its code and document tables) on databases where the application's account has no grant on
 * them — it needs only EXECUTE on the package. Called as legacy did: by file only, every other
 * filter null.
 */
@Component
public class TenureAppCpRequestLegacy {

  private static final String CALL =
      "DECLARE"
          + " v_action VARCHAR2(10) := 'GET';"
          + " v_file VARCHAR2(10) := ?;"
          + " v_hva VARCHAR2(30); v_cp VARCHAR2(30); v_req_date VARCHAR2(30);"
          + " v_req_code VARCHAR2(30); v_status VARCHAR2(30); v_user VARCHAR2(30);"
          + " v_acc_date VARCHAR2(30); v_err VARCHAR2(4000);"
          + " v_results the.fta_950x_ten_app_cut_perm_req.ref_cut_perm_request;"
          + " BEGIN"
          + " the.fta_950x_ten_app_cut_perm_req.mainline(v_action, v_file, v_hva, v_cp,"
          + " v_req_date, v_req_code, v_status, v_user, v_acc_date, v_err, v_results);"
          + " ? := v_results; ? := v_err;"
          + " END;";

  private final JdbcTemplate jdbc;

  public TenureAppCpRequestLegacy(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /**
   * The file's requests, newest first.
   *
   * @throws IllegalStateException when the package reports an error
   */
  public List<TenureAppCpRequestDto> requests(String forestFileId) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(CALL),
        (CallableStatement cs) -> {
          cs.setString(1, forestFileId);
          cs.registerOutParameter(2, Types.REF_CURSOR);
          cs.registerOutParameter(3, Types.VARCHAR);
          cs.execute();
          String error = cs.getString(3);
          if (error != null && !error.isBlank()) {
            throw new IllegalStateException(error.trim());
          }
          List<TenureAppCpRequestDto> out = new ArrayList<>();
          try (ResultSet rs = cs.getObject(2, ResultSet.class)) {
            while (rs != null && rs.next()) {
              out.add(row(rs));
            }
          }
          out.sort(Comparator
              .comparing(TenureAppCpRequestDto::requestDate,
                  Comparator.nullsLast(Comparator.reverseOrder()))
              .thenComparing(TenureAppCpRequestDto::cuttingPermitId,
                  Comparator.nullsLast(Comparator.naturalOrder())));
          return out;
        });
  }

  /*
   * By position: the record (rec_cut_perm_request) names both descriptions DESCRIPTION.
   * 1 guid, 2 hva_skey, 3 forest_file_id, 4 request code, 5 its description, 6 cutting permit,
   * 7 accepted user, 8 accepted date, 9 status code, 10 its description, 11 request date,
   * 12 source uri, 13 rationale detail, 14 rationale document ind, ...
   */
  private static TenureAppCpRequestDto row(ResultSet rs) throws SQLException {
    long hva = rs.getLong(2);
    Long hvaSkey = rs.wasNull() ? null : hva;
    return new TenureAppCpRequestDto(
        rs.getString(1),
        hvaSkey,
        rs.getString(6),
        date(rs.getTimestamp(11)),
        rs.getString(4),
        rs.getString(5),
        rs.getString(9),
        rs.getString(10),
        rs.getString(7),
        date(rs.getTimestamp(8)),
        rs.getString(13),
        "Y".equals(rs.getString(14)));
  }

  private static LocalDate date(Timestamp t) {
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }
}
