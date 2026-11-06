package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@DisplayName("Unit Test | AptForestFileIdService")
class AptForestFileIdServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptForestFileIdService service = new AptForestFileIdService(jdbc);

  @Test
  void theFoaTypeIsUpperCasedAndTheIdsComeBackOnceInOrder() {
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(List.of("B2", "A1", "B2"));

    assertThat(service.forestFileIds(3L, " fl ")).containsExactly("A1", "B2");

    ArgumentCaptor<SqlParameterSource> p = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).queryForList(anyString(), p.capture(), eq(String.class));
    assertThat(p.getValue().getValue("foaType")).isEqualTo("FL");
    assertThat(p.getValue().getValue("managementUnitId")).isEqualTo(3L);
  }
}
