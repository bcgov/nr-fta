package ca.bc.gov.nrs.fta.batch.rangebilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.GetResult;
import ca.bc.gov.nrs.fta.batch.rangebilling.RangeBillingPackage.ProcessResult;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Unit Test | FTA_690_TENURE_APPROVAL calls")
class RangeBillingPackageTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CallableStatement cs = mock(CallableStatement.class);
  private final Connection con = mock(Connection.class);
  private final RangeBillingPackage pkg = new RangeBillingPackage(jdbc);

  @SuppressWarnings("unchecked")
  private void runStatementCallbacks() {
    when(jdbc.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenAnswer(inv -> inv.<CallableStatementCallback<Object>>getArgument(1)
            .doInCallableStatement(cs));
  }

  @SuppressWarnings("unchecked")
  private void runConnectionCallbacks() throws SQLException {
    when(jdbc.execute(any(ConnectionCallback.class)))
        .thenAnswer(inv -> inv.<ConnectionCallback<Object>>getArgument(0).doInConnection(con));
    when(con.getAutoCommit()).thenReturn(true);
    when(con.prepareCall(RangeBillingPackage.PROCESS_CALL)).thenReturn(cs);
  }

  @Test
  void callsTheLegacyMainlineWithAllTenParameters() {
    assertThat(RangeBillingPackage.GET_CALL)
        .contains("the.fta_690_tenure_approval.mainline(v_action, v_year, v_org, v_user, v_sdate,"
            + " v_stime, v_id, v_summ, v_err, v_queue)")
        .contains("v_queue the.fta_690_tenure_approval.queue_cur_t");
    assertThat(RangeBillingPackage.PROCESS_CALL).contains("? := v_summ;").contains("? := v_err;");
  }

  @Test
  void getReadsTheQueue() throws Exception {
    runStatementCallbacks();
    ResultSet rs = mock(ResultSet.class);
    when(cs.getString(3)).thenReturn(null);
    when(cs.getObject(4, ResultSet.class)).thenReturn(rs);
    when(rs.next()).thenReturn(true, false);
    when(rs.getLong("RANGE_BILL_REQUEST_ID")).thenReturn(101L);
    when(rs.getLong("ORG_UNIT_NO")).thenReturn(1840L);
    when(rs.getString("SUBMIT_USERID")).thenReturn("IDIR\\JSMITH");
    when(rs.getInt("CALENDAR_YEAR")).thenReturn(2026);

    GetResult result = pkg.get();

    assertThat(result.errorMessage()).isEmpty();
    assertThat(result.requests())
        .containsExactly(new RangeBillingPackage.QueuedRequest(101L, 1840L, "IDIR\\JSMITH", 2026));
    verify(cs).setString(1, "GET");
    verify(cs).setNull(2, Types.VARCHAR);
  }

  @Test
  void getWithAnErrorDoesNotReadTheCursor() throws Exception {
    runStatementCallbacks();
    when(cs.getString(3)).thenReturn("fta.web.usr.database.unexpected:x;");

    GetResult result = pkg.get();

    assertThat(result.errorMessage()).isEqualTo("fta.web.usr.database.unexpected:x;");
    assertThat(result.requests()).isEmpty();
    verify(cs, never()).getObject(4, ResultSet.class);
  }

  @Test
  void processCommitsWhenThePackageReportsNoError() throws Exception {
    runConnectionCallbacks();
    when(cs.getString(3)).thenReturn("fta411-ApprovalSummary.rpt");
    when(cs.getString(4)).thenReturn(null);

    ProcessResult result = pkg.process(101L);

    assertThat(result).isEqualTo(new ProcessResult("fta411-ApprovalSummary.rpt", ""));
    verify(cs).setString(1, "PROCESS");
    verify(cs).setString(2, "101");
    InOrder order = inOrder(con, cs);
    order.verify(con).setAutoCommit(false);
    order.verify(cs).execute();
    order.verify(con).commit();
    order.verify(con).setAutoCommit(true);
    verify(con, never()).rollback();
  }

  @Test
  void processRollsBackWhenThePackageReportsAnError() throws Exception {
    runConnectionCallbacks();
    when(cs.getString(4)).thenReturn("The specified request was not found!");

    ProcessResult result = pkg.process(101L);

    assertThat(result.errorMessage()).isEqualTo("The specified request was not found!");
    verify(con).rollback();
    verify(con, never()).commit();
    verify(con).setAutoCommit(true);
  }

  @Test
  void processRollsBackWhenTheCallThrows() throws Exception {
    runConnectionCallbacks();
    when(cs.execute()).thenThrow(new SQLException("ORA-04068"));

    assertThatThrownBy(() -> pkg.process(101L)).isInstanceOf(SQLException.class);
    verify(con).rollback();
    verify(con).setAutoCommit(true);
  }

  @Test
  void summaryLowerCasesTheColumns() {
    when(jdbc.queryForList(RangeBillingPackage.SUMMARY_SQL, 101L))
        .thenReturn(List.of(Map.of("ORG_UNIT_CODE", "DCK", "TOTAL_INVOICES", 12)));

    assertThat(pkg.summary(101L))
        .contains(Map.of("org_unit_code", "DCK", "total_invoices", 12));
  }
}
