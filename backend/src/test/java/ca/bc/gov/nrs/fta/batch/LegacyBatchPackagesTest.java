package ca.bc.gov.nrs.fta.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.batch.cancelmark.CancelMarkPackage;
import java.sql.CallableStatement;
import java.sql.Types;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Unit Test | legacy batch package calls (520)")
class LegacyBatchPackagesTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final CallableStatement cs = mock(CallableStatement.class);

  /** Runs whatever callback the package hands JdbcTemplate against the mock statement. */
  @SuppressWarnings("unchecked")
  private void runCallbacks() {
    when(jdbc.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenAnswer(inv -> inv.<CallableStatementCallback<Object>>getArgument(1)
            .doInCallableStatement(cs));
  }

  @Test
  void cancelMarkPassesNullTraceAndReturnsTheError() throws Exception {
    runCallbacks();
    when(cs.getString(2)).thenReturn(" fta.web.usr.database.unexpected:boom; ");

    String error = new CancelMarkPackage(jdbc).mainline();

    assertThat(error).isEqualTo("fta.web.usr.database.unexpected:boom;");
    verify(cs).setNull(1, Types.VARCHAR);
    verify(cs).setNull(2, Types.VARCHAR);
    verify(cs).registerOutParameter(2, Types.VARCHAR);
    verify(cs).execute();
  }

  @Test
  void cancelMarkCallsTheLegacyPackage() {
    assertThat(callOf(CancelMarkPackage.class, "CALL"))
        .contains("the.fta_520_cancel_mark.mainline(p_trace_ind => ?, p_error_message => ?)");
  }

  private static String callOf(Class<?> type, String field) {
    try {
      var f = type.getDeclaredField(field);
      f.setAccessible(true);
      return (String) f.get(null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
