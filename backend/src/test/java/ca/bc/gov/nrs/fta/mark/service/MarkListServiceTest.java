package ca.bc.gov.nrs.fta.mark.service;

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

@DisplayName("Unit Test | MarkListService")
class MarkListServiceTest {

  @Test
  @DisplayName("a column sort orders the page, ties broken by certificate and process type")
  void columnSort() {
    assertThat(pageSql("clientName,desc"))
        .contains("ORDER BY m.client_name DESC NULLS LAST, m.certificate, m.process_type");
  }

  @Test
  @DisplayName("no or unknown sort keeps the newest-application-first order")
  void defaultOrder() {
    String fallback = "ORDER BY m.mark_appl_date DESC NULLS LAST, m.certificate, m.process_type";
    assertThat(pageSql(null)).contains(fallback);
    assertThat(pageSql("client_name,asc")).contains(fallback);
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    new MarkListService(jdbc, null)
        .list(null, null, null, null, null, null, null, 0, 10, sort);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
