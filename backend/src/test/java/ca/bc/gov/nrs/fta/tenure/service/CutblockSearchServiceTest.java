package ca.bc.gov.nrs.fta.tenure.service;

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

@DisplayName("Unit Test | CutblockSearchService")
class CutblockSearchServiceTest {

  @Test
  @DisplayName("a column sort orders the page; an unknown one keeps the sortBy order")
  void columnSort() {
    assertThat(pageSql("disturbanceStartDate,asc")).contains(
        "ORDER BY disturbance_start_date ASC NULLS LAST, forest_file_id, cutting_permit_id,"
            + " cut_block_id, cb_skey");
    assertThat(pageSql("nope,asc"))
        .contains("ORDER BY org_unit_code, forest_file_id, cutting_permit_id, cut_block_id");
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(1L);
    new CutblockSearchService(jdbc, null).search(
        "A1", null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, sort, 0, 10);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
