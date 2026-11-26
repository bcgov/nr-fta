package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.shared.dto.RatesSaveRequest;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Fold;
import ca.bc.gov.nrs.fta.shared.service.RatesRules.Row;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RatesSaveWriteService (FTA699 Save)")
class RatesSaveWriteServiceTest {

  private static final String OLD = "2025-01-02 03:04:05";
  private static final String NOW = "2026-11-25 10:00:00";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RatesMaintenanceService rates = mock(RatesMaintenanceService.class);
  private final RatesSaveWriteService service = new RatesSaveWriteService(jdbc, rates);

  @BeforeEach
  void setUp() {
    when(rates.years()).thenReturn(List.of(2027, 2026, 2025));
    when(jdbc.queryForObject(contains("SYSDATE"), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(NOW);
    when(rates.fold(2025)).thenReturn(new Fold(false, Map.of(), OLD, "IDIR\\X"));
    when(rates.fold(2027)).thenReturn(new Fold(false, Map.of(), null, null));
  }

  private static RatesSaveRequest req(int year, String ts) {
    return new RatesSaveRequest(year, ts, RatesRulesTest.allValid());
  }

  private List<MapSqlParameterSource> written(String sql, int times) {
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc, times(times)).update(contains(sql), p.capture());
    return p.getAllValues();
  }

  @Test
  void anEmptyYearInsertsTheTwentyFourRowsWithOneTimestamp() {
    when(jdbc.update(contains("INSERT INTO the.range_bill_rate"), any(SqlParameterSource.class)))
        .thenReturn(1);

    assertThat(service.save(req(2027, null), "IDIR\\ME")).isEqualTo(24);

    List<MapSqlParameterSource> ps = written("INSERT INTO the.range_bill_rate", 24);
    for (int i = 0; i < 24; i++) {
      Row r = RatesRules.ROWS.get(i);
      MapSqlParameterSource p = ps.get(i);
      assertThat(p.getValue("fileType")).isEqualTo(r.fileType());
      assertThat(p.getValue("rateType")).isEqualTo(r.rateType());
      assertThat(p.getValue("revenueClass")).isEqualTo(r.revenueClass());
      assertThat(p.getValue("newTimestamp")).isEqualTo(NOW);
      assertThat(p.getValue("calendarYear")).isEqualTo(2027);
      assertThat(p.getValue("userId")).isEqualTo("IDIR\\ME");
    }
    verify(jdbc, never()).update(contains("UPDATE the.range_bill_rate"),
        any(SqlParameterSource.class));
  }

  @Test
  void eachValueFansOutToAllItsRows() {
    Map<String, String> values = RatesRulesTest.allValid();
    values.put("GGR", "7.5");
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

    service.save(new RatesSaveRequest(2025, OLD, values), "IDIR\\ME");

    List<MapSqlParameterSource> ps = written("UPDATE the.range_bill_rate", 24);
    assertThat(ps.stream().filter(p -> "GGR".equals(p.getValue("rateType"))))
        .hasSize(3)
        .allSatisfy(p -> assertThat(p.getValue("rate")).hasToString("7.50"));
    assertThat(ps).allSatisfy(p -> {
      assertThat(p.getValue("updateTimestamp")).isEqualTo(OLD);
      assertThat(p.getValue("newTimestamp")).isEqualTo(NOW);
    });
  }

  @Test
  void theDescriptionIsWrittenNull() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    service.save(req(2025, OLD), "IDIR\\ME");
    service.save(req(2027, null), "IDIR\\ME");
    verify(jdbc, times(24)).update(contains("rng_tenr_rate_desc = NULL"),
        any(SqlParameterSource.class));
    verify(jdbc, times(24)).update(contains(":userId, NULL)"), any(SqlParameterSource.class));
  }

  @Test
  void aRowSavedByAnotherUserRefusesTheWholeSave() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1, 1, 0);

    assertThatThrownBy(() -> service.save(req(2025, OLD), "IDIR\\ME"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("modified by another user")
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    verify(jdbc, times(3)).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aCorruptYearIsRefused() {
    when(rates.fold(2026)).thenReturn(new Fold(true, Map.of(), null, null));
    assertThatThrownBy(() -> service.save(req(2026, OLD), "IDIR\\ME"))
        .hasMessageContaining("Corrupt data has been found");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void theYearIsRequiredAndMustBeListed() {
    assertThatThrownBy(() -> service.save(new RatesSaveRequest(null, null, Map.of()), "U"))
        .hasMessageContaining("Calendar Year is mandatory.");
    assertThatThrownBy(() -> service.save(req(2040, null), "U"))
        .hasMessageContaining("Calendar Year (2040) is not in the list");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void invalidValuesAreRefusedWithLegacyMessages() {
    Map<String, String> values = RatesRulesTest.allValid();
    values.put("GGR", "");
    values.put("GUF", "1000");
    assertThatThrownBy(() -> service.save(new RatesSaveRequest(2025, OLD, values), "U"))
        .hasMessageContaining("Grazing Ground Rent Rate is mandatory.")
        .hasMessageContaining("Grazing Usage Fee Rate field must be between 0.00 and 999.99.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }
}
