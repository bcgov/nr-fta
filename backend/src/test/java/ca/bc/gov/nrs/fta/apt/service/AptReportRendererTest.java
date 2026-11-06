package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | AptReportRenderer")
class AptReportRendererTest {

  @Test
  @DisplayName("a report whose template isn't on the classpath yet is 501, without a connection")
  void missingTemplateIs501() {
    DataSource dataSource = mock(DataSource.class);
    AptReportRenderer renderer = new AptReportRenderer(dataSource);

    assertThat(renderer.available(AptReportDefinition.APTR041.template())).isFalse();
    assertThatThrownBy(() -> renderer.render(AptReportDefinition.APTR041.template(), Map.of()))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
          assertThat(e.getReason()).contains("APTR041_master");
        });
    verifyNoInteractions(dataSource);
  }

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
