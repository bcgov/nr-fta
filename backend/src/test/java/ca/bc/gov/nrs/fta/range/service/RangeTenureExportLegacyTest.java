package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * How the FTA001 export calls {@code FTA_001R_TENR_SRCH_CSV1..10}: the call text, the order
 * the 26 inputs bind in, and the checks and escaping that stand between the user and the
 * procedures' dynamic SQL.
 */
@DisplayName("Unit Test | FTA001 range tenure export procedures")
class RangeTenureExportLegacyTest {

  private static RangeTenureExportCriteria all() {
    return new RangeTenureExportCriteria(
        " ran07 ", "E01", "1835", "Z1", "O'BRIEN", "00012345", "00", "A", "A", "TSA", "01",
        "2020-01-01", "2020-12-31", "2025-01-01", "2025-12-31", "2026", "10", "20.5", "1", "2",
        "3", "4", "5", "6", "7", "8");
  }

  @Test
  void callNamesEachProcedureWithTwentySevenBinds() {
    assertThat(RangeTenureExportLegacy.call(1)).isEqualTo(
        "{call the.fta_001r_tenr_srch_csv1(" + "?, ".repeat(26) + "?)}");
    assertThat(RangeTenureExportLegacy.call(10)).startsWith("{call the.fta_001r_tenr_srch_csv10(");
  }

  @Test
  void bindsInputsInProcedureOrderThenTheCursor() throws Exception {
    CallableStatement cs = mock(CallableStatement.class);
    RangeTenureExportLegacy.bind(cs, all().procedureArguments());

    verify(cs).setString(1, "RAN07");
    verify(cs).setString(2, "E01");
    verify(cs).setString(3, "1835");
    verify(cs).setString(4, "Z1");
    verify(cs).setString(5, "O''BRIEN");
    verify(cs).setString(6, "00012345");
    verify(cs).setString(9, "A");
    verify(cs).setString(10, "TSA");
    verify(cs).setString(12, "2020-01-01");
    verify(cs).setString(15, "2025-12-31");
    verify(cs).setString(16, "2026");
    verify(cs).setString(18, "20.5");
    verify(cs).setString(26, "8");
    verify(cs).registerOutParameter(27, Types.REF_CURSOR);
    verify(cs, never()).setString(eq(27), anyString());
  }

  @Test
  void blanksBindAsNull() {
    List<String> args = new RangeTenureExportCriteria(" ", "", null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null).procedureArguments();
    assertThat(args).hasSize(26).containsOnlyNulls();
  }

  @Test
  void forFilePassesOnlyTheFileAndItsAdminOrg() {
    List<String> args = RangeTenureExportCriteria.forFile("RAN077777", "1835")
        .procedureArguments();
    assertThat(args.get(0)).isEqualTo("RAN077777");
    assertThat(args.get(2)).isEqualTo("1835");
    assertThat(args.subList(3, 26)).containsOnlyNulls();
    assertThat(args.get(1)).isNull();
  }

  @Test
  void quotesInSplicedTextAreDoubled() {
    List<String> args = new RangeTenureExportCriteria(null, "E'01", null, "Z' OR '1'='1", null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null).procedureArguments();
    assertThat(args.get(1)).isEqualTo("E''01");
    assertThat(args.get(3)).isEqualTo("Z'' OR ''1''=''1");
  }

  @Test
  void barelySplicedNumbersMustBeNumbers() {
    assertThatThrownBy(() -> new RangeTenureExportCriteria(null, null, "1835 OR 1=1", null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null).procedureArguments())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Administration organization unit must be a number");
    assertThatThrownBy(() -> new RangeTenureExportCriteria(null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, "2026", "1)", null, null, null,
        null, null, null, null, null, null).procedureArguments())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Authorized use from must be a number");
  }

  @Test
  void datesMustBeIso() {
    assertThatThrownBy(() -> new RangeTenureExportCriteria(null, null, null, null, null, null,
        null, null, null, null, null, "2020-01-01') OR (1=1", null, null, null, null, null, null,
        null, null, null, null, null, null, null, null).procedureArguments())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Issue date from must be a date");
  }

  @Test
  void reportParametersUseTheMainReportNames() {
    Map<String, Object> params =
        RangeTenureExportLegacy.reportParameters(all().procedureArguments(), "IDIR\\JSMITH");
    assertThat(params).containsEntry("p_user_id", "IDIR\\JSMITH")
        .containsEntry("p_forest_file_id", "RAN07")
        .containsEntry("p_file_type_code_1", "E01")
        .containsEntry("p_org_unit_no", "1835")
        .containsEntry("p_billing_period_from", "2020-01-01")
        .containsEntry("p_year", "2026")
        .containsEntry("p_total_annual_use_to", "8")
        .hasSize(27);
  }

  @Test
  void searchExportNeedsAnOrgUnit() {
    RangeTenureExportLegacy legacy = mock(RangeTenureExportLegacy.class);
    RangeTenureExportService service = new RangeTenureExportService(
        legacy, mock(RangeFileAccess.class), mock(NamedParameterJdbcTemplate.class),
        mock(LoggedUserHelper.class));
    assertThatThrownBy(() -> service.search(RangeTenureExportCriteria.forFile("RAN07", null)))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(legacy, never()).xlsx(any(), any());
  }

  @Test
  void fileExportIsForRangeFilesOnly() {
    RangeTenureExportLegacy legacy = mock(RangeTenureExportLegacy.class);
    RangeFileAccess access = mock(RangeFileAccess.class);
    when(access.isRangeFile("A12345")).thenReturn(false);
    RangeTenureExportService service = new RangeTenureExportService(
        legacy, access, mock(NamedParameterJdbcTemplate.class), mock(LoggedUserHelper.class));
    assertThatThrownBy(() -> service.file("A12345"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(legacy, never()).xlsx(any(), any());
  }

  @Test
  void fileExportPassesTheFilesAdminOrg() {
    RangeTenureExportLegacy legacy = mock(RangeTenureExportLegacy.class);
    RangeFileAccess access = mock(RangeFileAccess.class);
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    LoggedUserHelper auth = mock(LoggedUserHelper.class);
    when(access.isRangeFile("RAN077777")).thenReturn(true);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(List.of("1800"));
    when(auth.getLoggedUserId()).thenReturn("IDIR\\JSMITH");
    when(legacy.xlsx(any(), any())).thenReturn(new byte[] {1});

    new RangeTenureExportService(legacy, access, jdbc, auth).file("RAN077777");

    verify(legacy).xlsx(RangeTenureExportCriteria.forFile("RAN077777", "1800"), "IDIR\\JSMITH");
  }
}
