package ca.bc.gov.nrs.fta.tenure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The FTA008 workbook writer's cell references and text escaping. */
@DisplayName("Unit Test | FTA008 workbook writer")
class MetricsWorkbookTest {

  @Test
  void columnLettersRunAThroughZThenAa() {
    assertThat(MetricsWorkbook.column(0)).isEqualTo("A");
    assertThat(MetricsWorkbook.column(18)).isEqualTo("S");
    assertThat(MetricsWorkbook.column(25)).isEqualTo("Z");
    assertThat(MetricsWorkbook.column(26)).isEqualTo("AA");
    assertThat(MetricsWorkbook.column(701)).isEqualTo("ZZ");
    assertThat(MetricsWorkbook.column(702)).isEqualTo("AAA");
  }

  @Test
  void escapesMarkupAndDropsWhatXmlCannotCarry() {
    assertThat(MetricsWorkbook.escape("a & b < c > \"d\" 'e'"))
        .isEqualTo("a &amp; b &lt; c &gt; &quot;d&quot; 'e'");
    assertThat(MetricsWorkbook.escape("tab\there\nline\u0001\u0008\u001F end"))
        .isEqualTo("tab\there\nline end");
    assertThat(MetricsWorkbook.escape("Lheidli T’enneh 🌲"))
        .isEqualTo("Lheidli T’enneh 🌲");
  }
}
