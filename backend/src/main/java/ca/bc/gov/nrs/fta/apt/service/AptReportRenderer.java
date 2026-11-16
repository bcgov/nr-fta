package ca.bc.gov.nrs.fta.apt.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.repo.ReportResource;
import net.sf.jasperreports.repo.RepositoryService;
import net.sf.jasperreports.repo.Resource;
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
 * A report whose template is missing answers <b>501 Not Implemented</b>.
 *
 * <p>The templates are APT2's own, exported from its JasperReports Server and cleaned for this
 * (see the README beside them), their queries rewritten from APT2's {@code THE.APT_RPTAPTR*}
 * procedures to plain SQL on the tables. Each runs against a live database connection. Their
 * subreports are
 * named {@code $P{SUBREPORT_DIR} + "<name>" + $P{SUBREPORT_EXT}}; this renderer's own
 * repository resolves such a name to {@code reports/apt/<name>.jrxml}, compiled and cached like
 * the reports themselves, so nothing is loaded from outside that directory.
 *
 * <p>Each input arrives as text and is converted to the type the template declares for that
 * parameter ({@code String}, a date type, or a number); a parameter the template does not
 * declare is passed through as text, where JasperReports ignores it.
 */
@Component
public class AptReportRenderer {

  /** Where the templates live on the classpath. */
  static final String TEMPLATE_DIR = "reports/apt/";

  /** A template's name: letters, digits and underscores only, so a name is never a path. */
  private static final Pattern TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9_]+");

  private final DataSource dataSource;
  private final Map<String, JasperReport> compiled = new ConcurrentHashMap<>();
  private final SimpleJasperReportsContext context;

  public AptReportRenderer(DataSource dataSource) {
    this.dataSource = dataSource;
    this.context = new SimpleJasperReportsContext(DefaultJasperReportsContext.getInstance());
    context.setExtensions(RepositoryService.class, List.of(new TemplateRepository()));
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
      Map<String, Object> values = typed(report, params);
      // Subreports are found by name (TemplateRepository); the templates add these around it.
      values.put("SUBREPORT_DIR", "");
      values.put("SUBREPORT_EXT", ".jrxml");
      JasperPrint print = JasperFillManager.getInstance(context).fill(report, values, connection);
      return JasperExportManager.exportReportToPdf(print);
    } catch (JRException e) {
      throw new IllegalStateException("Could not render APT report " + template + ".", e);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }
  }

  /** Whether the template has been added to the classpath. */
  public boolean available(String template) {
    return TEMPLATE_NAME.matcher(template).matches()
        && new ClassPathResource(TEMPLATE_DIR + template + ".jrxml").exists();
  }

  private JasperReport report(String template) {
    if (!available(template)) {
      throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
          "The " + template + " report template has not been added yet.");
    }
    return compiled(template);
  }

  /** A template compiled, once; subreports come through here too. */
  JasperReport compiled(String template) {
    return compiled.computeIfAbsent(template, this::compile);
  }

  private JasperReport compile(String template) {
    String path = TEMPLATE_DIR + template + ".jrxml";
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return JasperCompileManager.getInstance(context).compile(in);
    } catch (IOException | JRException e) {
      throw new IllegalStateException("Could not compile " + path, e);
    }
  }

  /**
   * The template name a subreport location names: the last path segment, without its
   * {@code .jrxml} or {@code .jasper} extension. Null when that is not a template name.
   */
  static String templateName(String location) {
    if (location == null) {
      return null;
    }
    String name = location.substring(Math.max(location.lastIndexOf('/'),
        location.lastIndexOf('\\')) + 1);
    name = name.replaceFirst("\\.(jrxml|jasper)$", "");
    return TEMPLATE_NAME.matcher(name).matches() ? name : null;
  }

  /**
   * The only repository the templates see: a subreport location resolves to the compiled
   * template of that name in {@link #TEMPLATE_DIR}, and nothing else resolves.
   */
  private final class TemplateRepository implements RepositoryService {

    @Override
    public Resource getResource(String uri) {
      return getResource(uri, ReportResource.class);
    }

    @Override
    public <K extends Resource> K getResource(String uri, Class<K> resourceType) {
      String name = templateName(uri);
      if (!resourceType.isAssignableFrom(ReportResource.class) || name == null
          || !available(name)) {
        return null;
      }
      ReportResource resource = new ReportResource();
      resource.setName(name);
      resource.setReport(compiled(name));
      return resourceType.cast(resource);
    }

    @Override
    public void saveResource(String uri, Resource resource) {
      throw new UnsupportedOperationException("APT report templates are read-only.");
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
