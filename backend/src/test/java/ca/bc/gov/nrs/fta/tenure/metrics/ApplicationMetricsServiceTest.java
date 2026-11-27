package ca.bc.gov.nrs.fta.tenure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.metrics.ApplicationMetricsRules.Criteria;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA008 Export end to end below the controller, with the package mocked: the form's checks,
 * the package's errors, and the workbook — two sheets, Metrics then Holds, legacy's headings,
 * every cell a string, and legacy's file name.
 */
@DisplayName("Unit Test | FTA008 application metrics export")
class ApplicationMetricsServiceTest {

  private final ApplicationMetricsPackage pkg = mock(ApplicationMetricsPackage.class);
  // 2026-03-05 17:07:03 UTC is 09:07:03 in Vancouver.
  private final ApplicationMetricsService service = new ApplicationMetricsService(pkg,
      Clock.fixed(Instant.parse("2026-03-05T17:07:03Z"), ZoneId.of("America/Vancouver")));

  private static Criteria criteria(String org, String subFrom, String subTo) {
    return new Criteria(org, subFrom, subTo, null, null, null, null, null, null, null, null);
  }

  private static List<String> row(int size, String prefix) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      out.add(prefix + i);
    }
    return out;
  }

  /** Each part of the .xlsx, by name. */
  private static Map<String, String> parts(byte[] xlsx) throws IOException {
    Map<String, String> out = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        out.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return out;
  }

  /** The sheet's rows as their cells' text; a missing (blank) cell reads as null. */
  private static List<List<String>> cells(String sheetXml) {
    List<List<String>> rows = new ArrayList<>();
    Matcher row = Pattern.compile("<row r=\"(\\d+)\">(.*?)</row>").matcher(sheetXml);
    Pattern cell = Pattern.compile(
        "<c r=\"([A-Z]+)\\d+\" t=\"inlineStr\"><is><t xml:space=\"preserve\">(.*?)</t></is></c>");
    while (row.find()) {
      List<String> values = new ArrayList<>();
      Matcher c = cell.matcher(row.group(2));
      while (c.find()) {
        int col = c.group(1).charAt(c.group(1).length() - 1) - 'A'
            + (c.group(1).length() > 1 ? 26 : 0);
        while (values.size() < col) {
          values.add(null);
        }
        values.add(c.group(2));
      }
      rows.add(values);
    }
    return rows;
  }

  @Test
  void writesMetricsThenHoldsWithLegacysHeadingsAndRows() throws Exception {
    List<String> metric = row(19, "m");
    metric.set(1, null);
    metric.set(2, "A&B <C>");
    when(pkg.extract(any())).thenReturn(new ApplicationMetricsPackage.Extract(
        List.of(metric), List.of(row(7, "h"), row(7, "k")), ""));

    ApplicationMetricsService.Workbook wb = service.export(criteria("1835", "2025-01-01", null));

    assertThat(wb.filename()).isEqualTo("2026-03-05_090703_MetricsOutput.xlsx");
    Map<String, String> parts = parts(wb.content());
    assertThat(parts).containsKeys("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
        "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
        "xl/worksheets/sheet2.xml");
    // Every part is well-formed XML.
    for (String xml : parts.values()) {
      DocumentBuilderFactory.newInstance().newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    assertThat(parts.get("xl/workbook.xml"))
        .containsSubsequence("<sheet name=\"Metrics\"", "<sheet name=\"Holds\"");

    List<List<String>> metrics = cells(parts.get("xl/worksheets/sheet1.xml"));
    assertThat(metrics).hasSize(2);
    assertThat(metrics.get(0)).containsExactlyElementsOf(
        ApplicationMetricsRules.METRICS_COLUMNS.stream()
            .map(ApplicationMetricsRules.Column::heading).toList());
    assertThat(metrics.get(1)).hasSize(19);
    assertThat(metrics.get(1).get(0)).isEqualTo("m0");
    assertThat(metrics.get(1).get(1)).isNull();
    assertThat(metrics.get(1).get(2)).isEqualTo("A&amp;B &lt;C&gt;");
    assertThat(metrics.get(1).get(18)).isEqualTo("m18");

    List<List<String>> holds = cells(parts.get("xl/worksheets/sheet2.xml"));
    assertThat(holds).hasSize(3);
    assertThat(holds.get(0)).containsExactly("Tenure ID", "Cutting Permit ID", "Hold Start Date",
        "Hold End Date", "Hold Reason", "Hold Notes", "# Days on Hold");
    assertThat(holds.get(2)).containsExactly("k0", "k1", "k2", "k3", "k4", "k5", "k6");
  }

  @Test
  void passesTheCriteriaInMainlineOrder() {
    when(pkg.extract(any())).thenReturn(
        new ApplicationMetricsPackage.Extract(List.of(), List.of(), null));
    service.export(new Criteria(" 1835 ", "2025-01-01", "2025-01-31", "", null, null, null,
        null, null, "2025-06-01", "2025-06-30"));
    verify(pkg).extract(Arrays.asList("1835", "2025-01-01", "2025-01-31", null, null, null, null,
        null, null, "2025-06-01", "2025-06-30"));
  }

  @Test
  void noMatchesStillGivesTheWorkbookWithHeadingsOnly() throws Exception {
    when(pkg.extract(any())).thenReturn(new ApplicationMetricsPackage.Extract(
        Collections.emptyList(), Collections.emptyList(), ""));

    Map<String, String> parts = parts(service.export(criteria(null, null, null)).content());

    assertThat(cells(parts.get("xl/worksheets/sheet1.xml"))).hasSize(1);
    assertThat(cells(parts.get("xl/worksheets/sheet2.xml"))).hasSize(1);
  }

  @Test
  void theFormsErrorsAreA400AndThePackageIsNotCalled() {
    assertThatThrownBy(() -> service.export(criteria(null, "2025-02-01", "2025-01-01")))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(
              "Submission Date From must be less than or equal to Submission Date To.");
        });
    assertThatThrownBy(() -> service.export(criteria("DCC", null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining(ApplicationMetricsRules.DISTRICT_INVALID);
    verify(pkg, never()).extract(any());
  }

  @Test
  void thePackagesErrorIsA500InItsWords() {
    when(pkg.extract(any())).thenReturn(new ApplicationMetricsPackage.Extract(List.of(),
        List.of(),
        "sil.web.usr.database.unexpected:FTA_008_METRICS_EXTRACT,GET_HOLDS,-1722,ORA-01722;"));
    assertThatThrownBy(() -> service.export(criteria(null, null, null)))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
          assertThat(e.getReason()).isEqualTo("An unexpected error has occurred in"
              + " package/procedure FTA_008_METRICS_EXTRACT, GET_HOLDS - -1722, ORA-01722.");
        });
  }
}
