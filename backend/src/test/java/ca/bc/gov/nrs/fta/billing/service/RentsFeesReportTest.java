package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeRowDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RentsFeesReport (FTA406, FTA670 Print)")
class RentsFeesReportTest {

  private static String text(byte[] pdf) throws Exception {
    PdfReader reader = new PdfReader(pdf);
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      sb.append(new PdfTextExtractor(reader).getTextFromPage(i)).append('\n');
    }
    return sb.toString();
  }

  private static Map<String, Object> sampleRow(String file, String type, String units,
      String rate, String amount, String client) {
    Map<String, Object> r = new HashMap<>();
    r.put("forest_file_id", file);
    r.put("file_type_code", type);
    r.put("file_status_st", "HI");
    r.put("legal_effective_dt", "2015-12-10 00:00:00");
    r.put("initial_expiry_dt", "2035-12-09 00:00:00");
    r.put("current_expiry_dt", "2035-12-09 00:00:00");
    r.put("invoice_prep_ind", "R");
    r.put("calendar_year", new BigDecimal("2026"));
    r.put("anniversary_date", "2026-12-10");
    r.put("org_unit_code", "DCC");
    r.put("billable_units", new BigDecimal(units));
    r.put("billable_rates", new BigDecimal(rate));
    r.put("billing_amounts", new BigDecimal(amount));
    r.put("serverid", "FTATEST");
    r.put("client_number", "00012345");
    r.put("client_locn_code", "00");
    r.put("client_name", client);
    return r;
  }

  private static Map<String, Object> sampleParams() {
    return RentsFeesReport.parameters("IDIR\\JSMITH", "2026-11-28", "2026-12-31",
        "Cariboo-Chilcotin Natural Resource District", "DCC", "1010", "N");
  }

  // ─── The layout ───────────────────────────────────────────────────

  @Test
  void theLayoutCompilesAndRendersRowsWithLegacysUnitTypeAndAmount() throws Exception {
    byte[] pdf = JasperTemplates.pdf(RentsFeesReport.TEMPLATE, sampleParams(), List.of(
        // A01 is billed by volume: M3, units x rate.
        sampleRow("A12345", "A01", "1000", "0.30", "999", "ACME LOGGING LTD."),
        // S01 is billed its billing amount, whatever units x rate.
        sampleRow("S54321", "S01", "12.5", "2.00", "175", "WILLOWFIELD RANCH LTD.")));

    Files.write(Path.of("target", "FTA406-sample.pdf"), pdf);
    String text = text(pdf);
    assertThat(text).contains(
        "FTA406R", "FTATEST", "IDIR\\JSMITH", "Rents and Fees Preparation Report",
        "Prepared By:", "Cariboo-Chilcotin Natural Resource District", "DCC",
        "2026-11-28", "2026-12-31",
        "A12345", "ACME LOGGING LTD.", "M3", "1,000.00", "0.30", "300.00",
        "S54321", "WILLOWFIELD RANCH LTD.", "HA", "175.00",
        "2026-12-10", "END OF REPORT");
  }

  @Test
  void theLayoutRendersWithNoRows() throws Exception {
    byte[] pdf = JasperTemplates.pdf(RentsFeesReport.TEMPLATE, sampleParams(), List.of());
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    assertThat(text(pdf)).contains("Rents and Fees Preparation Report", "END OF REPORT");
  }

  // ─── FTAR406P ─────────────────────────────────────────────────────

  @Test
  void theCallIsFtar406pWithTheCursorFirstAndEightInputs() {
    assertThat(RentsFeesReport.CALL).isEqualTo("{call the.ftar406p(?, ?, ?, ?, ?, ?, ?, ?, ?)}");
    assertThat(RentsFeesReport.INPUTS).containsExactly(
        "p_prepared_by", "p_billing_period_from", "p_billing_period_to", "p_org_unit_name",
        "p_org_unit_code", "p_org_unit_no", "p_rollup_ind", "p_userid");
  }

  @Test
  void bindRegistersTheCursorAndBindsTheInputsInProcedureOrder() throws Exception {
    CallableStatement cs = mock(CallableStatement.class);
    RentsFeesReport.bind(cs, sampleParams());
    InOrder o = inOrder(cs);
    o.verify(cs).registerOutParameter(1, Types.REF_CURSOR);
    o.verify(cs).setString(2, "IDIR\\JSMITH");
    o.verify(cs).setString(3, "2026-11-28");
    o.verify(cs).setString(4, "2026-12-31");
    o.verify(cs).setString(5, "Cariboo-Chilcotin Natural Resource District");
    o.verify(cs).setString(6, "DCC");
    o.verify(cs).setString(7, "1010");
    o.verify(cs).setString(8, "N");
    o.verify(cs).setString(9, "IDIR\\JSMITH");
  }

  @Test
  void aRowReadsTheNumberFieldsAsNumbersAndTheRestAsText() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString(anyString())).thenAnswer(inv -> "s:" + inv.getArgument(0));
    when(rs.getBigDecimal(anyString())).thenReturn(BigDecimal.TEN);
    Map<String, ?> row = RentsFeesReport.row(rs);
    assertThat(row.keySet()).containsExactlyInAnyOrderElementsOf(RentsFeesReport.FIELDS);
    assertThat(row.get("calendar_year")).isEqualTo(BigDecimal.TEN);
    assertThat(row.get("billable_units")).isEqualTo(BigDecimal.TEN);
    assertThat(row.get("billable_rates")).isEqualTo(BigDecimal.TEN);
    assertThat(row.get("billing_amounts")).isEqualTo(BigDecimal.TEN);
    assertThat(row.get("forest_file_id")).isEqualTo("s:forest_file_id");
    assertThat(row.get("serverid")).isEqualTo("s:serverid");
  }

  // ─── Scope and parameters ─────────────────────────────────────────

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate plain = mock(JdbcTemplate.class);
  private final RentsFeesPackage pkg = mock(RentsFeesPackage.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final RentsFeesReport report =
      new RentsFeesReport(jdbc, new RentsFeesService(jdbc, pkg, auth), auth);

  /** Org unit 1010 is DCC; Go lists one row, earlier than the package's billing period. */
  private void dcc(boolean known) {
    when(jdbc.queryForList(eq(RentsFeesService.ORG_UNIT_CODE_SQL), anyMap(), eq(String.class)))
        .thenReturn(List.of("DCC"));
    when(jdbc.queryForObject(eq(RentsFeesService.HQ_ORG_UNIT_SQL), anyMap(), eq(String.class)))
        .thenReturn("99");
    when(pkg.get(anyString(), anyString(), anyString())).thenReturn(new RentsFeesPackage.Get(
        "2026-12-01", "2026-12-31",
        List.of(new RentFeeRowDto("A1", "2026", "R", "A01", "DCC", "00012345", "00", "ACME",
            "2026-11-28", "1,000.00", "M3", "0.30", "300.00", "ts")),
        ""));
    when(jdbc.query(eq(RentsFeesReport.ORG_UNIT_SQL), anyMap(), any(RowMapper.class)))
        .thenReturn(known
            ? List.of(new RentsFeesReport.OrgUnit(
                "DCC", "Cariboo-Chilcotin Natural Resource District"))
            : List.of());
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
  }

  private void unscoped() {
    when(auth.isUnscopedAdmin()).thenReturn(true);
    when(auth.administersDistrict(any())).thenReturn(true);
  }

  private void scopedTo(String district) {
    when(auth.isUnscopedAdmin()).thenReturn(false);
    when(auth.administersDistrict(any()))
        .thenAnswer(inv -> district.equals(inv.getArgument(0)));
  }

  /** Runs the report, answering FTAR406P with no rows; returns what was bound. */
  @SuppressWarnings("unchecked")
  private CallableStatement run(Boolean rollup) throws Exception {
    CallableStatement cs = mock(CallableStatement.class);
    when(cs.getObject(1, ResultSet.class)).thenReturn(mock(ResultSet.class));
    when(plain.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenAnswer(inv -> ((CallableStatementCallback<?>) inv.getArgument(1))
            .doInCallableStatement(cs));
    byte[] pdf = report.render(1010L, rollup, "IDIR\\JSMITH");
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    return cs;
  }

  @Test
  void anUnscopedAdministratorPrintsWithRollupAndGosBillingPeriod() throws Exception {
    dcc(true);
    unscoped();
    CallableStatement cs = run(true);
    verify(cs).setString(2, "IDIR\\JSMITH");
    // Go's billing period: the earliest anniversary date listed, to the package's end.
    verify(cs).setString(3, "2026-11-28");
    verify(cs).setString(4, "2026-12-31");
    verify(cs).setString(5, "Cariboo-Chilcotin Natural Resource District");
    verify(cs).setString(6, "DCC");
    verify(cs).setString(7, "1010");
    verify(cs).setString(8, "Y");
    verify(cs).setString(9, "IDIR\\JSMITH");
  }

  @Test
  void aDistrictScopedAdministratorPrintsTheirDistrictWithoutRollup() throws Exception {
    dcc(true);
    scopedTo("DCC");
    CallableStatement cs = run(true);
    verify(cs).setString(8, "N");
  }

  @Test
  void anOrgUnitMissingFromTheListIsUnkUnknownAsLegacy() throws Exception {
    dcc(false);
    unscoped();
    CallableStatement cs = run(false);
    verify(cs).setString(5, "Unknown");
    verify(cs).setString(6, "UNK");
    verify(cs).setString(8, "N");
  }

  @Test
  void aDistrictScopedAdministratorMayNotPrintAnotherDistrict() {
    dcc(true);
    scopedTo("DKA");
    assertThatThrownBy(() -> report.render(1010L, false, "IDIR\\JSMITH"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verify(plain, never()).execute(
        any(CallableStatementCreator.class), any(CallableStatementCallback.class));
  }

  @Test
  void anOrgUnitIsRequired() {
    assertThatThrownBy(() -> report.render(null, false, "IDIR\\JSMITH"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(jdbc, never()).getJdbcTemplate();
  }
}
