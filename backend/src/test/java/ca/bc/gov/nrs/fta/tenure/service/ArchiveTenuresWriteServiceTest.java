package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.tenure.dto.ArchiveTenuresRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | ArchiveTenuresWriteService (FTA640 Archive)")
class ArchiveTenuresWriteServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final ArchiveTenuresWriteService service = new ArchiveTenuresWriteService(jdbc, auth);

  private static final ArchiveTenuresRequest REGION_2020 = new ArchiveTenuresRequest(10L, null, 2020);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    // Neither code is expired unless a test says so.
    when(jdbc.query(contains("FROM the.org_unit"), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of((Object) new String[] {"RCB", "N"}));
    when(jdbc.update(contains("UPDATE the.prov_forest_use"), any(SqlParameterSource.class)))
        .thenReturn(5);
  }

  @Test
  void anUnscopedAdminArchivesAnywhere() {
    when(auth.isUnscopedRangeAdmin()).thenReturn(true);

    assertThat(service.archive(REGION_2020, "IDIR\\ME")).isEqualTo(5);
    verify(jdbc, never()).queryForList(anyString(), any(SqlParameterSource.class), eq(String.class));
  }

  @Test
  void aScopedAdminOfEveryAffectedDistrictArchives() {
    when(auth.isUnscopedRangeAdmin()).thenReturn(false);
    when(jdbc.queryForList(contains("SELECT DISTINCT ou.org_unit_code"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn(List.of("DCC"));
    when(auth.administersRangeDistrict("DCC")).thenReturn(true);

    assertThat(service.archive(REGION_2020, "IDIR\\ME")).isEqualTo(5);
  }

  @Test
  void aScopedAdminMissingOneDistrictIsRefused() {
    when(auth.isUnscopedRangeAdmin()).thenReturn(false);
    when(jdbc.queryForList(contains("SELECT DISTINCT ou.org_unit_code"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn(List.of("DCC", "DQU"));
    when(auth.administersRangeDistrict("DCC")).thenReturn(true);
    when(auth.administersRangeDistrict("DQU")).thenReturn(false);

    assertThatThrownBy(() -> service.archive(REGION_2020, "IDIR\\ME"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("District/Region is not within your Admin Org")
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void anExpiredRegionIsRefused() {
    when(auth.isUnscopedRangeAdmin()).thenReturn(true);
    when(jdbc.query(contains("FROM the.org_unit"), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of((Object) new String[] {"ROLD", "Y"}));

    assertThatThrownBy(() -> service.archive(REGION_2020, "IDIR\\ME"))
        .hasMessageContaining("Region (ROLD) is an expired code.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anOutOfRangeYearIsRefused() {
    assertThatThrownBy(() -> service.archive(new ArchiveTenuresRequest(10L, null, 999), "U"))
        .hasMessageContaining("Expiry Year field must be between 1000 and 9999.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }
}
