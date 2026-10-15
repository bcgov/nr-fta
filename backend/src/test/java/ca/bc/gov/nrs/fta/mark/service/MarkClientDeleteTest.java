package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.MarkDetailDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA513's Delete: only a previous licensee (C), only where the tab can be saved — legacy's
 * page rules, enforced by the service.
 */
@DisplayName("Unit Test | MarkClientWriteService delete")
class MarkClientDeleteTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final MarkDetailService detail = mock(MarkDetailService.class);
  private final MarkClientWriteService service =
      new MarkClientWriteService(jdbc, detail, mock(MarkFieldChecks.class));

  private static MarkDetailDto mark(String status, String fileType) {
    return new MarkDetailDto(
        "12A345", "150557", "12A345", fileType, status, null, null, null, null, null, 60,
        "1867", "DVA", "00001", "00", "Holder", "S", "H", null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0,
        null, null, null, 1L, null, true, List.of(), List.of(), List.of(), List.of(), null);
  }

  private void given(MarkDetailDto mark, String storedType) {
    when(detail.findByMarkNumber("12A345")).thenReturn(Optional.of(mark));
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class), eq(String.class)))
        .thenReturn(storedType == null ? List.of() : List.of(storedType));
  }

  @Test
  void aPreviousLicenseeIsDeleted() {
    given(mark("HI", "B01"), "C");
    when(jdbc.update(contains("DELETE FROM the.forest_file_client"),
        any(SqlParameterSource.class))).thenReturn(1);

    service.delete("12A345", false, 7L, 2L, false);

    verify(jdbc).update(contains("DELETE FROM the.forest_file_client"),
        any(SqlParameterSource.class));
  }

  @Test
  void anyOtherTypeIsRefused() {
    given(mark("HI", "B01"), "B");

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2L, false))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Only a previous licensee (C)");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void notAtAStatusWhereClientsCanChange() {
    given(mark("HX", "B01"), "C");

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2L, false))
        .hasMessageContaining("HI, PI or PA");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void notForADistrict() {
    given(mark("HI", "B01"), "C");

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2L, true))
        .isInstanceOf(ResponseStatusException.class);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void notOnAViewOnlyMarkType() {
    given(mark("HI", "B15"), "C");

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2L, false))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void someoneElseChangedIt() {
    given(mark("HI", "B01"), "C");
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

    assertThatThrownBy(() -> service.delete("12A345", false, 7L, 2L, false))
        .hasMessageContaining("changed by someone else");
  }
}
