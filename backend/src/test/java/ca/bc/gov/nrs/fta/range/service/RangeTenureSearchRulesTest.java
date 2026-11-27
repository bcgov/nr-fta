package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | FTA001R criteria (RangeTenureSearchRules and the service)")
class RangeTenureSearchRulesTest {

  @Test
  @DisplayName("client name: upper-cased, the user's wildcards only, none added")
  void clientName() {
    assertThat(RangeTenureSearchRules.clientName("smith%john%")).isEqualTo("SMITH%JOHN%");
    assertThat(RangeTenureSearchRules.clientName("Acme")).isEqualTo("ACME");
    assertThat(RangeTenureSearchRules.clientName(" ")).isNull();
    assertThat(RangeTenureSearchRules.clientName(null)).isNull();
  }

  @Test
  @DisplayName("the search matches legacy's name expression, case-sensitively")
  void clientNameSql() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    search(jdbc, null, null, "smith%");
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<MapSqlParameterSource> params =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Long.class));
    assertThat(sql.getValue()).contains(
        "fc.client_name || ', ' || fc.legal_first_name || ' ' || fc.legal_middle_name");
    assertThat(sql.getValue()).doesNotContain("UPPER(fc.client_name)");
    assertThat(params.getValue().getValue("clientName")).isEqualTo("SMITH%");
  }

  @Test
  @DisplayName("an unknown management unit type/id is legacy's 400")
  void mgmtUnit() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(contains("forest_mgmt_unit"), any(MapSqlParameterSource.class),
        eq(Long.class))).thenReturn(0L);
    assertThatThrownBy(() -> search(jdbc, "T", "99", null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(RangeTenureSearchRules.MGMT_UNIT_INVALID);
        });
  }

  private static void search(NamedParameterJdbcTemplate jdbc, String muType, String muId,
      String clientName) {
    new RangeTenureSearchService(jdbc, null).search(
        null, null, "1", null, clientName, null, null, null, null, muType, muId,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
        0, 10, null);
  }
}
