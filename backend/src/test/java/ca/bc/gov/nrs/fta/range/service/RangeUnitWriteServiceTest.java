package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeUnitPastureRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitSaveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeUnitWriteResultDto;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.service.CodeListService;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeUnitWriteService")
class RangeUnitWriteServiceTest {

  private static final String USER = "IDIR\\JSMITH";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RangeUnitPackage pkg = mock(RangeUnitPackage.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final CodeListService codeLists = mock(CodeListService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneId.of("America/Vancouver"));
  private final RangeUnitWriteService service =
      new RangeUnitWriteService(jdbc, pkg, auth, codeLists, clock);

  @BeforeEach
  void setUp() {
    when(codeLists.rangeUnitStatuses()).thenReturn(List.of(
        new CodeOptionDto("A", "A - Active"), new CodeOptionDto("P", "P - Pending")));
    when(codeLists.districtOrgUnits()).thenReturn(List.of(
        new CodeOptionDto("10", "DCC - Cariboo-Chilcotin"),
        new CodeOptionDto("20", "DMH - 100 Mile House")));
    when(jdbc.queryForList(contains("FROM the.org_unit WHERE org_unit_no"),
        any(MapSqlParameterSource.class), eq(String.class)))
        .thenAnswer(inv -> {
          Object no = ((MapSqlParameterSource) inv.getArgument(1)).getValue("districtNo");
          return List.of(Long.valueOf(10).equals(no) ? "DCC" : "DMH");
        });
    when(auth.mayEditRangeDistrict("DCC")).thenReturn(true);
    when(pkg.call(anyMap())).thenReturn(out(""));
  }

  // ── Add ───────────────────────────────────────────────────────────

  @Test
  @DisplayName("add: SAVE with no revision, status P, the district as the user's org unit")
  void add() {
    when(pkg.call(anyMap())).thenReturn(out("", "p_range_unit_id", "RU1235"));

    RangeUnitWriteResultDto result = service.add(
        new RangeUnitSaveRequest(null, " big creek ", "A", "", "10", "ab", null), USER);

    assertThat(result.rangeUnitId()).isEqualTo("RU1235");
    assertThat(result.warnings()).containsExactly(RangeUnitRules.STATUS_DATE_CHANGE);
    Map<String, String> in = sent();
    assertThat(in).containsEntry("p_action", "SAVE")
        .containsEntry("p_status", "P")
        .containsEntry("p_district", "10")
        .containsEntry("p_user_org_unit", "10")
        .containsEntry("p_range_unit_name", "big creek")
        .containsEntry("p_district_admin_zone", "AB")
        .containsEntry("p_as_of", "2026-11-26")
        .containsEntry("p_update_userid", USER);
    assertThat(in.get("p_revision_count")).isNull();
    assertThat(in.get("p_range_unit_id")).isNull();
  }

  @Test
  @DisplayName("add: a district the user may not edit is refused with districtNotInRegion")
  void addOtherDistrict() {
    assertThatThrownBy(() -> service.add(
        new RangeUnitSaveRequest(null, "N", null, null, "20", "Z", null), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(e.getReason()).isEqualTo(RangeUnitRules.DISTRICT_NOT_IN_REGION);
        });
    verify(pkg, never()).call(anyMap());
  }

  @Test
  @DisplayName("add: an expired district is refused")
  void addExpiredDistrict() {
    assertThatThrownBy(() -> service.add(
        new RangeUnitSaveRequest(null, "N", null, null, "99", "Z", null), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("District (99) is an expired code.");
        });
  }

  @Test
  @DisplayName("add: a package error rolls back with legacy's text")
  void addPackageError() {
    when(pkg.call(anyMap()))
        .thenReturn(out("fta.web.usr.database.record.RangeUnitIdExists"));
    assertThatThrownBy(() -> service.add(
        new RangeUnitSaveRequest(null, "N", null, null, "10", "Z", null), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason())
              .isEqualTo("Range Unit Id already exists. Please contact system support.");
        });
  }

