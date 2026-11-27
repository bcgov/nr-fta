package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.CopyRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.Options;
import ca.bc.gov.nrs.fta.range.dto.RangeUsageDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Outcome;
import ca.bc.gov.nrs.fta.range.service.RangeUsageLegacy.Values;
import ca.bc.gov.nrs.fta.range.service.RangeUsageWriteService.FileFacts;
import ca.bc.gov.nrs.fta.range.service.RangeUsageWriteService.StoredRow;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeUsageWriteService (FTA613R Save and Copy)")
class RangeUsageWriteServiceTest {

  private static final String FILE = "RAN001";
  private static final String USER = "IDIR\\ME";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RangeFileAccess access = mock(RangeFileAccess.class);
  private final RangeUsageLegacy legacy = mock(RangeUsageLegacy.class);
  private final RangeUsageWriteService service =
      new RangeUsageWriteService(jdbc, access, legacy);

  private void file(String status, boolean pending) {
    doReturn(List.of(new FileFacts("E01", status, "2023-01-01", "2027-12-31", pending)))
        .when(jdbc).query(eq(RangeUsageWriteService.FILE_SQL), any(SqlParameterSource.class),
            any(RowMapper.class));
  }

  private void stored(StoredRow row) {
    doReturn(row == null ? List.of() : List.of(row))
        .when(jdbc).query(eq(RangeUsageWriteService.ROW_SQL), any(SqlParameterSource.class),
            any(RowMapper.class));
  }

  @BeforeEach
  void setUp() {
    file("HI", false);
    stored(null);
    doReturn(List.of(new CodeOptionDto("INC", "INC - Increase")))
        .when(jdbc).query(eq(RangeUsageWriteService.INCREASE_REASONS_SQL), anyMap(),
            any(RowMapper.class));
    doReturn(List.of(new CodeOptionDto("NU1", "NU1 - Non-use")))
        .when(jdbc).query(eq(RangeUsageWriteService.NON_USE_REASONS_SQL), anyMap(),
            any(RowMapper.class));
    when(legacy.save(any(), any())).thenReturn(new Outcome(List.of(), false));
    when(legacy.copy(any(), anyBoolean(), any(), any()))
        .thenReturn(new Outcome(List.of(), false));
  }

  private static SaveRequest request(Integer revision) {
    return new SaveRequest("100", "20", "INC", "", "", "", "", revision);
  }

  @Test
  void optionsListTheTermsYearsAndTheInboxFlag() {
    file("HI", true);
    Options o = service.options(FILE);
    assertThat(o.years()).containsExactly(2023, 2024, 2025, 2026, 2027);
    assertThat(o.increaseReasons()).extracting(CodeOptionDto::code).containsExactly("INC");
    assertThat(o.applicationPending()).isTrue();
  }

  @Test
  void aNewYearIsSavedByTheLegacyPackageWithTheTermAndTotal() {
    assertThat(service.save(FILE, 2025, request(null), USER).message())
        .isEqualTo("Save successful.");
    ArgumentCaptor<Values> v = ArgumentCaptor.forClass(Values.class);
    verify(access).requireEditable(FILE);
    verify(legacy).save(v.capture(), eq(null));
    assertThat(v.getValue().startDate()).isEqualTo("2023-01-01");
    assertThat(v.getValue().expiryDate()).isEqualTo("2027-12-31");
    assertThat(v.getValue().totalAnnualUse()).isEqualTo(120);
    assertThat(v.getValue().nonUseBillable()).isNull();
    assertThat(v.getValue().userId()).isEqualTo(USER);
  }

  @Test
  void theDistrictGateRunsFirst() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireEditable(FILE);
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .isInstanceOf(ResponseStatusException.class);
    verify(legacy, never()).save(any(), any());
  }

  @Test
  void anApplicationInTheInboxRefusesSaveAndCopy() {
    file("HI", true);
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .hasMessageContaining(RangeUsageRules.PENDING);
    assertThatThrownBy(() -> service.copy(FILE, 2025, new CopyRequest(true, null, null), USER))
        .hasMessageContaining(RangeUsageRules.PENDING);
  }

  @Test
  void theFormsChecksRefuseBeforeTheWrite() {
    file("PE", false);
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .hasMessageContaining(RangeUsageRules.NO_SAVE_WHEN_PE)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    verify(legacy, never()).save(any(), any());
  }

  @Test
  void savingAYearThatHasUsageNeedsItsRevision() {
    stored(new StoredRow("50", "0", null, "0", "0", "0", null, 3));
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    service.save(FILE, 2025, request(3), USER);
    verify(legacy).save(any(), eq(3));
  }

  @Test
  void whatThePackageReportsFailsTheRequest() {
    when(legacy.save(any(), any())).thenReturn(new Outcome(List.of("You may only add."), false));
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .hasMessageContaining("You may only add.")
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    when(legacy.save(any(), any()))
        .thenReturn(new Outcome(List.of(RangeUsageLegacy.MSG_MODIFIED), true));
    assertThatThrownBy(() -> service.save(FILE, 2025, request(null), USER))
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void copySendsTheStoredYearToTheLegacyPackage() {
    stored(new StoredRow("80", "0", null, "4", "10", "0", null, 2));
    assertThat(service.copy(FILE, 2024, new CopyRequest(false, "2025", "2027"), USER).message())
        .isEqualTo("Copy successful for range usage records.");
    ArgumentCaptor<Values> v = ArgumentCaptor.forClass(Values.class);
    verify(legacy).copy(v.capture(), eq(false), eq(2025), eq(2027));
    assertThat(v.getValue().calendarYear()).isEqualTo(2024);
    assertThat(v.getValue().authorizedUse()).isEqualTo("80");
    assertThat(v.getValue().totalAnnualUse()).isEqualTo(70);
  }

  @Test
  void copyOfAllRemainingYearsSendsNoFromOrTo() {
    stored(new StoredRow("80", "0", null, "0", "0", "0", null, 2));
    service.copy(FILE, 2024, new CopyRequest(true, null, null), USER);
    verify(legacy).copy(any(), eq(true), eq(null), eq(null));
  }

  @Test
  void copyOfAYearWithNoUsageIsAConflict() {
    assertThatThrownBy(() -> service.copy(FILE, 2024, new CopyRequest(true, null, null), USER))
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void copyChecksItsYears() {
    stored(new StoredRow("80", "0", null, "0", "0", "0", null, 2));
    assertThatThrownBy(() -> service.copy(FILE, 2024, new CopyRequest(false, "2026", null), USER))
        .hasMessageContaining(RangeUsageRules.COPY_BOTH_YEARS);
    verify(legacy, never()).copy(any(), anyBoolean(), any(), any());
  }
}
