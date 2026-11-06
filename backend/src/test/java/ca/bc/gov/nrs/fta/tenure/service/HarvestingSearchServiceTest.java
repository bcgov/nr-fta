package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.dto.HarvestingSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | HarvestingSearchService")
class HarvestingSearchServiceTest {

  @Test
  @DisplayName("a column sort wraps the grouped query and orders on its aliases")
  void columnSort() {
    assertThat(pageSql("timberMark,desc"))
        .startsWith("SELECT * FROM (")
        .contains("ORDER BY timber_mark DESC NULLS LAST, forest_file_id, cutting_permit_id, hva_skey");
  }

  @Test
  @DisplayName("without a column sort the legacy sortBy order stands")
  void legacyOrder() {
    assertThat(pageSql(null)).contains("ORDER BY DECODE(:sortBy");
    assertThat(pageSql("nope,asc")).contains("ORDER BY DECODE(:sortBy");
  }

  @SuppressWarnings("unchecked")
  private static String pageSql(String sort) {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    // A non-zero count, or the page query is skipped.
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(1L);
    new HarvestingSearchService(jdbc, null).search(
        new HarvestingSearchCriteria(
            null, null, null, "A1", null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null),
        0, 10, sort);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
    return sql.getValue();
  }
}
