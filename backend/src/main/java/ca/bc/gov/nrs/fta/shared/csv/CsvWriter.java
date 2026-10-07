package ca.bc.gov.nrs.fta.shared.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes RFC 4180 CSV to a {@link Writer}, a row at a time.
 *
 * <p>Row at a time on purpose: a search export has no row limit, so nothing may
 * hold the whole result set in memory. Callers pair this with a streaming query
 * (a {@code RowCallbackHandler}) so each row is written and forgotten.
 *
 * <p>{@link IOException} is rethrown as {@link UncheckedIOException} so this can
 * be called from inside a row handler, whose signature cannot throw it. The
 * usual cause is the client disconnecting mid-download, which aborts the query
 * as intended.
 */
public final class CsvWriter {

  /** Dates go out ISO, the same way the JSON API renders them. */
  private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private static final String LINE_END = "\r\n";

  private final Writer out;

  CsvWriter(Writer out) {
    this.out = out;
  }

  /** Writes one row, escaping each value. */
  public void writeRow(Object... values) {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        line.append(',');
      }
      line.append(escape(values[i]));
    }
    line.append(LINE_END);
    try {
      out.write(line.toString());
    } catch (IOException e) {
      throw new UncheckedIOException("Writing CSV row failed", e);
    }
  }

  /**
   * One CSV field: the value as text, quoted when it has to be.
   *
   * <p>A field is quoted when it contains a comma, a quote or a line break, and
   * an embedded quote is doubled — RFC 4180.
   *
   * <p>A leading {@code = + @} or tab is also prefixed with an apostrophe. A
   * spreadsheet treats those as the start of a formula, so a value like
   * {@code =1+1} arriving from a client name field would be evaluated on open
   * rather than displayed. The apostrophe is a spreadsheet escape and is not
   * part of the value.
   */
  private static String escape(Object value) {
    if (value == null) {
      return "";
    }
    String text = switch (value) {
      case LocalDate d -> d.format(DATE);
      case LocalDateTime d -> d.toLocalDate().format(DATE);
      default -> String.valueOf(value);
    };
    if (text.isEmpty()) {
      return "";
    }
    if ("=+@\t".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    boolean mustQuote = text.indexOf(',') >= 0
        || text.indexOf('"') >= 0
        || text.indexOf('\n') >= 0
        || text.indexOf('\r') >= 0;
    if (!mustQuote) {
      return text;
    }
    return '"' + text.replace("\"", "\"\"") + '"';
  }
}
