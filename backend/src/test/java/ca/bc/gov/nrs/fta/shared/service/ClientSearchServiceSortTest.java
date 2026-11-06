package ca.bc.gov.nrs.fta.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | ClientSearchService column sort")
class ClientSearchServiceSortTest {

  @Test
  @DisplayName("a column sort orders the wrapped rows, with the tie-breaker after it")
  void columnSort() {
    assertThat(pageSql("city,desc"))
        .contains("SELECT * FROM (")
        .contains("ORDER BY city DESC NULLS LAST, client_number, client_locn_code");
  }

  @Test
  @DisplayName("no or unknown sort keeps the default order")
  void defaultOrder() {
    assertThat(pageSql(null)).contains("ORDER BY t2.client_name");
    assertThat(pageSql("nope,asc")).contains("ORDER BY t2.client_name");
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    new ClientSearchService(jdbc, null).search(null, null, null, null, null, 0, 10, sort);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
