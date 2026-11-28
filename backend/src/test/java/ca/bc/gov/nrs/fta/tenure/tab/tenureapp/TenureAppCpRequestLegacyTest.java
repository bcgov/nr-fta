package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppCpRequestDto;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | TenureAppCpRequestLegacy (FTA_950X_TEN_APP_CUT_PERM_REQ.MAINLINE GET)")
class TenureAppCpRequestLegacyTest {

  private CallableStatement cs;
  private ResultSet rs;
  private TenureAppCpRequestLegacy legacy;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds = mock(DataSource.class);
    Connection con = mock(Connection.class);
    cs = mock(CallableStatement.class);
    rs = mock(ResultSet.class);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareCall(anyString())).thenReturn(cs);
    when(cs.getObject(2, ResultSet.class)).thenReturn(rs);
    legacy = new TenureAppCpRequestLegacy(new NamedParameterJdbcTemplate(ds));
  }

  @Test
  void theCallIsLegacysGetByFileOnly() {
    assertThat(TenureAppCpRequestLegacy.CALL)
        .contains("v_action VARCHAR2(10) := 'GET';")
        .contains("the.fta_950x_ten_app_cut_perm_req.mainline(v_action, v_file, v_hva, v_cp,"
            + " v_req_date, v_req_code, v_status, v_user, v_acc_date, v_err, v_results);")
        .contains("? := v_results; ? := v_err;")
        .doesNotContainIgnoringCase("cutting_permit_request cpr");
    assertThat(TenureAppCpRequestLegacy.CALL.chars().filter(c -> c == '?').count()).isEqualTo(3);
  }

  @Test
  void bindsTheFileAndMapsTheCursorByPosition() throws Exception {
    when(rs.next()).thenReturn(true, false);
    stubRow("G1", 42L, "12", at(2024, 3, 5, 10), "Y");
    when(rs.getString(4)).thenReturn("NEW");
    when(rs.getString(5)).thenReturn("New cutting permit");
    when(rs.getString(7)).thenReturn("IDIR\\ME");
    when(rs.getTimestamp(8)).thenReturn(at(2024, 3, 6, 9));
    when(rs.getString(9)).thenReturn("ACC");
    when(rs.getString(10)).thenReturn("Accepted");
    when(rs.getString(13)).thenReturn("because");

    List<TenureAppCpRequestDto> out = legacy.requests("A12345");

    InOrder o = inOrder(cs);
    o.verify(cs).setString(1, "A12345");
    o.verify(cs).registerOutParameter(2, Types.REF_CURSOR);
    o.verify(cs).registerOutParameter(3, Types.VARCHAR);
    o.verify(cs).execute();
    assertThat(out).containsExactly(new TenureAppCpRequestDto(
        "G1", 42L, "12", LocalDate.of(2024, 3, 5), "NEW", "New cutting permit", "ACC",
        "Accepted", "IDIR\\ME", LocalDate.of(2024, 3, 6), "because", true));
  }

  @Test
  void aNullHvaStaysNull() throws Exception {
    when(rs.next()).thenReturn(true, false);
    stubRow("G1", 0L, "12", at(2024, 3, 5, 10), "N");
    when(rs.wasNull()).thenReturn(true);
    assertThat(legacy.requests("A1").get(0).hvaSkey()).isNull();
  }

  @Test
  void anErrorMessageFailsWithItsTextAndSkipsTheCursor() throws Exception {
    when(cs.getString(3)).thenReturn(
        "fta.web.error.user.custom.msg:FTA_950X_TEN_APP_CUT_PERM_REQ,GET(line 9),-942,"
            + "ORA-00942: table or view does not exist;");
    assertThatThrownBy(() -> legacy.requests("A1"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ORA-00942")
        .hasMessageNotContaining("fta.web.error.user.custom.msg");
    verify(cs, never()).getObject(2, ResultSet.class);
  }

  @Test
  void aRequestWithTwoDocumentsIsListedOnceAndFlagged() {
    var noDoc = row("G1", "12", at(2024, 1, 1, 8), false);
    var withDoc = row("G1", "12", at(2024, 1, 1, 8), true);
    assertThat(TenureAppCpRequestLegacy.merge(List.of(noDoc, withDoc)))
        .singleElement()
        .satisfies(d -> assertThat(d.rationaleDocument()).isTrue());
  }

  @Test
  void newestRequestFirstByTimeThenCp() {
    var older = row("G1", "10", at(2024, 1, 1, 8), false);
    var laterSameDay = row("G2", "11", at(2024, 1, 1, 15), false);
    var sameTimeLowerCp = row("G3", "09", at(2024, 1, 1, 15), false);
    assertThat(TenureAppCpRequestLegacy.merge(List.of(older, laterSameDay, sameTimeLowerCp)))
        .extracting(TenureAppCpRequestDto::requestGuid)
        .containsExactly("G3", "G2", "G1");
  }

  private void stubRow(String guid, long hva, String cp, Timestamp requested, String docInd)
      throws Exception {
    when(rs.getString(1)).thenReturn(guid);
    when(rs.getLong(2)).thenReturn(hva);
    when(rs.getString(6)).thenReturn(cp);
    when(rs.getTimestamp(11)).thenReturn(requested);
    when(rs.getString(14)).thenReturn(docInd);
  }

  private static TenureAppCpRequestLegacy.Row row(
      String guid, String cp, Timestamp at, boolean doc) {
    return new TenureAppCpRequestLegacy.Row(at, new TenureAppCpRequestDto(guid, 1L, cp,
        at.toLocalDateTime().toLocalDate(), "NEW", null, "ACC", null, null, null, null, doc));
  }

  private static Timestamp at(int y, int m, int d, int h) {
    return Timestamp.valueOf(LocalDateTime.of(y, m, d, h, 0));
  }
}
