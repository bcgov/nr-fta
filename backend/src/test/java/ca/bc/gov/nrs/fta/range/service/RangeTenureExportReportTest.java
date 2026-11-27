package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRStaticText;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported FTA001 Range Tenure Export compiles, fills from sample rows and exports a workbook
 * with legacy's ten sheets, each headed by its subreport's column headings in order.
 */
@DisplayName("Unit Test | FTA001 range tenure export workbook")
class RangeTenureExportReportTest {

  /**
   * The sheet names, in order. Where the main JRXML and a subreport name a sheet differently
   * (FTA_RANGE_FULL_LAND_BASE, FTA_RANGE_CLIENTS) the subreport's heading cell names it.
   */
  private static final List<String> SHEET_NAMES = List.of(
      "FTA_RANGE_BASIC",
      "FTA_RANGE_NOTES",
      "FTA_RANGE_CONDITIONS",
      "FTA_RANGE_USAGE",
      "FTA_RANGE_NON_USE",
      "FTA_RANGE_LAND_BASE",
      "FTA_ACTIVE_LAND_BASE",
      "FTA_RANGE_CLIENT",
      "FTA_RANGE_LICENSEE",
      "FTA_NON_USE_PKG");

  private static JasperReport subreport(int i) {
    return JasperTemplates.get(RangeTenureExportReport.SUBREPORT.formatted(i));
  }

  /** A sample row for subreport {@code i}: a value of the declared class for every field. */
  private static Map<String, Object> sampleRow(int i) {
    Map<String, Object> row = new HashMap<>();
    for (JRField f : subreport(i).getFields()) {
      String type = f.getValueClassName();
      Object v;
      if (type.equals("java.lang.Number")) {
        v = new BigDecimal("1234");
      } else if (type.equals("java.util.Date")) {
        v = Timestamp.valueOf("2026-03-15 00:00:00");
      } else {
        v = f.getName().equals("forest_file_id") ? "RAN077777" : "v_" + f.getName();
      }
      row.put(f.getName(), v);
    }
    return row;
  }

  /** The column heading texts of subreport {@code i}, left to right. */
  private static List<String> headings(int i) {
    JRBand band = subreport(i).getColumnHeader();
    List<JRElement> elements = new ArrayList<>(List.of(band.getElements()));
    elements.sort(Comparator.comparingInt(JRElement::getX));
    List<String> out = new ArrayList<>();
    for (JRElement e : elements) {
      if (e instanceof JRStaticText t) {
        out.add(t.getText());
      }
    }
    return out;
  }

  private static Map<String, String> unzip(byte[] xlsx) throws IOException {
    Map<String, String> files = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return files;
  }

  /** The text cells of one row of a sheet, in column order (inline or shared strings). */
  private static List<String> rowText(String sheetXml, List<String> shared, int rowNum) {
    Matcher row = Pattern.compile("<row r=\"" + rowNum + "\"[^>]*>(.*?)</row>", Pattern.DOTALL)
        .matcher(sheetXml);
    List<String> out = new ArrayList<>();
    if (!row.find()) {
      return out;
    }
    Matcher c = Pattern.compile("<c ([^>]*)>(.*?)</c>", Pattern.DOTALL)
        .matcher(row.group(1));
    while (c.find()) {
      String body = c.group(2);
      Matcher t = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(body);
      Matcher v = Pattern.compile("<v>(.*?)</v>").matcher(body);
      if (t.find()) {
        out.add(unescape(t.group(1)));
      } else if (c.group(1).contains("t=\"s\"") && v.find()) {
        out.add(shared.get(Integer.parseInt(v.group(1))));
      } else if (v.find()) {
        out.add(v.group(1));
      }
    }
    return out;
  }

  private static String unescape(String s) {
    return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&");
  }

