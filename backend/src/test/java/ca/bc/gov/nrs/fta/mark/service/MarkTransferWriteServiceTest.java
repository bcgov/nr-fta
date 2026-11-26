package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.mark.dto.MarkTransferRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** FTA_230_MARKTRANFER.save: the mark it derives, its validation, and the updates it makes. */
@DisplayName("Unit Test | MarkTransferWriteService")
class MarkTransferWriteServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final MarkTransferWriteService service = new MarkTransferWriteService(jdbc);

  /** Rows the lookups return, keyed by "<query marker>|<id>". */
  private final Map<String, List<Map<String, Object>>> rows = new HashMap<>();
  private long mapFeatures;

  private static final MarkTransferRequest REQUEST =
      new MarkTransferRequest("a19201", "01", "A20115", "02", "2026-03-01");

  @BeforeEach
  void setUp() {
    // Source A19201/01 has primary mark 52/1234 in district 1867, under region 1800.
    forestFile("A19201", "A01", 1867L);
    forestFile("A20115", "A01", 1867L);
    primaryMark("A19201", "01", "52/1234", 1867L, 99L);
    orgUnit(1867L, "D", 1800L);
    orgUnit(1800L, "R", 1800L);
    orgUnit(1900L, "R", 1900L);
    orgUnit(1967L, "D", 1900L);

    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenAnswer(inv -> {
      String sql = inv.getArgument(0);
      MapSqlParameterSource p = inv.getArgument(1);
      String key;
      if (sql.contains("harvesting_hauling_xref")) {
        key = "hva|" + p.getValue("forestFileId") + "/" + p.getValue("cuttingPermitId");
      } else if (sql.contains("FROM the.prov_forest_use")) {
        key = "pfu|" + p.getValue("forestFileId");
      } else {
        key = "ou|" + p.getValue("orgUnitNo");
      }
      return rows.getOrDefault(key, List.of());
    });
    when(jdbc.queryForObject(contains("tenure_application_map_feature"),
        any(SqlParameterSource.class), eq(Long.class))).thenAnswer(inv -> mapFeatures);
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
  }

  private void forestFile(String id, String type, Long admin) {
    Map<String, Object> r = new HashMap<>();
    r.put("file_type_code", type);
    r.put("forest_region", admin == null ? null : BigDecimal.valueOf(admin));
    rows.put("pfu|" + id, List.of(r));
  }

  private void primaryMark(String file, String cp, String mark, long district, long skey) {
    rows.put("hva|" + file + "/" + cp, List.of(Map.of(
        "timber_mark", mark, "forest_district", BigDecimal.valueOf(district),
        "hva_skey", BigDecimal.valueOf(skey))));
  }

  private void orgUnit(long no, String level, long region) {
    rows.put("ou|" + no, List.of(Map.of(
        "org_level_code", level, "rollup_region_no", BigDecimal.valueOf(region))));
  }

  private void refusedWith(String message) {
    assertThatThrownBy(() -> service.transfer(REQUEST, "IDIR\\TESTER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining(message);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void movesTheSourcesPrimaryMark() {
    assertThat(service.transfer(REQUEST, "IDIR\\TESTER")).isEqualTo("52/1234");

    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(contains("UPDATE the.harvesting_authority"), p.capture());
    assertThat(p.getValue().getValue("hvaSkey")).isEqualTo(99L);
    assertThat(p.getValue().getValue("sourceForestFileId")).isEqualTo("A19201");
    assertThat(p.getValue().getValue("timberMark")).isEqualTo("52/1234");
    // The effective date is the date alone: midnight, as legacy stored it.
    assertThat(p.getValue().getValue("transferEffDate")).isEqualTo(LocalDate.of(2026, 3, 1));
    for (String table : List.of("hauling_authority", "associated_tenure",
        "waste_assessment_area", "cut_block", "cut_block_open_admin", "harvest_amend",
        "fdc_audit_log", "mstr_lic_to_cut")) {
      verify(jdbc).update(argThat(sql -> sql.contains("UPDATE the." + table + "\n")),
          any(SqlParameterSource.class));
    }
    verify(jdbc).update(contains("INSERT INTO the.mark_transfer"), any(SqlParameterSource.class));
  }

  @Test
  void everyFieldIsRequired() {
    assertThatThrownBy(() -> service.transfer(
        new MarkTransferRequest("A19201", " ", "A20115", null, ""), "U"))
        .hasMessageContaining("Source cutting permit is required.")
        .hasMessageContaining("Target cutting permit is required.")
        .hasMessageContaining("Legal transfer date is required.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUnknownSourceFile() {
    rows.remove("pfu|A19201");
    refusedWith("Invalid Source File.");
  }

  @Test
  void aSourceFileCpWithoutAPrimaryMark() {
    rows.remove("hva|A19201/01");
    refusedWith("Invalid Source File/CP.");
  }

  @Test
  void bothFilesMustBeATypeTenures() {
    forestFile("A19201", "B01", 1867L);
    forestFile("A20115", "B01", 1867L);
    assertThatThrownBy(() -> service.transfer(REQUEST, "U"))
        .hasMessageContaining("Source File must be an 'A' type tenure.")
        .hasMessageContaining("Target File must be an 'A' type tenure.");
  }

  @Test
  void spatialDataInMsrmStopsIt() {
    mapFeatures = 1;
    refusedWith("Cannot transfer mark. Spatial data exists in MSRM.");
  }

  @Test
  void anUnknownTargetFile() {
    rows.remove("pfu|A20115");
    refusedWith("Invalid Target File.");
  }

  @Test
  void theTargetFileCpMustNotExist() {
    primaryMark("A20115", "02", "52/9999", 1867L, 100L);
    refusedWith("Target File/CP already exists.");
  }

  @Test
  void sourceAndTargetMustDiffer() {
    assertThatThrownBy(() -> service.transfer(
        new MarkTransferRequest("A19201", "01", "A19201", "01", "2026-03-01"), "U"))
        .hasMessageContaining("Source and Target File/CP cannot be the same.");
  }

  @Test
  void aDistrictTargetTakesItsOwnDistrictsMarks() {
    forestFile("A20115", "A01", 1967L);
    refusedWith("Source and Target timber marks must be in the same district.");
  }

  @Test
  void aRegionTargetTakesMarksFromItsRegion() {
    forestFile("A20115", "A01", 1800L);
    assertThat(service.transfer(REQUEST, "U")).isEqualTo("52/1234");
  }

  @Test
  void aRegionTargetRefusesAnotherRegionsMarks() {
    forestFile("A20115", "A01", 1900L);
    refusedWith("Source and Target timber marks must be in the same region.");
  }

  @Test
  void aSourceChangedMeanwhileUndoesNothingAndFails() {
    when(jdbc.update(contains("UPDATE the.harvesting_authority"), any(SqlParameterSource.class)))
        .thenReturn(0);
    assertThatThrownBy(() -> service.transfer(REQUEST, "U"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("changed by someone else");
    verify(jdbc, never())
        .update(contains("INSERT INTO the.mark_transfer"), any(SqlParameterSource.class));
  }
}
