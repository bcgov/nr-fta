package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeUnitSearchService")
class RangeUnitSearchServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RangeUnitSearchService service = new RangeUnitSearchService(jdbc, null);

  @Test
  @DisplayName("no org unit is a 400, before any query (search and export)")
  void orgUnitRequired() {
    assertThatThrownBy(() -> service.search(" ", null, null, null, 0, 10))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(RangeUnitSearchService.ORG_UNIT_REQUIRED);
        });
    assertThatThrownBy(() -> RangeUnitSearchService.requireOrgUnit(null))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(jdbc);
  }

  @Test
  @DisplayName("blank names still filter LIKE '%', so NULL names never match, as in legacy")
  void nullNamesNeverMatch() {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    service.search("1", null, null, null, 0, 10);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(sql.capture(), any(MapSqlParameterSource.class), eq(Long.class));
    assertThat(sql.getValue())
        .contains("AND UPPER(rp.pasture_name) LIKE UPPER(:pastureName) || '%'")
        .contains("AND UPPER(ru.range_unit_name) LIKE UPPER(:rangeUnitName) || '%'")
        .doesNotContain(":pastureName   IS NULL")
        .doesNotContain(":rangeUnitName IS NULL");
  }
}
