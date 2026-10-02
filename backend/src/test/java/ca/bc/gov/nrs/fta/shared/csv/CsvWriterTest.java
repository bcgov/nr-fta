package ca.bc.gov.nrs.fta.shared.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the CSV escaping the search exports rely on.
 *
 * <p>Worth pinning because every failure here is silent: a stray comma shifts
 * every later column of that row, and an unescaped formula character is only
 * discovered once a spreadsheet evaluates it.
 */
@DisplayName("Unit Test | CsvWriter")
class CsvWriterTest {

  private final StringWriter out = new StringWriter();

  private final CsvWriter csv = new CsvWriter(out);

  private String written() {
    return out.toString();
  }

  @Nested
  @DisplayName("quoting")
  class Quoting {

    @Test
    void plainValuesAreNotQuoted() {
      csv.writeRow("A18", "DCC", "123");

      assertThat(written()).isEqualTo("A18,DCC,123\r\n");
    }

    @Test
    void aValueWithACommaIsQuoted() {
      csv.writeRow("SMITH, JOHN");

      assertThat(written()).isEqualTo("\"SMITH, JOHN\"\r\n");
    }

    @Test
    void anEmbeddedQuoteIsDoubledAndTheFieldQuoted() {
      csv.writeRow("THE \"BIG\" MILL");

      assertThat(written()).isEqualTo("\"THE \"\"BIG\"\" MILL\"\r\n");
    }

    @Test
    void aValueWithANewlineIsQuotedSoTheRowIsNotSplit() {
      csv.writeRow("line one\nline two");

      assertThat(written()).isEqualTo("\"line one\nline two\"\r\n");
    }

    @Test
    void nullAndEmptyBecomeEmptyFields() {
      csv.writeRow(null, "", "x");

      assertThat(written()).isEqualTo(",,x\r\n");
    }
  }

  @Nested
  @DisplayName("spreadsheet formula guard")
  class FormulaGuard {

    @Test
    void aLeadingEqualsIsNeutralised() {
      csv.writeRow("=1+1");

      assertThat(written()).isEqualTo("'=1+1\r\n");
    }

    /**
     * The tab field is guarded but not quoted: only a comma, a quote or a line
     * break forces quoting, and a tab inside an unquoted field is valid CSV.
     */
    @Test
    void aLeadingPlusAtOrTabIsNeutralised() {
      csv.writeRow("+A1", "@SUM(A1)", "\tx");

      assertThat(written()).isEqualTo("'+A1,'@SUM(A1),'\tx\r\n");
    }

    @Test
    void anInteriorEqualsIsLeftAlone() {
      csv.writeRow("BLOCK=1");

      assertThat(written()).isEqualTo("BLOCK=1\r\n");
    }

    /** A minus sign also starts a formula, but it starts far more legitimate
     * values (negative volumes, hyphenated codes), so it is deliberately not
     * prefixed — this test documents that choice. */
    @Test
    void aLeadingMinusIsLeftAlone() {
      csv.writeRow("-500");

      assertThat(written()).isEqualTo("-500\r\n");
    }
  }

  @Nested
  @DisplayName("types")
  class Types {

    @Test
    void datesAreIso() {
      csv.writeRow(LocalDate.of(2026, 4, 10));

      assertThat(written()).isEqualTo("2026-04-10\r\n");
    }

    @Test
    void numbersCarryNoGroupingSeparator() {
      csv.writeRow(1234567, 1234.5);

      assertThat(written()).isEqualTo("1234567,1234.5\r\n");
    }
  }

  @Test
  void rowsAreSeparatedByCrlf() {
    csv.writeRow("h1", "h2");
    csv.writeRow("a", "b");

    assertThat(written()).isEqualTo("h1,h2\r\na,b\r\n");
  }
}
