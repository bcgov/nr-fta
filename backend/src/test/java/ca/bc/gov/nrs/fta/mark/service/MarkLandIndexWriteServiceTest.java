package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import ca.bc.gov.nrs.fta.mark.dto.MarkLandIndexRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** FTA511: the add row's Deactivate Date, and REMOVE behind the add's gate. */
@DisplayName("Unit Test | MarkLandIndexWriteService")
class MarkLandIndexWriteServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final MarkDetailService detail = mock(MarkDetailService.class);
  private final MarkFieldChecks checks = mock(MarkFieldChecks.class);
  private final MarkLandIndexWriteService service =
      new MarkLandIndexWriteService(jdbc, detail, checks);

  private void given(String status, String fileType) {
    when(detail.findByMarkNumber("12A345")).thenReturn(Optional.of(new MarkDetailDto(
        "12A345", "150557", "12A345", fileType, status, null, null, null, null, null, 60,
        "1867", "DVA", "00001", "00", "Holder", "S", "H", null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0,
        null, null, null, null, 1L, null, true, List.of(), List.of(), List.of(), List.of(),
        null)));
  }

  @Test
  void addStoresTheDeactivateDate() {
    given("HI", "B01");
    when(checks.unchangedOrActive(any(), any(), anyString())).thenReturn(true);
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    LocalDate deactivate = LocalDate.of(2026, 1, 15);

    service.add("12A345", false,
        new MarkLandIndexRequest("NW", "12", "Lot 1", deactivate), false, "IDIR\\TESTER");

    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO the.mark_land_index"), p.capture());
    assertThat(p.getValue().getValue("deactivate")).isEqualTo(deactivate);
  }

  @Test
  void deleteRemovesTheRowAtItsRevisionCount() {
    given("HI", "B01");
    when(jdbc.update(contains("DELETE FROM the.mark_land_index"), any(SqlParameterSource.class)))
        .thenReturn(1);

    service.delete("12A345", false, 7L, 2, false);

    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("DELETE FROM the.mark_land_index"), p.capture());
    assertThat(p.getValue().getValue("skey")).isEqualTo(7L);
    assertThat(p.getValue().getValue("revisionCount")).isEqualTo(2);
  }

  @Test
  void deleteIsRefusedWhereAnAddWouldBe() {
    for (String status : List.of("HX", "DV", "DD")) {
      given(status, "B01");
      assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2, false))
          .as(status).isInstanceOf(ResponseStatusException.class);
    }
    given("HI", "B15");
    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2, false))
        .isInstanceOf(ResponseStatusException.class);
    given("HI", "B01");
    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2, true))
        .isInstanceOf(ResponseStatusException.class);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void deleteNeedsTheRevisionCount() {
    given("HI", "B01");

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, null, false))
        .hasMessageContaining("Revision count");
  }

  @Test
  void aRowChangedElsewhereIsNotDeleted() {
    given("HI", "B01");
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class)))
        .thenReturn(List.of(Map.of("primary_land_index_code", "NW")));

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2, false))
        .hasMessageContaining("changed by someone else");
  }

  @Test
  void aRowNotOnTheMarkIsNotFound() {
    given("HI", "B01");
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2, false))
        .hasMessageContaining("not found");
  }
}
