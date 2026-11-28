package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppProfDecDto;
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

@DisplayName("Unit Test | TenureAppProfDecLegacy (FTA_950X_TEN_APP_PROF_DEC.MAINLINE GET)")
class TenureAppProfDecLegacyTest {

  private CallableStatement cs;
  private ResultSet rs;
  private TenureAppProfDecLegacy legacy;

  @BeforeEach
  void setUp() throws Exception {
    DataSource ds = mock(DataSource.class);
    Connection con = mock(Connection.class);
    cs = mock(CallableStatement.class);
    rs = mock(ResultSet.class);
    when(ds.getConnection()).thenReturn(con);
    when(con.prepareCall(anyString())).thenReturn(cs);
    when(cs.getObject(5, ResultSet.class)).thenReturn(rs);
    legacy = new TenureAppProfDecLegacy(new NamedParameterJdbcTemplate(ds));
  }

  @Test
  void theCallIsLegacysGet() {
    assertThat(TenureAppProfDecLegacy.CALL)
        .contains("v_action VARCHAR2(10) := 'GET';")
        .contains("the.fta_950x_ten_app_prof_dec.mainline(v_action, v_app, v_file, v_sub, v_cp,"
            + " v_hva, v_err, v_results);")
        .contains("v_results the.fta_950x_ten_app_prof_dec.ref_cut_perm_prof_dec;")
        .contains("? := v_results; ? := v_err;")
        .doesNotContainIgnoringCase("professional_party");
    assertThat(TenureAppProfDecLegacy.CALL.chars().filter(c -> c == '?').count()).isEqualTo(6);
  }

  @Test
  void bindsInLegacyOrderAndMapsTheCursorByPosition() throws Exception {
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong(1)).thenReturn(77L);
    when(rs.getString(2)).thenReturn("A12345");
    when(rs.getLong(3)).thenReturn(900L);
    when(rs.getString(5)).thenReturn("RPF");
    when(rs.getString(6)).thenReturn("Registered Professional Forester");
    when(rs.getTimestamp(7)).thenReturn(at(2024, 2, 1, 13));
    when(rs.getString(8)).thenReturn("00001234");
    when(rs.getString(9)).thenReturn("00");
    when(rs.getString(10)).thenReturn("ACME LOGGING");
    when(rs.getString(11)).thenReturn("all good");
    when(rs.getString(18)).thenReturn("RPF");
    when(rs.getString(19)).thenReturn("SMITH, JANE  ");
    when(rs.getString(20)).thenReturn("1234");
    when(rs.getString(21)).thenReturn("2505551234");
    when(rs.getString(22)).thenReturn("jane@example.com");
    when(rs.getString(23)).thenReturn("example.com");
    when(rs.getString(30)).thenReturn("1, 2");

    List<TenureAppProfDecDto> out = legacy.declarations("A12345", 77L, "12", 42L);

    InOrder o = inOrder(cs);
    o.verify(cs).setString(1, "77");
    o.verify(cs).setString(2, "A12345");
    o.verify(cs).setString(3, "12");
    o.verify(cs).setString(4, "42");
    o.verify(cs).registerOutParameter(5, Types.REF_CURSOR);
    o.verify(cs).registerOutParameter(6, Types.VARCHAR);
    o.verify(cs).execute();
    assertThat(out).containsExactly(new TenureAppProfDecDto(
        77L, "A12345", 900L, "RPF", "Registered Professional Forester",
        LocalDate.of(2024, 2, 1), "00001234", "00", "ACME LOGGING", "SMITH, JANE", "1234",
        "RPF", "2505551234", "jane@example.com", "example.com", "all good", "1, 2"));
  }

  @Test
  void aMissingHvaOrCpIsBoundNull() throws Exception {
    when(rs.next()).thenReturn(false);
    assertThat(legacy.declarations("A1", 5L, null, null)).isEmpty();
    verify(cs).setString(3, null);
    verify(cs).setString(4, null);
  }

  @Test
  void anEmptyDeclarantNameIsNull() throws Exception {
    when(rs.next()).thenReturn(true, false);
    when(rs.getString(19)).thenReturn(",  ");
    assertThat(legacy.declarations("A1", 5L, null, null).get(0).declarantName()).isNull();
  }

  @Test
  void newestDeclarationFirst() throws Exception {
    when(rs.next()).thenReturn(true, true, false);
    when(rs.getTimestamp(7)).thenReturn(at(2023, 1, 1, 8), at(2024, 1, 1, 8));
    when(rs.getString(5)).thenReturn("OLD", "NEW");
    assertThat(legacy.declarations("A1", 5L, null, null))
        .extracting(TenureAppProfDecDto::declarationTypeCode)
        .containsExactly("NEW", "OLD");
  }

  @Test
  void anErrorMessageFailsWithItsTextAndSkipsTheCursor() throws Exception {
    when(cs.getString(6)).thenReturn(
        "fta.web.error.user.custom.msg:FTA_950X_TEN_APP_PROF_DEC,GET(line 60),-1722,"
            + "ORA-01722: invalid number;");
    assertThatThrownBy(() -> legacy.declarations("A1", 5L, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ORA-01722")
        .hasMessageNotContaining("fta.web.error.user.custom.msg");
    verify(cs, never()).getObject(anyInt(), org.mockito.ArgumentMatchers.eq(ResultSet.class));
  }

  private static Timestamp at(int y, int m, int d, int h) {
    return Timestamp.valueOf(LocalDateTime.of(y, m, d, h, 0));
  }
}
