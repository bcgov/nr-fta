package ca.bc.gov.nrs.fta.shared.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | ColumnSort")
class ColumnSortTest {

  private static final Map<String, String> COLUMNS =
      Map.of("clientName", "client_name", "issueDate", "mark_issue_date");

  private static String orderBy(String sort) {
    return ColumnSort.orderBy(sort, COLUMNS, "org_unit_code, id", "id");
  }

  @Test
  @DisplayName("a mapped column sorts first, nulls last, then the tie-breaker")
  void mappedColumn() {
    assertThat(orderBy("clientName,asc")).isEqualTo("\n ORDER BY client_name ASC NULLS LAST, id");
    assertThat(orderBy("issueDate,DESC"))
        .isEqualTo("\n ORDER BY mark_issue_date DESC NULLS LAST, id");
  }

  @Test
  @DisplayName("direction defaults to ascending")
  void defaultDirection() {
    assertThat(orderBy("clientName")).isEqualTo("\n ORDER BY client_name ASC NULLS LAST, id");
  }

  @Test
  @DisplayName("no, unknown or malformed sort falls back to the default order")
  void fallback() {
    String fallback = "\n ORDER BY org_unit_code, id";
    assertThat(orderBy(null)).isEqualTo(fallback);
    assertThat(orderBy(" ")).isEqualTo(fallback);
    assertThat(orderBy("client_name,asc")).isEqualTo(fallback);
    assertThat(orderBy("clientName; DROP TABLE x,asc")).isEqualTo(fallback);
    assertThat(orderBy("clientName,sideways")).isEqualTo(fallback);
    assertThat(orderBy("clientName,asc,extra")).isEqualTo(fallback);
  }
}
