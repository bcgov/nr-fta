package ca.bc.gov.nrs.fta.billing.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;

/** The range billing reports' JRXML, compiled once each, and filled to PDF. */
final class JasperTemplates {

  private static final Map<String, JasperReport> COMPILED = new ConcurrentHashMap<>();

  private JasperTemplates() {}

  static JasperReport get(String path) {
    return COMPILED.computeIfAbsent(path, JasperTemplates::compile);
  }

  /** Fills {@code path} with {@code rows} and the parameters, as a PDF. */
  static byte[] pdf(String path, Map<String, Object> params, List<Map<String, ?>> rows) {
    try {
      JasperPrint print = JasperFillManager.fillReport(
          get(path), params, new JRMapCollectionDataSource(rows));
      return JasperExportManager.exportReportToPdf(print);
    } catch (JRException e) {
      throw new IllegalStateException("Could not produce " + path, e);
    }
  }

  private static JasperReport compile(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JasperCompileManager.compileReport(in);
    } catch (IOException | JRException e) {
      throw new IllegalStateException("Could not compile " + path, e);
    }
  }
}
