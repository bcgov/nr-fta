package ca.bc.gov.nrs.fta.tenure.tab.tenureapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppCpRequestDto;
import ca.bc.gov.nrs.fta.tenure.tab.tenureapp.TenureAppDtos.TenureAppTabDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | TenureAppService reads through legacy packages")
class TenureAppServiceLegacyReadsTest {

  private NamedParameterJdbcTemplate jdbc;
  private TenureAppCpRequestLegacy cpRequests;
  private TenureAppProfDecLegacy profDecs;
  private TenureAppService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    jdbc = mock(NamedParameterJdbcTemplate.class);
    cpRequests = mock(TenureAppCpRequestLegacy.class);
    profDecs = mock(TenureAppProfDecLegacy.class);
    service = new TenureAppService(jdbc, cpRequests, profDecs);
    Map<String, Object> file = new HashMap<>();
    file.put("file_type_code", "A01");
    when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(file);
    when(jdbc.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of());
  }

  @Test
  void theSmartFormListComesFromThePackage() {
    TenureAppCpRequestDto req = new TenureAppCpRequestDto("G1", 1L, "12", null, "NEW", "New",
        "ACC", "Accepted", null, null, null, false);
    when(cpRequests.requests("A1")).thenReturn(List.of(req));
    TenureAppTabDto tab = service.tab("A1");
    assertThat(tab.cpRequests()).containsExactly(req);
    assertThat(tab.unavailable()).isEmpty();
  }

  @Test
  void aPackageErrorCostsOnlyTheSmartFormList() {
    when(cpRequests.requests("A1")).thenThrow(new IllegalStateException("boom"));
    TenureAppTabDto tab = service.tab("A1");
    assertThat(tab.cpRequests()).isEmpty();
    assertThat(tab.unavailable()).containsExactly(TenureAppService.SMART_FORM_UNAVAILABLE);
  }

  @Test
  void aDatabaseErrorFromThePackageCallIsANoticeToo() {
    when(cpRequests.requests("A1")).thenThrow(
        new BadSqlGrammarException("call", "BEGIN", new java.sql.SQLException("ORA-06550")));
    assertThat(service.tab("A1").unavailable())
        .containsExactly(TenureAppService.SMART_FORM_UNAVAILABLE);
  }

  @Test
  void profDecsPassABlankCpAsNullAndTrimIt() {
    when(profDecs.declarations("A1", 5L, null, 3L)).thenReturn(List.of());
    assertThat(service.professionalDeclarations("A1", 5L, "  ", 3L)).isEmpty();
    service.professionalDeclarations("A1", 5L, " 12 ", null);
    verify(profDecs).declarations("A1", 5L, "12", null);
  }

  @Test
  void aProfDecPackageErrorIsA500WithItsMessage() {
    when(profDecs.declarations("A1", 5L, null, null))
        .thenThrow(new IllegalStateException("ORA-01722: invalid number"));
    assertThatThrownBy(() -> service.professionalDeclarations("A1", 5L, null, null))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
          assertThat(e.getReason()).contains("ORA-01722");
        });
  }
}
