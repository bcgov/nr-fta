package ca.bc.gov.nrs.fta.range.service;

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

import ca.bc.gov.nrs.fta.range.dto.ManageZoneAddRequest;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.user.UserLookupClient;
import ca.bc.gov.nrs.fta.user.UserLookupClient.IdirUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | ManageZoneAddWriteService (FTA631R Save)")
class ManageZoneAddWriteServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final UserLookupClient lookup = mock(UserLookupClient.class);
  private final ManageZoneAddWriteService service =
      new ManageZoneAddWriteService(jdbc, auth, lookup);

  @BeforeEach
  void setUp() {
    when(jdbc.queryForList(contains("FROM the.org_unit"), any(SqlParameterSource.class),
        eq(String.class))).thenReturn(List.of("DCC"));
    when(auth.administersRangeDistrict("DCC")).thenReturn(true);
    when(lookup.isConfigured()).thenReturn(true);
    when(lookup.getIdirDetail("JSMITH")).thenReturn(
        Optional.of(new IdirUser("JSMITH", "g", "Jane", "Smith", "j@x")));
    when(jdbc.queryForObject(contains("SELECT COUNT(*)"), any(SqlParameterSource.class),
        eq(Integer.class))).thenReturn(0);
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
  }

  @Test
  void aNewZoneIsInsertedUpperCasedWithTheContactsIdirName() {
    assertThat(service.save(ManageZoneRulesTest.valid(), "IDIR\\ME")).isEqualTo(1);

    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO the.range_zone"), p.capture());
    assertThat(p.getValue().getValue("rangeZoneCode")).isEqualTo("AB1");
    assertThat(p.getValue().getValue("contactUserId")).isEqualTo("IDIR\\JSMITH");
    assertThat(p.getValue().getValue("contact")).isEqualTo("JANE SMITH");
    assertThat(p.getValue().getValue("adminForestDistrictNo")).isEqualTo(1234L);
  }

  @Test
  void anExistingZoneIsUpdated() {
    when(jdbc.queryForObject(contains("SELECT COUNT(*)"), any(SqlParameterSource.class),
        eq(Integer.class))).thenReturn(1);
    service.save(ManageZoneRulesTest.valid(), "IDIR\\ME");
    verify(jdbc).update(contains("UPDATE the.range_zone"), any(SqlParameterSource.class));
  }

  @Test
  void aDistrictTheUserDoesNotAdministerIsForbidden() {
    when(auth.administersRangeDistrict("DCC")).thenReturn(false);
    assertThatThrownBy(() -> service.save(ManageZoneRulesTest.valid(), "IDIR\\ME"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining(ManageZoneRules.NOT_IN_ADMIN_ORG)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUnknownDistrictIsRefused() {
    when(jdbc.queryForList(contains("FROM the.org_unit"), any(SqlParameterSource.class),
        eq(String.class))).thenReturn(List.of());
    assertThatThrownBy(() -> service.save(ManageZoneRulesTest.valid(), "IDIR\\ME"))
        .hasMessageContaining(ManageZoneRules.DISTRICT_INVALID);
  }

  @Test
  void aContactIdirDoesNotKnowIsRefused() {
    when(lookup.getIdirDetail("JSMITH")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.save(ManageZoneRulesTest.valid(), "IDIR\\ME"))
        .hasMessageContaining("Contact IDIR\\JSMITH was not found in IDIR.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aFailedLookupIsAServiceError() {
    when(lookup.getIdirDetail("JSMITH")).thenThrow(new IllegalStateException("down"));
    assertThatThrownBy(() -> service.save(ManageZoneRulesTest.valid(), "IDIR\\ME"))
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void withoutALookupTheIdIsTakenAsTyped() {
    when(lookup.isConfigured()).thenReturn(false);
    service.save(ManageZoneRulesTest.valid(), "IDIR\\ME");
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("INSERT INTO the.range_zone"), p.capture());
    assertThat(p.getValue().getValue("contact")).isNull();
    assertThat(p.getValue().getValue("contactUserId")).isEqualTo("IDIR\\JSMITH");
  }

  @Test
  void missingFieldsAreRefusedBeforeAnyLookup() {
    assertThatThrownBy(() -> service.save(
        new ManageZoneAddRequest("", null, "", null, "", "", ""), "IDIR\\ME"))
        .hasMessageContaining("District is mandatory.")
        .hasMessageContaining("Contact email is mandatory.");
    verify(lookup, never()).getIdirDetail(anyString());
  }
}
