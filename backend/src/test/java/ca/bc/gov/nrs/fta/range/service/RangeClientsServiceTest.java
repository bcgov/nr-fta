package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientAssignRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientRowDto;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RangeClientsListDto;
import ca.bc.gov.nrs.fta.range.dto.RangeClientsDtos.RelatedClientDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeClientsService (FTA920R)")
class RangeClientsServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RangeClientsPackage pkg = mock(RangeClientsPackage.class);
  private final RangeFileAccess access = mock(RangeFileAccess.class);
  private final RangeClientsService service = new RangeClientsService(jdbc, pkg, access);

  private static final RelatedClientDto MAIN = new RelatedClientDto("00012345", "00", "ACME",
      "1 Main St Victoria, BC V8V 1A1 CA", "Bus: n/a Res: n/a Cell:n/a Fax:n/a", null, "A",
      "A-Licensee");

  private static final RangeClientAssignRequest REQUEST =
      new RangeClientAssignRequest("00012345", "00", "2026-04-01", "");

  private void pending(boolean pending) {
    when(jdbc.queryForObject(eq(RangeClientsService.APPLICATION_PENDING_SQL), anyMap(),
        eq(Integer.class))).thenReturn(pending ? 1 : 0);
  }

  @SuppressWarnings("unchecked")
  private void rows(RangeClientRowDto... rows) {
    when(jdbc.query(eq(RangeClientsService.LIST_SQL), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of(rows));
  }

  @Test
  void theListIsInLegacysOrderWithItsContactColumns() {
    assertThat(RangeClientsService.LIST_SQL)
        .contains("ORDER BY fcl.licensee_end_date DESC, fcl.update_timestamp DESC,")
        .contains("'Bus: '")
        .contains("cl.email_address");
  }

  @Test
  void listEnablesSaveForAnEditableFileWithNothingInTheInbox() {
    rows();
    when(access.mayEdit("RAN1")).thenReturn(true);
    pending(false);
    RangeClientsListDto list = service.list("RAN1");
    assertThat(list.canEdit()).isTrue();
    assertThat(list.warnings()).isEmpty();
  }

  @Test
  void listIsViewOnlyWhileAnApplicationIsInTheInbox() {
    rows();
    when(access.mayEdit("RAN1")).thenReturn(true);
    pending(true);
    RangeClientsListDto list = service.list("RAN1");
    assertThat(list.canEdit()).isFalse();
    assertThat(list.warnings())
        .containsExactly("You may not update while application is in the Inbox.");
  }

  @Test
  void listIsViewOnlyForAnotherDistrictsFile() {
    rows();
    when(access.mayEdit("RAN1")).thenReturn(false);
    pending(true);
    RangeClientsListDto list = service.list("RAN1");
    assertThat(list.canEdit()).isFalse();
    assertThat(list.warnings()).isEmpty();
  }

  @Test
  void relatedReturnsThePackagesRows() {
    when(pkg.getRelated("RAN1", "00012345", "00"))
        .thenReturn(new RangeClientsPackage.Related(List.of(MAIN), ""));
    assertThat(service.related("RAN1", " 00012345 ", "00")).containsExactly(MAIN);
  }

  @Test
  void relatedNeedsAClientBeforeCallingThePackage() {
    assertThatThrownBy(() -> service.related("RAN1", "", "00"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Client Number is mandatory.");
        });
    verify(pkg, never()).getRelated(anyString(), anyString(), anyString());
  }

  @Test
  void relatedReportsAClientWhoIsAlreadyTheLicensee() {
    when(pkg.getRelated("RAN1", "00012345", "00")).thenReturn(new RangeClientsPackage.Related(
        List.of(), "fta.web.error.user.custom.msg:Client already exists as the main or"
            + " secondary licensee;"));
    assertThatThrownBy(() -> service.related("RAN1", "00012345", "00"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason())
            .isEqualTo("Client already exists as the main or secondary licensee."));
  }

  @Test
  void assignSavesThroughThePackage() {
    pending(false);
    when(pkg.getRelated("RAN1", "00012345", "00"))
        .thenReturn(new RangeClientsPackage.Related(List.of(MAIN), ""));
    when(pkg.save("RAN1", "00012345", "00", "2026-04-01", null, "IDIR\\JDOE")).thenReturn("");
    assertThat(service.assign("RAN1", REQUEST, "IDIR\\JDOE"))
        .isEqualTo("Update successful. The Range Tenure Current Client has been updated.");
    verify(access).requireEditable("RAN1");
  }

  @Test
  void assignPassesThePreviousLicenseeEndDate() {
    pending(false);
    when(pkg.getRelated("RAN1", "00012345", "00"))
        .thenReturn(new RangeClientsPackage.Related(List.of(MAIN), ""));
    when(pkg.save(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("");
    service.assign("RAN1",
        new RangeClientAssignRequest("00012345", "00", "2026-04-01", "2026-03-31"), "U");
    verify(pkg).save("RAN1", "00012345", "00", "2026-04-01", "2026-03-31", "U");
  }

  @Test
  void assignNeedsTheStartDate() {
    assertThatThrownBy(() -> service.assign("RAN1",
        new RangeClientAssignRequest("00012345", "00", null, null), "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Licensee Start Date is mandatory.");
        });
    verify(access, never()).requireEditable(anyString());
  }

  @Test
  void assignRefusesAUserWhoMayNotEditTheFileWithLegacysMessage() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "x"))
        .when(access).requireEditable("RAN1");
    assertThatThrownBy(() -> service.assign("RAN1", REQUEST, "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(e.getReason())
              .isEqualTo("You are not authorized to attach clients at the FILE level.");
        });
  }

  @Test
  void assignPassesOnANotARangeFileRefusal() {
    doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "A1 is not a range tenure."))
        .when(access).requireEditable("A1");
    assertThatThrownBy(() -> service.assign("A1", REQUEST, "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getReason()).isEqualTo("A1 is not a range tenure."));
  }

  @Test
  void assignIsRefusedWhileAnApplicationIsInTheInbox() {
    pending(true);
    assertThatThrownBy(() -> service.assign("RAN1", REQUEST, "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason())
              .isEqualTo("You may not update while application is in the Inbox.");
        });
    verify(pkg, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void assignRunsGetFirstSoAnExpiredLocationIsNotSaved() {
    pending(false);
    when(pkg.getRelated("RAN1", "00012345", "00")).thenReturn(new RangeClientsPackage.Related(
        List.of(), "fta.web.usr.database.record.expiredClientLocation"));
    assertThatThrownBy(() -> service.assign("RAN1", REQUEST, "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason())
            .isEqualTo("That client location is expired. Please check your client and"
                + " location."));
    verify(pkg, never()).save(any(), any(), any(), any(), any(), any());
  }

  @Test
  void assignFailsWhenThePackageReportsAnError() {
    pending(false);
    when(pkg.getRelated("RAN1", "00012345", "00"))
        .thenReturn(new RangeClientsPackage.Related(List.of(MAIN), ""));
    when(pkg.save(any(), any(), any(), any(), any(), any())).thenReturn(
        "fta.web.usr.database.record.duplicate:Fta920_Asso_Clnts,SAVE,FOREST_FILE_CLIENT,-1;");
    assertThatThrownBy(() -> service.assign("RAN1", REQUEST, "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).startsWith("A record was found with an identical key value.");
        });
  }
}
