package ca.bc.gov.nrs.fta.apt.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Renders APT's PDF reports inside the backend with the embedded JasperReports library, as
 * the FTA402 certificate is ({@code mark/service/MarkCertificateReport}). There is no
 * external report server.
 *
 * <p>Each report is a JRXML template on the classpath at
 * {@code reports/apt/<template>.jrxml}, where the template name is
 * {@link AptReportDefinition#template()}. Templates are compiled on first use and cached.
 * A report whose template has not been added yet answers <b>501 Not Implemented</b>, so the
 * rest of APT works without them.
 *
 * <p>The template is filled against a live database connection, so it runs its own query.
 * Each input arrives as text and is converted to the type the template declares for that
 * parameter ({@code String}, a date type, or a number); a parameter the template does not
 * declare is passed through as text, where JasperReports ignores it.
 */
@Component
public class AptReportRenderer {

  /** Where the templates live on the classpath. */
  static final String TEMPLATE_DIR = "reports/apt/";

  private final DataSource dataSource;
  private final Map<String, JasperReport> compiled = new ConcurrentHashMap<>();

  public AptReportRenderer(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * The report as a PDF.
   *
   * @param template the template's name, without directory or {@code .jrxml}
   * @param params   the report's parameters by name, as text
   * @throws ResponseStatusException 501 when the template is not on the classpath
   */
  public byte[] render(String template, Map<String, String> params) {
    JasperReport report = report(template);
    Connection connection = DataSourceUtils.getConnection(dataSource);
    try {
      JasperPrint print = JasperFillManager.fillReport(report, typed(report, params), connection);
      return JasperExportManager.exportReportToPdf(print);
    } catch (JRException e) {
      throw new IllegalStateException("Could not render APT report " + template + ".", e);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  /** Whether the template has been added to the classpath. */
  public boolean available(String template) {
    return new ClassPathResource(TEMPLATE_DIR + template + ".jrxml").exists();
  }

  private JasperReport report(String template) {
    if (!available(template)) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
          "The " + template + " report template has not been added yet.");
    }
    return compiled.computeIfAbsent(template, AptReportRenderer::compile);
  }

  private static JasperReport compile(String template) {
    String path = TEMPLATE_DIR + template + ".jrxml";
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JasperCompileManager.compileReport(in);
    } catch (IOException | JRException e) {
      throw new IllegalStateException("Could not compile " + path, e);
    }
  }

  /** The parameters converted to the types the template declares. */
  static Map<String, Object> typed(JasperReport report, Map<String, String> params) {
    Map<String, Class<?>> declared = new HashMap<>();
    for (JRParameter p : report.getParameters()) {
      if (!p.isSystemDefined()) {
        declared.put(p.getName(), p.getValueClass());
      }
    }
    Map<String, Object> out = new HashMap<>();
    params.forEach((name, value) -> out.put(name, convert(value, declared.get(name))));
    return out;
  }

  /** One text value as {@code type}; text when the type is unknown or is text. */
  static Object convert(String value, Class<?> type) {
    if (value == null || type == null || type == String.class || type == Object.class) {
      return value;
    }
    try {
      if (type == java.util.Date.class || type == java.sql.Date.class) {
        return java.sql.Date.valueOf(LocalDate.parse(value));
      }
      if (type == java.sql.Timestamp.class) {
        return java.sql.Timestamp.valueOf(LocalDate.parse(value).atStartOfDay());
      }
      if (type == LocalDate.class) {
        return LocalDate.parse(value);
      }
      if (type == Integer.class) {
        return Integer.valueOf(value);
      }
      if (type == Long.class) {
        return Long.valueOf(value);
      }
      if (type == BigDecimal.class || type == Number.class) {
        return new BigDecimal(value);
      }
      if (type == Double.class) {
        return Double.valueOf(value);
      }
    } catch (RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "'" + value + "' is not a valid " + type.getSimpleName() + ".");
    }
    return value;
  }
}
