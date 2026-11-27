package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeFileAccess")
class RangeFileAccessTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final RangeFileAccess access = new RangeFileAccess(jdbc, auth);

  private void districts(String admin, String district) {
    Map<String, Object> row = new HashMap<>();
    row.put("admin_org_unit_code", admin);
    row.put("district_org_unit_code", district);
    when(jdbc.queryForList(eq(RangeFileAccess.DISTRICTS_SQL), any(MapSqlParameterSource.class)))
        .thenReturn(List.of(row));
  }

  @Test
  void aRangeFileIsOneWhoseTypeIsARangeFileType() {
    assertThat(RangeFileAccess.IS_RANGE_FILE_SQL).contains("the.range_file_type_code");
    when(jdbc.queryForObject(eq(RangeFileAccess.IS_RANGE_FILE_SQL),
        any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(1, 0);
    assertThat(access.isRangeFile("RAN1")).isTrue();
    assertThat(access.isRangeFile("A1")).isFalse();
  }

  @Test
  void eitherDistrictGrantsEditAsLegacyDid() {
    when(auth.mayEditRangeDistrict("DMH")).thenReturn(true);
    assertThat(access.mayEdit("DCC", "DMH")).isTrue();
    assertThat(access.mayEdit("DMH", null)).isTrue();
    assertThat(access.mayEdit("DCC", null)).isFalse();
  }

  @Test
  void requireEditableRefusesAFileOfAnotherDistrict() {
    districts("DCC", "DCC");
    assertThatThrownBy(() -> access.requireEditable("RAN1"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    when(auth.mayEditRangeDistrict("DCC")).thenReturn(true);
    assertThatCode(() -> access.requireEditable("RAN1")).doesNotThrowAnyException();
    assertThat(access.mayEdit("RAN1")).isTrue();
  }

  @Test
  void requireEditableRefusesAFileThatIsNotRange() {
    when(jdbc.queryForList(eq(RangeFileAccess.DISTRICTS_SQL), any(MapSqlParameterSource.class)))
        .thenReturn(List.of());
    assertThatThrownBy(() -> access.requireEditable("A1"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    assertThat(access.mayEdit("A1")).isFalse();
  }
}
