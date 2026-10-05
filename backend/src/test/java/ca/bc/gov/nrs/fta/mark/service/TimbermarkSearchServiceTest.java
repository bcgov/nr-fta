package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.TimbermarkSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@DisplayName("Unit Test | TimbermarkSearchService")
class TimbermarkSearchServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final TimbermarkSearchService service = new TimbermarkSearchService(jdbc, null);

  @Test
  @DisplayName("amended date range filters on the certificate's amend date, whole days")
  void amendedDateRange() {
    String sql = countSql(criteria("2026-10-01", "2026-10-05"));

    assertThat(sql)
        .contains("tm.certificate IN (")
        .contains("FROM the.private_mark_certificate pmc")
        .contains("pmc.private_mark_amend_date >= TO_DATE(:amendDateFrom, 'YYYY-MM-DD')")
        // Stamped with SYSDATE, so "to" covers the whole day.
        .contains("pmc.private_mark_amend_date < TO_DATE(:amendDateTo, 'YYYY-MM-DD') + 1");
    assertThat(lastParams().getValue("amendDateFrom")).isEqualTo("2026-10-01");
    assertThat(lastParams().getValue("amendDateTo")).isEqualTo("2026-10-05");
  }

  @Test
  @DisplayName("one bound alone is enough")
  void amendedDateFromOnly() {
    String sql = countSql(criteria("2026-10-01", null));

    assertThat(sql).contains(":amendDateFrom").doesNotContain(":amendDateTo");
  }

  @Test
  @DisplayName("no amended date, no certificate filter")
  void noAmendedDate() {
    assertThat(countSql(criteria(null, " "))).doesNotContain("private_mark_certificate");
  }

  private ArgumentCaptor<MapSqlParameterSource> params;

  private String countSql(TimbermarkSearchCriteria c) {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
        .thenReturn(0L);
    service.search(c, 0, 10);
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).queryForObject(sql.capture(), params.capture(), eq(Long.class));
    return sql.getValue();
  }

  private MapSqlParameterSource lastParams() {
    return params.getValue();
  }

  private static TimbermarkSearchCriteria criteria(String amendFrom, String amendTo) {
    return new TimbermarkSearchCriteria(
        null, null, null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, amendFrom, amendTo, null, "999999", null, null, null,
        null, null);
  }
}
