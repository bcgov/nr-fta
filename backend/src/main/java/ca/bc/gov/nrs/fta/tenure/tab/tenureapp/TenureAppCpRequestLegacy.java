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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Legacy FTA950's own read of the smart-form CP requests:
 * {@code FTA_950X_TEN_APP_CUT_PERM_REQ.MAINLINE('GET', …)}.
 *
 * <p>The package runs with its owner's rights, so it reads {@code CUTTING_PERMIT_REQUEST}, its
 * two code tables ({@code CUTTING_PERMIT_REQUEST_CODE}, {@code CP_REQUEST_STATUS_CODE}) and the
 * rationale document tables ({@code TENURE_PERMIT_DOCUMENT}, {@code CP_EXTERNAL_DOCUMENT_SDW})
 * — none of them granted to the application, which needs only EXECUTE on the package (granted
 * to PUBLIC). Called as legacy did: by file only, every other filter null. The package's
 * {@code col = NVL(:p, col)} filters still drop a request with any of those columns null
 * (notably one not yet accepted), as they did in legacy.
 */
@Component
public class TenureAppCpRequestLegacy {

  /** Binds: 1 forest file id (in); 2 the results cursor, 3 P_ERROR_MESSAGE (out). */
  static final String CALL =
      "DECLARE"
          + " v_action VARCHAR2(10) := 'GET';"
          + " v_file VARCHAR2(100) := ?;"
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
   * The file's requests, newest first (request date and time, then CP).
   *
   * @throws IllegalStateException with the readable text when the package reports an error
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
            String readable = TenureAppLegacyMessages.readable(error);
            throw new IllegalStateException(readable == null ? error.trim() : readable);
          }
          List<Row> rows = new ArrayList<>();
          try (ResultSet rs = cs.getObject(2, ResultSet.class)) {
            while (rs != null && rs.next()) {
              rows.add(row(rs));
            }
          }
          return merge(rows);
        });
  }

  /** One cursor row, with the request date's time kept for ordering. */
  record Row(Timestamp requestedAt, TenureAppCpRequestDto dto) {}

  /*
   * By position: the record (rec_cut_perm_request) names both descriptions DESCRIPTION.
   * 1 guid, 2 hva_skey, 3 forest_file_id, 4 request code, 5 its description, 6 cutting permit,
   * 7 accepted user, 8 accepted date, 9 status code, 10 its description, 11 request date,
   * 12 source uri, 13 rationale detail, 14 rationale document ind, 15 rationale document uri,
   * 16 last status date, then revision count and the audit columns.
   */
  static Row row(ResultSet rs) throws SQLException {
    long hva = rs.getLong(2);
    Long hvaSkey = rs.wasNull() ? null : hva;
    Timestamp requested = rs.getTimestamp(11);
    return new Row(requested, new TenureAppCpRequestDto(
        rs.getString(1),
        hvaSkey,
        rs.getString(6),
        date(requested),
        rs.getString(4),
        rs.getString(5),
        rs.getString(9),
        rs.getString(10),
        rs.getString(7),
        date(rs.getTimestamp(8)),
        rs.getString(13),
        "Y".equals(rs.getString(14))));
  }

  /**
   * One entry per request, newest first. The package outer-joins the request's documents, so a
   * request with two comes back twice; it is listed once, flagged when any row has a document.
   */
  static List<TenureAppCpRequestDto> merge(List<Row> rows) {
    Map<String, Row> byGuid = new LinkedHashMap<>();
    List<Row> unique = new ArrayList<>();
    for (Row r : rows) {
      String guid = r.dto().requestGuid();
      Row seen = guid == null ? null : byGuid.get(guid);
      if (seen == null) {
        if (guid != null) {
          byGuid.put(guid, r);
        }
        unique.add(r);
      } else if (r.dto().rationaleDocument() && !seen.dto().rationaleDocument()) {
        Row flagged = new Row(seen.requestedAt(), withDocument(seen.dto()));
        byGuid.put(guid, flagged);
        unique.set(unique.indexOf(seen), flagged);
      }
    }
    unique.sort(Comparator
        .comparing(Row::requestedAt, Comparator.nullsFirst(Comparator.reverseOrder()))
        .thenComparing(r -> r.dto().cuttingPermitId(),
            Comparator.nullsLast(Comparator.naturalOrder())));
    return unique.stream().map(Row::dto).toList();
  }

  private static TenureAppCpRequestDto withDocument(TenureAppCpRequestDto d) {
    return new TenureAppCpRequestDto(d.requestGuid(), d.hvaSkey(), d.cuttingPermitId(),
        d.requestDate(), d.requestCode(), d.requestDesc(), d.statusCode(), d.statusDesc(),
        d.acceptedUserId(), d.acceptedDate(), d.rationaleDetail(), true);
  }

  private static LocalDate date(Timestamp t) {
    return t == null ? null : t.toLocalDateTime().toLocalDate();
  }
}
