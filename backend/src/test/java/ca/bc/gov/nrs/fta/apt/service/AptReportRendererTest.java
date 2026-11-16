package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRElementGroup;
import net.sf.jasperreports.engine.JRGroup;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRSubreport;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | AptReportRenderer")
class AptReportRendererTest {

  @Test
  @DisplayName("a report whose template isn't on the classpath yet is 501, without a connection")
  void missingTemplateIs501() {
    DataSource dataSource = mock(DataSource.class);
    AptReportRenderer renderer = new AptReportRenderer(dataSource);

    assertThat(renderer.available("APTR099_no_such_report")).isFalse();
    assertThatThrownBy(() -> renderer.render("APTR099_no_such_report", Map.of()))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
          assertThat(e.getReason()).contains("APTR099_no_such_report");
        });
    verifyNoInteractions(dataSource);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(AptReportDefinition.class)
  @DisplayName("every report's template is here and compiles, with each subreport it names")
  void everyTemplateCompiles(AptReportDefinition report) {
    AptReportRenderer renderer = new AptReportRenderer(mock(DataSource.class));
    assertThat(renderer.available(report.template())).isTrue();

    Deque<String> pending = new ArrayDeque<>(List.of(report.template()));
    Set<String> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      String name = pending.pop();
      if (!seen.add(name)) {
        continue;
      }
      assertThat(renderer.available(name)).as("subreport %s", name).isTrue();
      for (String sub : subreports(renderer.compiled(name))) {
        pending.push(sub);
      }
    }
  }

  @Test
  @DisplayName("each report declares the parameters its definition sends")
  void parametersMatchTheTemplates() {
    AptReportRenderer renderer = new AptReportRenderer(mock(DataSource.class));
    for (AptReportDefinition report : AptReportDefinition.values()) {
      Set<String> declared = new HashSet<>();
      for (JRParameter p : renderer.compiled(report.template()).getParameters()) {
        declared.add(p.getName());
      }
      // APT2's templates print the caller as either user or User; the service sends both.
      assertThat(declared).as(report.name()).containsAnyOf("user", "User");
      assertThat(declared).as(report.name()).containsAll(report.paramNames());
    }
  }

  @Test
  @DisplayName("a subreport location resolves to a template name, never a path")
  void templateNames() {
    assertThat(AptReportRenderer.templateName("APT_RPT_HEADER.jrxml")).isEqualTo("APT_RPT_HEADER");
    // A "repo:" prefix is not part of any template name, so it does not resolve.
    assertThat(AptReportRenderer.templateName("repo:APT_RPT_HEADER.jasper")).isNull();
    assertThat(AptReportRenderer.templateName("I:\\Reports\\APT_RPTAPTR011_AAC.jasper"))
        .isEqualTo("APT_RPTAPTR011_AAC");
    assertThat(AptReportRenderer.templateName("../../application.yml")).isNull();
    assertThat(AptReportRenderer.templateName(null)).isNull();
  }

  /** The template names a report's subreport elements name, from their literal parts. */
  private static List<String> subreports(JasperReport report) {
    List<String> names = new ArrayList<>();
    for (JRBand band : bands(report)) {
      collect(band.getElements(), names);
    }
    return names;
  }

  private static List<JRBand> bands(JasperReport report) {
    List<JRBand> all = new ArrayList<>();
    for (JRBand b : new JRBand[] {report.getTitle(), report.getPageHeader(),
        report.getColumnHeader(), report.getColumnFooter(), report.getPageFooter(),
        report.getLastPageFooter(), report.getSummary(), report.getNoData(),
        report.getBackground()}) {
      if (b != null) {
        all.add(b);
      }
    }
    if (report.getDetailSection() != null && report.getDetailSection().getBands() != null) {
      all.addAll(List.of(report.getDetailSection().getBands()));
    }
    for (JRGroup g : report.getGroups() == null ? new JRGroup[0] : report.getGroups()) {
      for (var section : new net.sf.jasperreports.engine.JRSection[] {
          g.getGroupHeaderSection(), g.getGroupFooterSection()}) {
        if (section != null && section.getBands() != null) {
          all.addAll(List.of(section.getBands()));
        }
      }
    }
    return all;
  }

  private static void collect(JRElement[] elements, List<String> names) {
    if (elements == null) {
      return;
    }
    for (JRElement e : elements) {
      if (e instanceof JRSubreport sub && sub.getExpression() != null) {
        Matcher m = QUOTED.matcher(sub.getExpression().getText());
        while (m.find()) {
          names.add(m.group(1));
        }
      } else if (e instanceof JRElementGroup group) {
        collect(group.getElements(), names);
      }
    }
  }

  private static final Pattern QUOTED = Pattern.compile("\"([A-Za-z0-9_]+)\"");

  @Test
  @DisplayName("text inputs become the type the template declares")
  void convertsToDeclaredType() {
    assertThat(AptReportRenderer.convert("2026-03-31", java.util.Date.class))
        .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 3, 31)));
    assertThat(AptReportRenderer.convert("2026-03-31", LocalDate.class))
        .isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(AptReportRenderer.convert("42", Long.class)).isEqualTo(42L);
    assertThat(AptReportRenderer.convert("9", Integer.class)).isEqualTo(9);
    assertThat(AptReportRenderer.convert("9", BigDecimal.class)).isEqualTo(new BigDecimal("9"));
    assertThat(AptReportRenderer.convert("IDIR\\X", String.class)).isEqualTo("IDIR\\X");
    assertThat(AptReportRenderer.convert("as is", null)).isEqualTo("as is");
  }

  @Test
  @DisplayName("an input that doesn't fit the declared type is 400")
  void badInputIs400() {
    assertThatThrownBy(() -> AptReportRenderer.convert("soon", java.util.Date.class))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
