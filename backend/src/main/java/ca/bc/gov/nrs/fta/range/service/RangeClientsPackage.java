package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RelatedClientDto;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Calls legacy {@code THE.FTA_920R_ASSO_CLIENTS_DETAILS.related_client_mainline}, which FTA920R's
 * Get and Save ran ({@code Fta920RRelatedClientBean}):
 *
 * <ul>
 *   <li>{@code GET} — the client's related clients ({@code V_RELATED_CLIENT_PUBLIC}: the client
 *       as A, its partners as B; or the client alone), after the duplicate-licensee and
 *       expired-location checks;
 *   <li>{@code SAVE} — ends and demotes the current licensees and inserts the new ones.
 * </ul>
 *
 * <p>Every parameter is {@code IN OUT VARCHAR2}, bound by name. The package neither commits nor
 * rolls back, so SAVE runs in the caller's transaction (legacy's BasicStore did the same). Its
 * {@code DELETE} action (Delete Current Licensee) is not called: legacy hid that button.
 */
@Component
public class RangeClientsPackage {

  /** GET: the related clients, and the package's messages. */
  public record Related(List<RelatedClientDto> rows, String errorMessage) {}

  private static final String DECLARE =
      "DECLARE"
          + " v_action VARCHAR2(10) := ?;"
          + " v_file VARCHAR2(4000) := ?;"
          + " v_number VARCHAR2(4000) := ?;"
          + " v_locn VARCHAR2(4000) := ?;"
          + " v_start VARCHAR2(4000) := ?;"
          + " v_end VARCHAR2(4000) := ?;"
          + " v_save_ok VARCHAR2(10) := ?;"
          + " v_user VARCHAR2(4000) := ?;"
          + " v_err VARCHAR2(32767);"
          + " v_results the.fta_920r_asso_clients_details.ref_related_clients_results;"
          + " BEGIN"
          + " the.fta_920r_asso_clients_details.related_client_mainline("
          + "p_action => v_action, p_forest_file_id => v_file,"
          + " p_related_client_number => v_number, p_related_client_locn_code => v_locn,"
          + " p_new_licensee_start_dt => v_start, p_prev_licensee_end_date => v_end,"
          + " p_save_ok => v_save_ok, p_update_userid => v_user, p_error_message => v_err,"
          + " p_related_clients_results => v_results);";

  /**
   * GET opens its cursor only when there is no error; an empty one stands in otherwise, so the
   * driver never sees an unopened cursor.
   */
  static final String GET_CALL = DECLARE.replace(" BEGIN", " v_out SYS_REFCURSOR; BEGIN")
      + " ? := v_err;"
      + " IF v_results%ISOPEN THEN v_out := v_results;"
      + " ELSE OPEN v_out FOR SELECT NULL FROM dual WHERE 1 = 0; END IF;"
      + " ? := v_out;"
      + " END;";

  static final String SAVE_CALL = DECLARE + " ? := v_err; END;";

  private final JdbcTemplate jdbc;

  // Its JdbcTemplate shares the transaction-bound connection, so SAVE joins the caller's
  // transaction.
  public RangeClientsPackage(NamedParameterJdbcTemplate named) {
    this.jdbc = named.getJdbcTemplate();
  }

  /** GET for this client and location on the file. */
  public Related getRelated(String forestFileId, String clientNumber, String clientLocnCode) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(GET_CALL),
        (CallableStatement cs) -> {
          bind(cs, "GET", forestFileId, clientNumber, clientLocnCode, null, null, null, null);
          cs.registerOutParameter(9, Types.VARCHAR);
          cs.registerOutParameter(10, Types.REF_CURSOR);
          cs.execute();
          String error = cs.getString(9);
          List<RelatedClientDto> rows = new ArrayList<>();
          if (error == null || error.isBlank()) {
            try (ResultSet rs = cs.getObject(10, ResultSet.class)) {
              while (rs != null && rs.next()) {
                // rec_related_clients_results, by position (its email column is aliased
                // differently in the two halves of the UNION).
                rows.add(new RelatedClientDto(rs.getString(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getString(8)));
              }
            }
          }
          return new Related(rows, error == null ? "" : error);
        });
  }

  /**
   * SAVE: makes this client (and its partners) the file's licensees.
   *
   * @param startDate       YYYY-MM-DD
   * @param previousEndDate YYYY-MM-DD, or null for the start date
   * @return the package's messages, blank when it saved
   */
  public String save(String forestFileId, String clientNumber, String clientLocnCode,
      String startDate, String previousEndDate, String userId) {
    return jdbc.execute(
        (java.sql.Connection con) -> con.prepareCall(SAVE_CALL),
        (CallableStatement cs) -> {
          // p_save_ok Y: the caller has already checked what legacy's GET put there.
          bind(cs, "SAVE", forestFileId, clientNumber, clientLocnCode, startDate,
              previousEndDate, "Y", userId);
          cs.registerOutParameter(9, Types.VARCHAR);
          cs.execute();
          String error = cs.getString(9);
          return error == null ? "" : error;
        });
  }

  private static void bind(CallableStatement cs, String... values) throws java.sql.SQLException {
    for (int i = 0; i < values.length; i++) {
      String v = values[i];
      cs.setString(i + 1, v == null || v.isEmpty() ? null : v);
    }
  }
}
