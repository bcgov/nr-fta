package ca.bc.gov.nrs.fta.range.service;

import ca.bc.gov.nrs.fta.billing.service.JasperTemplates;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

/**
 * Legacy's {@code FTA001_RangeTenureExport} JasperServer report unit: a main JRXML whose ten
 * group headers each hold one subreport, one per worksheet, exported to Excel.
 *
 * <p>The JRXML under {@code reports/fta001/} is legacy's, with only what JasperServer supplied
 * swapped for parameters: each subreport's {@code plsql} query is gone, and the main report hands
 * subreport {@code n} the compiled template {@code SUBREPORT_n} and the rows {@code DATA_n} (the
 * cursor of {@code FTA_001R_TENR_SRCH_CSV<n+1>}). Sheet names, column headings, order and cell
 * patterns all come from the JRXML unchanged.
 */
public final class RangeTenureExportReport {

  /** One per worksheet, in workbook order. */
  public static final int SHEETS = 10;

  static final String MAIN = "reports/fta001/FTA001_RangeTenureExport.jrxml";

  static final String SUBREPORT = "reports/fta001/FTA001RangeExport_subreport%d.jrxml";

  public static final String FILENAME = "FTA001_RangeTenureExport.xlsx";

  public static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private RangeTenureExportReport() {}

  /**
   * Fills the workbook.
   *
   * @param params the report's {@code p_*} inputs (shown nowhere; passed on as legacy did)
   * @param sheets one data source per worksheet, {@link #SHEETS} of them, in order; their field
   *               names are the subreports' (the procedures' column names)
   */
  public static byte[] xlsx(Map<String, Object> params, List<? extends JRDataSource> sheets) {
    if (sheets.size() != SHEETS) {
      throw new IllegalArgumentException("Expected " + SHEETS + " sheets, got " + sheets.size());
    }
    Map<String, Object> all = new HashMap<>(params);
    for (int i = 0; i < SHEETS; i++) {
      all.put("SUBREPORT_" + i, JasperTemplates.get(SUBREPORT.formatted(i)));
      all.put("DATA_" + i, sheets.get(i));
    }
    try {
      // One record, so each group header (one per sheet) prints once; the main report has no
      // detail band of its own.
      JasperPrint print = JasperFillManager.fillReport(
          JasperTemplates.get(MAIN), all, new JREmptyDataSource(1));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      JRXlsxExporter exporter = new JRXlsxExporter();
      exporter.setExporterInput(new SimpleExporterInput(print));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
      exporter.exportReport();
      return out.toByteArray();
    } catch (JRException e) {
      throw new IllegalStateException("Could not produce " + MAIN, e);
    }
  }
}
