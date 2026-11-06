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
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptRelatedClientCreateRequest;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Related clients over APT_RELATED_CLIENTS: the checks APT_RELATED_CLIENTS_INS made, in its
 * order and with its messages and status codes, and the delete.
 */
@DisplayName("Unit Test | AptRelatedClientService")
class AptRelatedClientServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptRelatedClientService service = new AptRelatedClientService(jdbc);

  /** The same three keys exist already ({@code relationships}); which clients exist. */
  private void given(int relationships, boolean childExists, boolean parentExists) {
    when(jdbc.queryForObject(eq(AptRelatedClientService.EXISTS_SQL),
        any(SqlParameterSource.class), eq(Integer.class))).thenReturn(relationships);
    when(jdbc.queryForObject(eq(AptRelatedClientService.CLIENT_EXISTS_SQL),
        argThat((SqlParameterSource p) -> p != null
            && "00000002".equals(p.getValue("clientNumber"))), eq(Integer.class)))
        .thenReturn(childExists ? 1 : 0);
    when(jdbc.queryForObject(eq(AptRelatedClientService.CLIENT_EXISTS_SQL),
        argThat((SqlParameterSource p) -> p != null
            && "00000001".equals(p.getValue("clientNumber"))), eq(Integer.class)))
        .thenReturn(parentExists ? 1 : 0);
  }

  private static void assertStatus(Runnable call, HttpStatus status, String reason) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
      assertThat(e.getStatusCode()).isEqualTo(status);
      assertThat(e.getReason()).isEqualTo(reason);
    });
  }

  @Test
  void insertsTheThreeKeysWithABlankRelationshipAsCorp() {
    given(0, true, true);
    assertThat(service.create(new AptRelatedClientCreateRequest(" 00000002 ", "00000001", "")))
        .isEqualTo(new AptRelatedClientCreateRequest("00000002", "00000001", "CORP"));
    verify(jdbc).update(eq(AptRelatedClientService.INSERT_SQL),
        argThat((SqlParameterSource p) -> "00000002".equals(p.getValue("child"))
            && "00000001".equals(p.getValue("parent"))
            && "CORP".equals(p.getValue("rel"))));
  }

  @Test
  void missingClientNumberIs400WithoutTouchingTheDatabase() {
    assertThatThrownBy(() -> service.create(
        new AptRelatedClientCreateRequest("", "00000001", "CORP")))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void theSameRelationshipAgainIs409() {
    given(1, true, true);
    assertStatus(() -> service.create(
            new AptRelatedClientCreateRequest("00000002", "00000001", "CORP")),
        HttpStatus.CONFLICT, AptRelatedClientService.EXISTS_MSG);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUnknownLicenseeIs400BeforeTheParentIsChecked() {
    given(0, false, false);
    assertStatus(() -> service.create(
            new AptRelatedClientCreateRequest("00000002", "00000001", "CORP")),
        HttpStatus.BAD_REQUEST, "The client number is not valid");
  }

  @Test
  void anUnknownCorporationIs400() {
    given(0, true, false);
    assertStatus(() -> service.create(
            new AptRelatedClientCreateRequest("00000002", "00000001", "CORP")),
        HttpStatus.BAD_REQUEST, "The controlling client number is not valid");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aLicenseeThatAlreadyHasAParentIs409() {
    // ORA-00001 on REL_CLI_UK (CLIENT_NUMBER, APT_CLIENT_REL_CD).
    given(0, true, true);
    when(jdbc.update(eq(AptRelatedClientService.INSERT_SQL), any(SqlParameterSource.class)))
        .thenThrow(new DuplicateKeyException("ORA-00001"));
    assertStatus(() -> service.create(
            new AptRelatedClientCreateRequest("00000002", "00000001", "CORP")),
        HttpStatus.CONFLICT, "Record existed with the same Licensee and Relationship.");
  }

  @Test
  void anyOtherDatabaseErrorIs400WithOraclesMessage() {
    given(0, true, true);
    when(jdbc.update(eq(AptRelatedClientService.INSERT_SQL), any(SqlParameterSource.class)))
        .thenThrow(new DataIntegrityViolationException("insert failed",
            new SQLException("ORA-12899: value too large for column", "72000", 12899)));
    assertStatus(() -> service.create(
            new AptRelatedClientCreateRequest("00000002", "00000001", "CORP")),
        HttpStatus.BAD_REQUEST, "ORA-12899: value too large for column");
  }

  @Test
  void deletesByAllThreeKeys() {
    service.delete("00000002", "00000001", "CORP");
    verify(jdbc).update(eq(AptRelatedClientService.DELETE_SQL),
        argThat((SqlParameterSource p) -> "00000002".equals(p.getValue("child"))
            && "00000001".equals(p.getValue("parent"))
            && "CORP".equals(p.getValue("rel"))));
  }

  @Test
  void deleteNeedsEveryKey() {
    assertThatThrownBy(() -> service.delete("00000002", "00000001", " "))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }
}