  private static List<String> sharedStrings(Map<String, String> files) {
    List<String> out = new ArrayList<>();
    String xml = files.get("xl/sharedStrings.xml");
    if (xml == null) {
      return out;
    }
    Matcher si = Pattern.compile("<si>(.*?)</si>", Pattern.DOTALL).matcher(xml);
    while (si.find()) {
      Matcher t = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(si.group(1));
      StringBuilder sb = new StringBuilder();
      while (t.find()) {
        sb.append(unescape(t.group(1)));
      }
      out.add(sb.toString());
    }
    return out;
  }

  private static List<String> sheetNames(Map<String, String> files) {
    Matcher m = Pattern.compile("<sheet [^>]*name=\"([^\"]+)\"").matcher(files.get("xl/workbook.xml"));
    List<String> out = new ArrayList<>();
    while (m.find()) {
      out.add(m.group(1));
    }
    return out;
  }

  private static byte[] render(boolean withRows) {
    List<JRMapCollectionDataSource> sheets = new ArrayList<>();
    for (int i = 0; i < RangeTenureExportReport.SHEETS; i++) {
      List<Map<String, ?>> rows = withRows ? List.of(sampleRow(i)) : List.of();
      sheets.add(new JRMapCollectionDataSource(rows));
    }
    Map<String, Object> params = new HashMap<>();
    params.put("p_user_id", "IDIR\\JSMITH");
    params.put("p_forest_file_id", "RAN077777");
    return RangeTenureExportReport.xlsx(params, sheets);
  }

  @Test
  void everySubreportHasColumnHeadings() {
    for (int i = 0; i < RangeTenureExportReport.SHEETS; i++) {
      assertThat(headings(i)).as("subreport %d", i).isNotEmpty().contains("forest_file_id");
    }
    assertThat(headings(1)).containsExactly("forest_file_id", "note");
  }

  @Test
  void workbookHasLegacySheetsHeadingsAndRows() throws Exception {
    Map<String, String> files = unzip(render(true));
    List<String> shared = sharedStrings(files);

    assertThat(sheetNames(files)).containsExactlyElementsOf(SHEET_NAMES);
    for (int i = 0; i < RangeTenureExportReport.SHEETS; i++) {
      String sheet = files.get("xl/worksheets/sheet" + (i + 1) + ".xml");
      assertThat(sheet).as("sheet %d", i + 1).isNotNull();
      // The page margins leave row 1 and column A empty, as the JRXML lays them out.
      assertThat(rowText(sheet, shared, 1)).as("margin row of %s", SHEET_NAMES.get(i)).isEmpty();
      List<String> expected = new ArrayList<>(headings(i));
      if (i == 6) {
        // Legacy's own layout: this heading's box is 30px wide with no keep-full-text property,
        // so Jasper cuts it to what fits, as it did on the report server.
        expected.set(expected.size() - 1, "range");
      }
      assertThat(rowText(sheet, shared, 2)).as("headings of %s", SHEET_NAMES.get(i))
          .containsExactlyElementsOf(expected);
      assertThat(rowText(sheet, shared, 3)).as("row of %s", SHEET_NAMES.get(i))
          .startsWith("RAN077777");
    }
  }

  @Test
  void datesAndNumbersKeepLegacyCellFormats() throws Exception {
    Map<String, String> files = unzip(render(true));
    String styles = files.get("xl/styles.xml");
    // The JRXML's yyyy-MM-dd dates, ###0 whole numbers and ###0.0 land base areas.
    assertThat(styles).contains("formatCode=\"yyyy-MM-dd\"").contains("formatCode=\"#0\"")
        .contains("formatCode=\"#0.0\"");
    // Detected cell types: the dates and numbers are numeric cells, not text.
    List<String> basic = rowText(files.get("xl/worksheets/sheet1.xml"), sharedStrings(files), 3);
    assertThat(basic.get(2)).isEqualTo("46096.0"); // file_status_date 2026-03-15
    assertThat(basic.get(4)).isEqualTo("1234.0"); // forest_region
  }

  @Test
  void workbookRendersWithNoRows() throws Exception {
    Map<String, String> files = unzip(render(false));
    assertThat(sheetNames(files)).containsExactlyElementsOf(SHEET_NAMES);
  }
}
