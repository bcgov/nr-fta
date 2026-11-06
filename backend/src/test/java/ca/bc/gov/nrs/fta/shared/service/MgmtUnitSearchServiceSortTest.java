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

@DisplayName("Unit Test | MgmtUnitSearchService column sort")
class MgmtUnitSearchServiceSortTest {

  @Test
  @DisplayName("a column sort orders the wrapped rows, with the tie-breaker after it")
  void columnSort() {
    assertThat(pageSql("description,desc"))
        .contains("SELECT * FROM (")
        .contains("ORDER BY description DESC NULLS LAST, mgmt_unit_type_code");
  }

  @Test
  @DisplayName("no or unknown sort keeps the default order")
  void defaultOrder() {
    assertThat(pageSql(null)).contains("ORDER BY mgmt_unit_type_code");
    assertThat(pageSql("nope,asc")).contains("ORDER BY mgmt_unit_type_code");
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    new MgmtUnitSearchService(jdbc, null).search(null, null, 0, 10, sort);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
