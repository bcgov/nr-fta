package ca.bc.gov.nrs.fta.tenure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * How FTA008 calls {@code FTA_008_METRICS_EXTRACT.mainline}: the block, the binds, GET_METRICS
 * then GET_HOLDS on one connection, each cursor read by column name, an error skipping the cursor,
 * and the rollback that leaves the temporary table empty.
 */
@DisplayName("Unit Test | FTA008 application metrics package")
class ApplicationMetricsPackageTest {

  private static final List<String> ARGS = Arrays.asList("1835", "2025-01-01", "2025-12-31",
      null, null, null, null, null, null, null, "2025-06-30");

  private final NamedParameterJdbcTemplate named = mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final Connection con = mock(Connection.class);
  private final CallableStatement metricsCall = mock(CallableStatement.class);
  private final CallableStatement holdsCall = mock(CallableStatement.class);
  private ApplicationMetricsPackage pkg;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    when(named.getJdbcTemplate()).thenReturn(jdbc);
    when(jdbc.execute(any(ConnectionCallback.class))).thenAnswer(
        inv -> ((ConnectionCallback<Object>) inv.getArgument(0)).doInConnection(con));
    when(con.getAutoCommit()).thenReturn(true);
    when(con.prepareCall(ApplicationMetricsPackage.CALL)).thenReturn(metricsCall, holdsCall);
    pkg = new ApplicationMetricsPackage(named);
  }

  /** A cursor over these rows, read by column name. */
  private static ResultSet cursor(List<Map<String, String>> rows) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    int[] at = {-1};
    when(rs.next()).thenAnswer(inv -> ++at[0] < rows.size());
    when(rs.getString(anyString()))
        .thenAnswer(inv -> rows.get(at[0]).get(inv.<String>getArgument(0)));
    return rs;
  }

  @Test
  void callNamesMainlineWithEveryParameterByName() {
    String call = ApplicationMetricsPackage.CALL;
    assertThat(call).contains("the.fta_008_metrics_extract.mainline(")
        .contains("p_action => v_action")
        .contains("p_admin_org_unit_no => v_org")
        .contains("p_issuance_date_to => v_iss_to")
        .contains("p_harv_auth_metrics_id => v_metrics_id")
        .contains("p_metrics_rec_cursor => v_cur")
        .contains("IF v_cur%ISOPEN");
    assertThat(call.chars().filter(ch -> ch == '?').count()).isEqualTo(14);
  }

  @Test
  void bindsTheActionThenTheCriteriaThenTheOutputs() throws Exception {
    CallableStatement cs = mock(CallableStatement.class);
    ApplicationMetricsPackage.bind(cs, "GET_HOLDS", ARGS);
    verify(cs).setString(1, "GET_HOLDS");
    verify(cs).setString(2, "1835");
    verify(cs).setString(3, "2025-01-01");
    verify(cs).setString(4, "2025-12-31");
    verify(cs).setString(5, null);
    verify(cs).setString(12, "2025-06-30");
    verify(cs).registerOutParameter(13, Types.VARCHAR);
    verify(cs).registerOutParameter(14, Types.REF_CURSOR);
    verify(cs, never()).setString(eq(13), any());
  }

  @Test
  void bindRefusesTheWrongNumberOfCriteria() {
    CallableStatement cs = mock(CallableStatement.class);
    assertThatThrownBy(() -> ApplicationMetricsPackage.bind(cs, "GET_METRICS", List.of("1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void runsMetricsThenHoldsAndReadsEachSheetsColumnsInOrder() throws Exception {
    Map<String, String> metric = new java.util.HashMap<>();
    for (ApplicationMetricsRules.Column c : ApplicationMetricsRules.METRICS_COLUMNS) {
      metric.put(c.name(), "m_" + c.name());
    }
    metric.put("LICENSEE", null);
    ResultSet metrics = cursor(List.of(metric));
    when(metricsCall.getObject(14, ResultSet.class)).thenReturn(metrics);

    Map<String, String> hold = Map.of("TENURE_ID", "A12345", "CUTTING_PERMIT_ID", "01",
        "HOLD_START_DATE", "2025-01-02", "HOLD_END_DATE", "2025-01-09",
        "HOLD_REASON", "First Nations", "HOLD_NOTES", "notes", "NUM_DAYS_ON_HOLD", "7");
    ResultSet holds = cursor(List.of(hold, hold));
    when(holdsCall.getObject(14, ResultSet.class)).thenReturn(holds);

    ApplicationMetricsPackage.Extract out = pkg.extract(ARGS);

    verify(metricsCall).setString(1, "GET_METRICS");
    verify(holdsCall).setString(1, "GET_HOLDS");
    assertThat(out.errorMessage()).isEmpty();
    assertThat(out.metrics()).hasSize(1);
    List<String> expected = new ArrayList<>();
    for (ApplicationMetricsRules.Column c : ApplicationMetricsRules.METRICS_COLUMNS) {
      expected.add("LICENSEE".equals(c.name()) ? null : "m_" + c.name());
    }
    assertThat(out.metrics().get(0)).containsExactlyElementsOf(expected);
    assertThat(out.holds()).hasSize(2);
    assertThat(out.holds().get(0)).containsExactly(
        "A12345", "01", "2025-01-02", "2025-01-09", "First Nations", "notes", "7");

    InOrder order = inOrder(con, metricsCall, metrics, holdsCall);
    order.verify(con).setAutoCommit(false);
    order.verify(metricsCall).execute();
    order.verify(metrics).close();
    order.verify(holdsCall).execute();
    order.verify(con).rollback();
    order.verify(con).setAutoCommit(true);
    verify(metricsCall).close();
    verify(holdsCall).close();
  }

  @Test
  void anErrorSkipsThatCursorAndIsReturned() throws Exception {
    when(metricsCall.getString(13)).thenReturn(
        "sil.web.usr.database.unexpected:FTA_008_METRICS_EXTRACT,GET_METRICS,-1,boom;");
    ResultSet noHolds = cursor(List.of());
    when(holdsCall.getObject(14, ResultSet.class)).thenReturn(noHolds);

    ApplicationMetricsPackage.Extract out = pkg.extract(ARGS);

    verify(metricsCall, never()).getObject(14, ResultSet.class);
    assertThat(out.metrics()).isEmpty();
    assertThat(out.holds()).isEmpty();
    assertThat(out.errorMessage()).startsWith("sil.web.usr.database.unexpected:");
    verify(con).rollback();
  }

  @Test
  void aFailedCallStillRollsBackAndRestoresAutoCommit() throws Exception {
    when(metricsCall.execute()).thenThrow(new SQLException("ORA-06550"));

    assertThatThrownBy(() -> pkg.extract(ARGS)).isInstanceOf(SQLException.class);

    verify(con).rollback();
    verify(con).setAutoCommit(true);
    verify(holdsCall, never()).execute();
  }
}
