package ca.bc.gov.nrs.fta.tenure.metrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The FTA008 workbook as {@code Fta008MetricsSrchAction} built it with POI's
 * {@code XSSFWorkbook}: plain sheets, a heading row, then one row per record, every cell a string
 * ({@code setCellValue(String)}; a null value left the cell blank), no styling, widths or
 * freezing.
 *
 * <p>Written here as the few SpreadsheetML parts such a workbook needs (inline-string cells and
 * the default style), as the app has no spreadsheet library of its own (FTA001's workbook comes
 * out of Jasper).
 */
final class MetricsWorkbook {

  /** A worksheet: its tab name, its heading row, and its rows (each as long as the headings). */
  record Sheet(String name, List<String> headings, List<List<String>> rows) {}

  private static final String XML =
      "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
  private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String REL_NS =
      "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String PKG_REL_NS =
      "http://schemas.openxmlformats.org/package/2006/relationships";
  private static final String TYPE_NS =
      "http://schemas.openxmlformats.org/package/2006/content-types";
  private static final String SHEET_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml";

  /** The one cell format every cell uses: Calibri 11, POI's default. */
  private static final String STYLES = XML
      + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
      + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/>"
      + "</font></fonts>"
      + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>"
      + "<fill><patternFill patternType=\"gray125\"/></fill></fills>"
      + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border>"
      + "</borders>"
      + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/>"
      + "</cellStyleXfs>"
      + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\""
      + " xfId=\"0\"/></cellXfs>"
      + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/>"
      + "</cellStyles>"
      + "</styleSheet>";

  private MetricsWorkbook() {}

  /** The .xlsx bytes for these sheets, in tab order. */
  static byte[] xlsx(List<Sheet> sheets) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
      put(zip, "_rels/.rels", XML + "<Relationships xmlns=\"" + PKG_REL_NS + "\">"
          + "<Relationship Id=\"rId1\" Type=\"" + REL_NS + "/officeDocument\""
          + " Target=\"xl/workbook.xml\"/></Relationships>");
      put(zip, "xl/workbook.xml", workbook(sheets));
      put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
      put(zip, "xl/styles.xml", STYLES);
      for (int i = 0; i < sheets.size(); i++) {
        put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", worksheet(sheets.get(i)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private static void put(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static String contentTypes(int sheets) {
    StringBuilder sb = new StringBuilder(XML).append("<Types xmlns=\"").append(TYPE_NS)
        .append("\">")
        .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package"
            + ".relationships+xml\"/>")
        .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd"
            + ".openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd"
            + ".openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
    for (int i = 1; i <= sheets; i++) {
      sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
          .append(".xml\" ContentType=\"").append(SHEET_TYPE).append("\"/>");
    }
    return sb.append("</Types>").toString();
  }

  private static String workbook(List<Sheet> sheets) {
    StringBuilder sb = new StringBuilder(XML).append("<workbook xmlns=\"").append(MAIN_NS)
        .append("\" xmlns:r=\"").append(REL_NS).append("\"><sheets>");
    for (int i = 0; i < sheets.size(); i++) {
      sb.append("<sheet name=\"").append(escape(sheets.get(i).name()))
          .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1)
          .append("\"/>");
    }
    return sb.append("</sheets></workbook>").toString();
  }

  private static String workbookRels(int sheets) {
    StringBuilder sb = new StringBuilder(XML).append("<Relationships xmlns=\"")
        .append(PKG_REL_NS).append("\">");
    for (int i = 1; i <= sheets; i++) {
      sb.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL_NS)
          .append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
    }
    sb.append("<Relationship Id=\"rId").append(sheets + 1).append("\" Type=\"").append(REL_NS)
        .append("/styles\" Target=\"styles.xml\"/>");
    return sb.append("</Relationships>").toString();
  }

  private static String worksheet(Sheet sheet) {
    StringBuilder sb = new StringBuilder(XML).append("<worksheet xmlns=\"").append(MAIN_NS)
        .append("\"><sheetData>");
    row(sb, 1, sheet.headings());
    int r = 2;
    for (List<String> values : sheet.rows()) {
      row(sb, r++, values);
    }
    return sb.append("</sheetData></worksheet>").toString();
  }

  private static void row(StringBuilder sb, int r, List<String> values) {
    sb.append("<row r=\"").append(r).append("\">");
    for (int c = 0; c < values.size(); c++) {
      String v = values.get(c);
      if (v == null) {
        continue;
      }
      sb.append("<c r=\"").append(column(c)).append(r).append("\" t=\"inlineStr\"><is>")
          .append("<t xml:space=\"preserve\">").append(escape(v)).append("</t></is></c>");
    }
    sb.append("</row>");
  }

  /** The column letters for a 0-based index: A … Z, AA … */
  static String column(int index) {
    StringBuilder sb = new StringBuilder();
    for (int n = index + 1; n > 0; n = (n - 1) / 26) {
      sb.insert(0, (char) ('A' + (n - 1) % 26));
    }
    return sb.toString();
  }

  /** XML-escaped, with the characters XML 1.0 cannot carry (most control characters) dropped. */
  static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    s.codePoints().forEach(cp -> {
      switch (cp) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '"' -> sb.append("&quot;");
        default -> {
          boolean legal = cp == 0x9 || cp == 0xA || cp == 0xD
              || (cp >= 0x20 && cp <= 0xD7FF)
              || (cp >= 0xE000 && cp <= 0xFFFD)
              || (cp >= 0x10000 && cp <= 0x10FFFF);
          if (legal) {
            sb.appendCodePoint(cp);
          }
        }
      }
    });
    return sb.toString();
  }
}