  // ── Save ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("save: SAVE with the stored status, As of and revision as the previous values")
  void save() {
    stubUnit("10", "DCC", "P", "2026-01-01", 4);
    when(pkg.call(anyMap())).thenReturn(out("fta.web.warning.CbStatusDateChanged:~W;"));

    RangeUnitWriteResultDto result = service.save("RU1",
        new RangeUnitSaveRequest(null, "Big Creek", "A", "2026-01-01", "10", "z1", 4), USER);

    assertThat(result.warnings()).containsExactly(RangeUnitRules.CB_STATUS_DATE_CHANGED);
    assertThat(sent()).containsEntry("p_range_unit_id", "RU1")
        .containsEntry("p_status", "A")
        .containsEntry("p_prev_status", "P")
        .containsEntry("p_as_of", "2026-01-01")
        .containsEntry("p_prev_as_of", "2026-01-01")
        .containsEntry("p_revision_count", "4")
        .containsEntry("p_district_admin_zone", "Z1");
  }

  @Test
  @DisplayName("save: another user's change since the read is a 409")
  void saveModified() {
    stubUnit("10", "DCC", "A", null, 5);
    assertThatThrownBy(() -> service.save("RU1",
        new RangeUnitSaveRequest(null, "N", "A", null, "10", "Z", 4), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    verify(pkg, never()).call(anyMap());
  }

  @Test
  @DisplayName("save: the unit's own district must be editable, not only the new one")
  void saveFromOtherDistrict() {
    stubUnit("20", "DMH", "A", null, 1);
    assertThatThrownBy(() -> service.save("RU1",
        new RangeUnitSaveRequest(null, "N", "A", null, "10", "Z", 1), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("save: moving the unit to a district the user may not edit is refused")
  void saveToOtherDistrict() {
    stubUnit("10", "DCC", "A", null, 1);
    assertThatThrownBy(() -> service.save("RU1",
        new RangeUnitSaveRequest(null, "N", "A", null, "20", "Z", 1), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  @DisplayName("save: a duplicate pasture name (the '00' rename) is a 400")
  void saveDuplicate() {
    stubUnit("10", "DCC", "A", null, 1);
    when(pkg.call(anyMap()))
        .thenReturn(out("fta.web.usr.database.record.DuplicatePastureName;"));
    assertThatThrownBy(() -> service.save("RU1",
        new RangeUnitSaveRequest(null, "N", "A", null, "10", "Z", 1), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo(RangeUnitRules.DUPLICATE_PASTURE);
        });
  }

  @Test
  @DisplayName("save: no such unit is a 404")
  void saveMissing() {
    assertThatThrownBy(() -> service.save("RU404",
        new RangeUnitSaveRequest(null, "N", "A", null, "10", "Z", 1), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  // ── Delete ────────────────────────────────────────────────────────

  @Test
  @DisplayName("delete: REMOVE; a meadow rotation stopping it is a 409 with legacy's text")
  void delete() {
    stubUnit("10", "DCC", "A", null, 2);
    service.delete("RU1", 2);
    assertThat(sent()).containsEntry("p_action", "REMOVE").containsEntry("p_revision_count", "2");

    when(pkg.call(anyMap()))
        .thenReturn(out("fta.web.usr.database.record.DeleteNotAllowed_Meadow;"));
    assertThatThrownBy(() -> service.delete("RU1", 2))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).isEqualTo(RangeUnitRules.DELETE_MEADOW);
        });
  }

  @Test
  @DisplayName("delete: refused in a district the user may not edit")
  void deleteOtherDistrict() {
    stubUnit("20", "DMH", "A", null, 2);
    assertThatThrownBy(() -> service.delete("RU1", 2))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  // ── Pastures ──────────────────────────────────────────────────────

  @Test
  @DisplayName("add pasture: SAVE_ITEM with no pasture revision")
  void addPasture() {
    stubUnit("10", "DCC", "A", null, 3);
    service.addPasture("RU1", new RangeUnitPastureRequest(" 01 ", " north ", null, 3), USER);
    Map<String, String> in = sent();
    assertThat(in).containsEntry("p_action", "SAVE_ITEM")
        .containsEntry("p_pasture_id", "01")
        .containsEntry("p_pasture_name", "north")
        .containsEntry("p_revision_count", "3");
    assertThat(in.get("p_pasture_revision_count")).isNull();
  }

  @Test
  @DisplayName("add pasture: a non-integer id is refused before the package")
  void addPastureInvalid() {
    stubUnit("10", "DCC", "A", null, 3);
    assertThatThrownBy(() -> service.addPasture("RU1",
        new RangeUnitPastureRequest("AB", "North", null, 3), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getReason()).isEqualTo(RangeUnitRules.PASTURE_ID_INTEGER));
    verify(pkg, never()).call(anyMap());
  }

  @Test
  @DisplayName("update pasture: SAVE_ITEM with both revisions; a stale pasture is a 409")
  void updatePasture() {
    stubUnit("10", "DCC", "A", null, 3);
    stubPasture(7);
    service.updatePasture("RU1", "01", new RangeUnitPastureRequest(null, "South", 7, 3), USER);
    assertThat(sent()).containsEntry("p_pasture_id", "01")
        .containsEntry("p_pasture_revision_count", "7")
        .containsEntry("p_revision_count", "3");

    assertThatThrownBy(() -> service.updatePasture("RU1", "01",
        new RangeUnitPastureRequest(null, "South", 6, 3), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName("update pasture 00: it renames the unit, so the unit's revision is checked too")
  void updateFirstPasture() {
    stubUnit("10", "DCC", "A", null, 3);
    stubPasture(1);
    assertThatThrownBy(() -> service.updatePasture("RU1", "00",
        new RangeUnitPastureRequest(null, "South", 1, 2), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @DisplayName("delete pasture: 00 is refused; a livestock rotation is the package's 409")
  void deletePasture() {
    stubUnit("10", "DCC", "A", null, 3);
    stubPasture(1);
    assertThatThrownBy(() -> service.deletePasture("RU1", "00", 1))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).isEqualTo(RangeUnitRules.DELETE_FIRST_PASTURE);
        });

    when(pkg.call(anyMap())).thenReturn(out("fta.web.usr.database.record.DeleteNotAllowed_Livestock"
        + "fta.web.usr.database.record.modified:fta_630_mn_rg_un_pst,REMOVE,range_unit_pasture;"));
    assertThatThrownBy(() -> service.deletePasture("RU1", "01", 1))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(e.getReason()).isEqualTo(RangeUnitRules.DELETE_LIVESTOCK);
        });
  }

  @Test
  @DisplayName("pasture writes are refused in a district the user may not edit")
  void pastureOtherDistrict() {
    stubUnit("20", "DMH", "A", null, 3);
    assertThatThrownBy(() -> service.addPasture("RU1",
        new RangeUnitPastureRequest("01", "North", null, 3), USER))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    assertThatThrownBy(() -> service.deletePasture("RU1", "01", 1))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  // ── Helpers ───────────────────────────────────────────────────────

  private static Map<String, String> out(String message, String... pairs) {
    Map<String, String> m = new HashMap<>();
    m.put("p_error_message", message);
    for (int i = 0; i < pairs.length; i += 2) {
      m.put(pairs[i], pairs[i + 1]);
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> sent() {
    ArgumentCaptor<Map<String, String>> in = ArgumentCaptor.forClass(Map.class);
    verify(pkg, org.mockito.Mockito.atLeastOnce()).call(in.capture());
    return in.getValue();
  }

  @SuppressWarnings("unchecked")
  private void stubUnit(String district, String districtCode, String status, String asOf,
      int revision) {
    when(jdbc.query(contains("FROM the.range_unit ru"), any(MapSqlParameterSource.class),
        any(RowMapper.class))).thenAnswer(inv -> {
          ResultSet rs = mock(ResultSet.class);
          when(rs.getString("district")).thenReturn(district);
          when(rs.getString("district_code")).thenReturn(districtCode);
          when(rs.getString("status")).thenReturn(status);
          when(rs.getString("as_of")).thenReturn(asOf);
          when(rs.getObject("revision_count", Integer.class)).thenReturn(revision);
          return List.of(((RowMapper<Object>) inv.getArgument(2)).mapRow(rs, 0));
        });
  }

  private void stubPasture(int revision) {
    when(jdbc.queryForList(contains("FROM the.range_unit_pasture"),
        any(MapSqlParameterSource.class), eq(Integer.class))).thenReturn(List.of(revision));
  }
}
