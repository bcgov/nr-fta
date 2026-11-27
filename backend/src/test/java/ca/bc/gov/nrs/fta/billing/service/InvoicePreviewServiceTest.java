package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | InvoicePreviewService (FTA695)")
class InvoicePreviewServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final InvoicePreviewService service = new InvoicePreviewService(jdbc);

  private static final Map<String, Object> CLIENT = Map.of(
      "CLIENT_TYPE", "A", "CLIENT_NAME", "ACME RANCH", "CLIENT_TYPE_CODE", "C");

  @SuppressWarnings("unchecked")
  private void tenure(String status, List<Map<String, Object>> clients, List<?> provisions) {
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class)))
        .thenReturn(1L);
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(
        List.of(Map.of("FILE_TYPE_CODE", "E01", "FILE_STATUS_ST", status,
            "FILE_TYPE_DESCRIPTION", "Grazing Licence")),
        clients);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn((List<Object>) provisions);
  }

  private static void refusedWith(Runnable call, String reason) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode())
              .isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(reason);
        });
  }

  @Test
  void aWrongStatusIsAnErrorWithNoInvoice() {
    // FTA695's action cleared the invoice whenever GET returned an error.
    tenure("SU", List.of(CLIENT), List.of(new Object()));
    refusedWith(() -> service.preview("RAN000001", "2025"), InvoicePreviewService.BAD_STATUS);
  }

  @Test
  void aWrongStatusIsListedWithTheErrorThatStoppedGet() {
    tenure("SU", List.of(CLIENT), List.of());
    refusedWith(() -> service.preview("RAN000001", "2025"),
        InvoicePreviewService.BAD_STATUS + "\n" + InvoicePreviewService.NO_PROVISION);

    tenure("SU", List.of(), List.of());
    refusedWith(() -> service.preview("RAN000001", "2025"),
        InvoicePreviewService.BAD_STATUS + "\n" + InvoicePreviewService.NO_CLIENT);
  }

  @Test
  void noProvisionAloneIsOneError() {
    tenure("A", List.of(CLIENT), List.of());
    refusedWith(() -> service.preview("RAN000001", "2025"), InvoicePreviewService.NO_PROVISION);
  }
}
