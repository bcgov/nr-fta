package ca.bc.gov.nrs.fta.shared.csv;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Turns a search result into a downloadable CSV response.
 *
 * <p>The export is deliberately unbounded — a user asking for "all results"
 * gets every matching row — so the response is streamed: rows are written to
 * the socket as the database produces them and the server never holds the
 * result set. A large export therefore costs a long-lived connection rather
 * than heap.
 *
 * <p>No {@code Content-Length} is sent (the row count isn't known up front), so
 * the browser shows an indeterminate download. That is the trade for not
 * counting, then re-querying, before the first byte.
 */
public final class CsvExport {

  private CsvExport() {}

  /**
   * A CSV download whose rows {@code body} writes.
   *
   * @param baseName file name stem; the download is {@code <baseName>-<date>.csv}
   * @param body     writes the header row, then one row per result
   */
  public static ResponseEntity<StreamingResponseBody> response(
      String baseName, Consumer<CsvWriter> body) {

    String filename = baseName + "-" + LocalDate.now() + ".csv";

    StreamingResponseBody stream = outputStream -> {
      // Buffered so each row isn't its own write to the socket.
      Writer writer = new BufferedWriter(
          new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 32 * 1024);
      // UTF-8 byte order mark: without it Excel reads the file as the local
      // code page and mangles accented client names.
      writer.write('\uFEFF');
      body.accept(new CsvWriter(writer));
      flushQuietly(writer);
    };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(filename).build());
    // text/csv with the charset spelled out, so a browser that sniffs doesn't
    // guess something else.
    headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
    // The file reflects a query run now; a cached copy would be wrong.
    headers.setCacheControl("no-store");

    return ResponseEntity.ok().headers(headers).body(stream);
  }

  /**
   * Flushes the writer, ignoring a failure caused by the client having gone
   * away — by then the rows are written and there is nobody to report to.
   */
  private static void flushQuietly(Writer writer) {
    try {
      writer.flush();
    } catch (IOException e) {
      // Client disconnected mid-download; nothing to salvage.
    }
  }
}
