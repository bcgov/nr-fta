package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptLicenceDto;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceSearchParams;
import ca.bc.gov.nrs.fta.apt.dto.AptLicenceUpdateRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * The licence search's paging checks, made before the database is called (APT2's 400s), and
 * the update as {@code APT_LICENCE_INS_UPD} made it.
 */
@DisplayName("Unit Test | AptLicenceService")
class AptLicenceServiceTest {

  private static final AptLicenceSearchParams BY_FILE =
      new AptLicenceSearchParams("A12345", null, null, null, null, null);

  // Never reached: every case is refused before the call.
  private final AptLicenceService service = new AptLicenceService(null);

  @Test
  void aNegativePageIs400() {
    assertThatThrownBy(() -> service.search(BY_FILE, -1, 10))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Page index must be zero or greater.");
        });
  }

  @Test
  void aPageSizeBelowOneIs400() {
    assertThatThrownBy(() -> service.search(BY_FILE, 0, 0))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Page size must be at least 1.");
        });
  }

  // ── column sort ──

  private static AptLicenceDto licence(String fileId, String clientName, LocalDate statusDate) {
    return new AptLicenceDto(null, null, null, null, "HI", null, statusDate, fileId, null, null,
        null, null, null, clientName, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void aColumnSortOrdersTheLicencesNullsLastWithTheFileIdBreakingTies() {
    List<AptLicenceDto> all = List.of(
        licence("A3", "beta", null),
        licence("A1", null, LocalDate.of(2020, 1, 1)),
        licence("A2", "Alpha", LocalDate.of(2021, 1, 1)),
        licence("A0", "beta", LocalDate.of(2019, 1, 1)));

    assertThat(AptLicenceService.sorted(all, "clientName,asc"))
        .extracting(AptLicenceDto::forestFileId).containsExactly("A2", "A0", "A3", "A1");
    assertThat(AptLicenceService.sorted(all, "fileStatusDate,desc"))
        .extracting(AptLicenceDto::forestFileId).containsExactly("A2", "A1", "A0", "A3");
  }

  @Test
  void noOrAnUnknownSortKeepsTheQueryOrder() {
    List<AptLicenceDto> all = List.of(licence("B", null, null), licence("A", null, null));

    assertThat(AptLicenceService.sorted(all, null)).isSameAs(all);
    assertThat(AptLicenceService.sorted(all, "comments,asc")).isSameAs(all);
    assertThat(AptLicenceService.sorted(all, "clientName,up")).isSameAs(all);
  }

  // ── update ──

  private static final String USER = "IDIR\\JSMITH";
  private static final AptLicenceUpdateRequest CHANGE =
      new AptLicenceUpdateRequest(" fl ", LocalDate.of(2030, 1, 1), 60, "  ");

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptLicenceService writer = new AptLicenceService(jdbc);

  private void given(boolean aptRow, boolean tenure, boolean foa) {
    doReturn(List.of(licence())).when(jdbc)
        .query(contains("mgmt_unit_names"), any(SqlParameterSource.class), any(RowMapper.class));
    when(jdbc.queryForObject(contains("FROM the.apt_licences WHERE"),
        any(SqlParameterSource.class), eq(Integer.class))).thenReturn(aptRow ? 1 : 0);
    when(jdbc.queryForObject(contains("FROM the.tenure_term"), any(SqlParameterSource.class),
        eq(Integer.class))).thenReturn(tenure ? 1 : 0);
    when(jdbc.queryForObject(contains("'APT_FOA_ST'"), any(SqlParameterSource.class),
        eq(Integer.class))).thenReturn(foa ? 1 : 0);
  }

  private static AptLicenceDto licence() {
    return new AptLicenceDto(null, null, null, null, "HI", null, null, "A12345", null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void aLicenceWithoutAnAptRowIsInsertedWithItsAddingUser() {
    given(false, true, true);

    writer.update("a12345", CHANGE, USER);

    ArgumentCaptor<SqlParameterSource> p = ArgumentCaptor.forClass(SqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO the.apt_licences"), p.capture());
    assertThat(p.getValue().getValue("forestFileId")).isEqualTo("A12345");
    assertThat(p.getValue().getValue("foaType")).isEqualTo("FL");
    assertThat(p.getValue().getValue("replacementTerm")).isEqualTo(60L);
    assertThat(p.getValue().getValue("comments")).isNull();
    assertThat(p.getValue().getValue("user")).isEqualTo(USER);
    verify(jdbc, never()).update(contains("UPDATE the.apt_licences"),
        any(SqlParameterSource.class));
  }

  @Test
  void aLicenceWithAnAptRowIsUpdated() {
    given(true, true, true);

    writer.update("A12345", CHANGE, USER);

    verify(jdbc).update(contains("UPDATE the.apt_licences"), any(SqlParameterSource.class));
    verify(jdbc, never()).update(contains("INSERT INTO the.apt_licences"),
        any(SqlParameterSource.class));
  }

  @Test
  void aLicenceWithNoTenureTermIsTheProceduresOwn400() {
    given(false, false, true);

    assertThatThrownBy(() -> writer.update("A12345", CHANGE, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("The Licence does not exist.");
        });
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anFoaTypeOutsideTheSubsetIsTheProceduresOwn400() {
    given(true, true, false);

    assertThatThrownBy(() -> writer.update("A12345", CHANGE, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("The Licence Form of Agreement does not exist.");
        });
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUnknownLicenceIs404() {
    assertThatThrownBy(() -> writer.update("A12345", CHANGE, USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
