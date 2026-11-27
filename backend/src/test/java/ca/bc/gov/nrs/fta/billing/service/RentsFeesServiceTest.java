package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeChange;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeListDto;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareRequest;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeePrepareResult;
import ca.bc.gov.nrs.fta.billing.dto.RentsFeesDtos.RentFeeRowDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RentsFeesService (FTA670)")
class RentsFeesServiceTest {

  private static final String TS = "2026-10-01 09:15:00 AM";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RentsFeesPackage pkg = mock(RentsFeesPackage.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final RentsFeesService service = new RentsFeesService(jdbc, pkg, auth);

  private static RentFeeRowDto row(String file, String annv, String ind) {
    return new RentFeeRowDto(file, "2026", ind, "A01", "DCC", "00012345", "00", "ACME",
        annv, "1,000.00", "M3", "0.30", "300.00", TS);
  }

  /** org unit 1 is DCC; HQ is org unit 99. */
  private void orgUnits() {
    when(jdbc.queryForList(eq(RentsFeesService.ORG_UNIT_CODE_SQL), anyMap(), eq(String.class)))
        .thenReturn(List.of("DCC"));
    when(jdbc.queryForObject(eq(RentsFeesService.HQ_ORG_UNIT_SQL), anyMap(), eq(String.class)))
        .thenReturn("99");
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

  private void listing(RentFeeRowDto... rows) {
    when(pkg.get(anyString(), anyString(), anyString()))
        .thenReturn(new RentsFeesPackage.Get("2026-12-01", "2026-12-31", List.of(rows), ""));
  }

  private static void status(Runnable r, HttpStatus expected, String message) {
    assertThatThrownBy(r::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
      assertThat(e.getStatusCode()).isEqualTo(expected);
      assertThat(e.getReason()).isEqualTo(message);
    });
  }

  // ─── Go ───────────────────────────────────────────────────────────

  @Test
  void anUnscopedAdministratorRunsGetAsAnHqUserWithRollup() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", ""), row("A2", "2026-11-28", "R"));

    RentFeeListDto list = service.search(1L, true);

    verify(pkg).get("1", "99", "Y");
    assertThat(list.billFrom()).isEqualTo("2026-11-28");
    assertThat(list.billTo()).isEqualTo("2026-12-31");
    assertThat(list.rows()).extracting(RentFeeRowDto::forestFileId).containsExactly("A1", "A2");
  }

  @Test
  void aScopedAdministratorRunsGetAsThatDistrictsUserWithoutRollup() {
    orgUnits();
    scopedTo("DCC");
    listing();

    service.search(1L, true);

    verify(pkg).get("1", "1", "N");
  }

  @Test
  void aScopedAdministratorIsRefusedAnotherOrgUnit() {
    orgUnits();
    scopedTo("DKA");

    status(() -> service.search(1L, false), HttpStatus.FORBIDDEN,
        "You do not administer district DCC.");
    verify(pkg, never()).get(anyString(), anyString(), anyString());
  }

  @Test
  void goNeedsAnOrgUnit() {
    status(() -> service.search(null, false), HttpStatus.BAD_REQUEST, "Org Unit is required.");
  }

  @Test
  void thePackagesAuthorityRefusalIsForbidden() {
    orgUnits();
    unscoped();
    when(pkg.get(anyString(), anyString(), anyString())).thenReturn(new RentsFeesPackage.Get(
        "2026-12-01", "2026-12-31", List.of(),
        "fta.web.error.authority_exception_view:FTA_670_RentFee,GET,;"));

    status(() -> service.search(1L, false), HttpStatus.FORBIDDEN,
        "The records you are attempting to view belong to a District or Region outside your"
            + " Jurisdiction.");
  }

  // ─── Prepare ──────────────────────────────────────────────────────

  private static RentFeeChange change(String file, String before, String after) {
    return new RentFeeChange(file, "2026", before, after, TS);
  }

  @Test
  void prepareSavesOnlyTheChangedRows() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", ""), row("A2", "2026-12-11", "R"), row("A3", "2026-12-12", ""));
    when(pkg.save(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("");

    RentFeePrepareResult r = service.prepare(new RentFeePrepareRequest(1L, false, List.of(
        change("A1", " ", "D"), change("A2", "R", "R"), change("A3", "", "R"))), "IDIR\\ME");

    verify(pkg).save("A1", "2026", "", "D", "IDIR\\ME", TS);
    verify(pkg).save("A3", "2026", "", "R", "IDIR\\ME", TS);
    verify(pkg, never()).save(eq("A2"), anyString(), anyString(), anyString(), anyString(),
        anyString());
    assertThat(r.saved()).isEqualTo(2);
    assertThat(r.message()).isEqualTo("Prepare successful.");
  }

  @Test
  void prepareWithNothingChangedSaysSo() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", "R"));

    RentFeePrepareResult r = service.prepare(
        new RentFeePrepareRequest(1L, false, List.of(change("A1", "R", "R"))), "IDIR\\ME");

    assertThat(r.saved()).isZero();
    assertThat(r.message()).isEqualTo("No changes made.");
    verify(pkg, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void aRowChangedSinceGoFailsTheWholePrepareWithAConflict() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", ""), row("A2", "2026-12-11", ""));
    when(pkg.save(eq("A1"), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("");
    when(pkg.save(eq("A2"), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(
            "fta.web.usr.database.record.modified:FTA_670_RentFee,CHANGE,BILLABLE_TENURE;");

    status(() -> service.prepare(new RentFeePrepareRequest(1L, false, List.of(
            change("A1", "", "R"), change("A2", "", "R"))), "IDIR\\ME"),
        HttpStatus.CONFLICT, "Row 2 has been previously modified. Re-select data.");
  }

  @Test
  void aRowGetNoLongerListsCountsAsModified() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", ""));

    status(() -> service.prepare(new RentFeePrepareRequest(1L, false, List.of(
            change("A1", "", ""), change("GONE", "", "D"))), "IDIR\\ME"),
        HttpStatus.CONFLICT, "Row 2 has been previously modified. Re-select data.");
    verify(pkg, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anOptNotOfferedIsRefused() {
    orgUnits();
    unscoped();
    listing(row("A1", "2026-12-10", ""));

    status(() -> service.prepare(new RentFeePrepareRequest(1L, false,
            List.of(change("A1", "", "X"))), "IDIR\\ME"),
        HttpStatus.BAD_REQUEST, "Row 1: Invalid Opt");
  }

  @Test
  void prepareIsRefusedOutsideAScopedAdministratorsDistricts() {
    orgUnits();
    scopedTo("DKA");

    status(() -> service.prepare(new RentFeePrepareRequest(1L, false,
            List.of(change("A1", "", "D"))), "IDIR\\ME"),
        HttpStatus.FORBIDDEN, "You do not administer district DCC.");
    verify(pkg, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void prepareNeedsAnOrgUnit() {
    status(() -> service.prepare(new RentFeePrepareRequest(null, false, List.of()), "IDIR\\ME"),
        HttpStatus.BAD_REQUEST, "Org Unit is required.");
  }

  @Test
  void theCallsBindEveryMainlineParameterByName() {
    assertThat(RentsFeesPackage.GET_CALL)
        .contains("the.fta_670_rentfee.mainline(")
        .contains("p_calender_year => v_year")
        .contains("p_rentfee_results => v_results")
        .contains("IF v_results%ISOPEN");
    assertThat(RentsFeesPackage.SAVE_CALL).contains("p_invoice_prep_ind_new => v_ind_new");
  }
}
