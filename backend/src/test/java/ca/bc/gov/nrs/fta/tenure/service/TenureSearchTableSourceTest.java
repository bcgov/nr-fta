package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.dto.TenureSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | TenureSearchTableSource")
class TenureSearchTableSourceTest {

  @Test
  @DisplayName("a column sort orders the page; an unknown one keeps the sortBy order")
  void columnSort() {
    assertThat(pageSql("expiryDate,desc"))
        .contains("ORDER BY expiry_date DESC NULLS LAST, forest_file_id DESC");
    assertThat(pageSql("nope,asc")).contains("ORDER BY org_unit_code, forest_file_id DESC");
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    // A non-zero count, or the page query is skipped.
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(1L);
    new TenureSearchTableSource(jdbc, null).search(
        new TenureSearchCriteria(
            null, "A1", null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null),
        0, 10, sort);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
