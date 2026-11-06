package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** APT's client search (APT_CLIENTS_GET): one argument required, both upper-cased. */
@DisplayName("Unit Test | AptClientService")
class AptClientServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptClientService service = new AptClientService(jdbc);

  @Test
  void neitherArgumentIs400WithoutQuerying() {
    assertThatThrownBy(() -> service.search(" ", null))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
  }

  @Test
  void bindsTrimmedUpperCaseAndNullForAMissingArgument() {
    service.search(null, " west fraser ");
    verify(jdbc).query(eq(AptClientService.SEARCH_SQL),
        argThat((SqlParameterSource p) -> p.getValue("clientNumber") == null
            && "WEST FRASER".equals(p.getValue("clientName"))),
        any(RowMapper.class));
  }
}
