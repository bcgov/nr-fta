package ca.bc.gov.nrs.fta.apt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryDto;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitHistoryRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitRequest;
import ca.bc.gov.nrs.fta.apt.dto.AptManagementUnitSearchCriteria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

/** The management unit service's SQL port of the APT_MANAGEMENT_UNIT* and MGMT_UNIT_HISTORY* routines. */
@DisplayName("Unit Test | AptManagementUnitService")
class AptManagementUnitServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 1, 15);
  private static final String USER = "IDIR\\JSMITH";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final AptManagementUnitService service = new AptManagementUnitService(jdbc);

  private static AptManagementUnitDto unit(long id) {
    return new AptManagementUnitDto(id, "Arrow TSA", 1L, "DAB - Arrow", "U", "01", "ACT", DAY,
        null, null, null, null, null, null, null, null);
  }

  private static AptManagementUnitHistoryDto history(long id) {
    return new AptManagementUnitHistoryDto(id, 7L, DAY, DAY, BigDecimal.TEN, null, null, null,
        null, null, null, null, null, null, null, USER, null, null, null);
  }

  private static AptManagementUnitRequest unitRequest(Long sourceId) {
    return new AptManagementUnitRequest(1L, " U ", "01", "ACT", DAY, sourceId, null, null);
  }

  private static AptManagementUnitHistoryRequest historyRequest(Long prevId) {
    return new AptManagementUnitHistoryRequest(prevId, DAY, DAY, BigDecimal.TEN, null, null,
        null, null, null, null, null, null, null, null);
  }

  @SuppressWarnings("unchecked")
  private void unitsFound(AptManagementUnitDto... units) {
    when(jdbc.query(eq(AptManagementUnitService.SEARCH_SQL), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of(units));
  }

  @SuppressWarnings("unchecked")
  private void historiesFound(AptManagementUnitHistoryDto... rows) {
    when(jdbc.query(eq(AptManagementUnitService.HISTORY_SQL), any(SqlParameterSource.class),
        any(RowMapper.class))).thenReturn(List.of(rows));
  }

  private void nextId(String sequence, long id) {
    when(jdbc.queryForObject(contains(sequence + ".NEXTVAL"), anyMap(), eq(Long.class)))
        .thenReturn(id);
  }

  private static void assertStatus(Runnable call, HttpStatus status, String reason) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
      assertThat(e.getStatusCode()).isEqualTo(status);
      if (reason != null) {
        assertThat(e.getReason()).isEqualTo(reason);
      }
    });
  }

  // ── search ──

  @Test
  void searchTrimsAndBlanksItsFilters() {
    unitsFound(unit(1));

    service.search(new AptManagementUnitSearchCriteria(null, " U ", "  ", null, null, null,
        " arrow ", null, null));

    verify(jdbc).query(eq(AptManagementUnitService.SEARCH_SQL),
        argThat((SqlParameterSource p) -> "U".equals(p.getValue("type"))
            && p.getValue("number") == null
            && "arrow".equals(p.getValue("orgUnitName"))),
        any(RowMapper.class));
  }

  @Test
  void theSearchKeepsTheRoutinesFiltersAndOrder() {
    assertThat(AptManagementUnitService.SEARCH_SQL)
        .contains("UPPER(org_unit_name) LIKE '%' || UPPER(:orgUnitName) || '%'")
        .contains("UPPER(mu.orcs_file_number) LIKE '%' || UPPER(:orcs) || '%' OR :orcs IS NULL")
        .endsWith("ORDER BY musv.apt_mgmt_unit_st, musv.mgmt_unit_name");
  }

  @Test
  void anUnknownUnitIs404() {
    unitsFound();
    assertStatus(() -> service.get(9L), HttpStatus.NOT_FOUND, "Management unit 9 not found.");
  }

  // ── create / update ──

  @Test
  void createInsertsWithTheMuSequenceAndReturnsTheNewUnit() {
    when(jdbc.queryForObject(eq(AptManagementUnitService.UNIT_EXISTS_SQL),
        any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
    nextId("the.mu_seq", 42L);
    unitsFound(unit(42));

    assertThat(service.create(unitRequest(null), USER).id()).isEqualTo(42L);

    verify(jdbc).update(eq(AptManagementUnitService.INSERT_UNIT_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(42).equals(p.getValue("id"))
            && "U".equals(p.getValue("type"))));
  }

  @Test
  void createOfATypeAndNumberAlreadyInAptIs409() {
    when(jdbc.queryForObject(eq(AptManagementUnitService.UNIT_EXISTS_SQL),
        any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);

    assertStatus(() -> service.create(unitRequest(null), USER), HttpStatus.CONFLICT,
        "Management Unit is already exist.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void anUpdateToAnotherUnitsTypeAndNumberIs409() {
    unitsFound(unit(5));
    when(jdbc.update(eq(AptManagementUnitService.UPDATE_UNIT_SQL), any(SqlParameterSource.class)))
        .thenThrow(AptManagementUnitSqlTest.duplicate());

    assertStatus(() -> service.update(5L, unitRequest(null), USER), HttpStatus.CONFLICT, null);
  }

  @Test
  void aUnitCannotBeItsOwnSource() {
    unitsFound(unit(5));
    assertStatus(() -> service.update(5L, unitRequest(5L), USER), HttpStatus.BAD_REQUEST,
        "A management unit cannot be its own source management unit.");
  }

  // ── history ──

  @Test
  void aDeterminationWithTheSameDatesIs409WithTheRoutinesMessage() {
    unitsFound(unit(7));
    nextId("the.muh_seq", 3L);
    when(jdbc.update(eq(AptManagementUnitService.INSERT_HISTORY_SQL),
        any(SqlParameterSource.class))).thenThrow(AptManagementUnitSqlTest.duplicate());

    assertStatus(() -> service.createHistory(7L, historyRequest(null), USER),
        HttpStatus.CONFLICT, AptManagementUnitService.SAME_DATES);
  }

  @Test
  void createHistoryRecordsTheUser() {
    unitsFound(unit(7));
    nextId("the.muh_seq", 3L);
    historiesFound(history(3));

    assertThat(service.createHistory(7L, historyRequest(null), USER).id()).isEqualTo(3L);

    verify(jdbc).update(eq(AptManagementUnitService.INSERT_HISTORY_SQL),
        argThat((SqlParameterSource p) -> USER.equals(p.getValue("user"))
            && Long.valueOf(7).equals(p.getValue("muId"))));
    verify(jdbc, never()).query(eq(AptManagementUnitService.COPY_PARTITIONS_SQL),
        any(SqlParameterSource.class), any(RowMapper.class));
  }

  @Test
  void copyingFromADeterminationOfAnotherUnitIs400() {
    unitsFound(unit(7));
    historiesFound();

    assertStatus(() -> service.createHistory(7L, historyRequest(99L), USER),
        HttpStatus.BAD_REQUEST,
        "The determination to copy from is not one of this management unit's.");
  }

  @Test
  void withAPreviousDeterminationItsChildrenAreCopied() {
    unitsFound(unit(7));
    nextId("the.muh_seq", 3L);
    historiesFound(history(3));

    service.createHistory(7L, historyRequest(2L), USER);

    verify(jdbc).query(eq(AptManagementUnitService.COPY_PARTITIONS_SQL),
        argThat((SqlParameterSource p) -> Long.valueOf(2).equals(p.getValue("prevId"))),
        any(RowMapper.class));
  }

  @Test
  void theCopyKeepsThePackagesColumnsAndJournalRule() {
    // No AAC (ha) and no user on the copies, as APT_PKG.INSERT_MUH_CHILD_RECORDS.
    assertThat(AptManagementUnitService.INSERT_PARTITION_COPY_SQL)
        .doesNotContain("allowable_annual_cut_ha").doesNotContain("add_userid");
    assertThat(AptManagementUnitService.INSERT_APPORTIONMENT_COPY_SQL)
        .doesNotContain("allowable_annual_cut_ha").doesNotContain("add_userid");
    assertThat(AptManagementUnitService.INSERT_DISTRIBUTION_JN_COPY_SQL)
        .contains("adj.jn_operation <> 'DEL'")
        .contains("'(Inserted via copy function) ' || adj.jn_notes")
        .contains("SELECT MAX(jn_datetime)");
  }

  @Test
  void aFailedCopyIsThePackages400() {
    when(jdbc.query(eq(AptManagementUnitService.COPY_PARTITIONS_SQL),
        any(SqlParameterSource.class), any(RowMapper.class)))
        .thenAnswer(inv -> List.of(copyRow(inv.getArgument(2), 11L, 1L)));
    when(jdbc.update(eq(AptManagementUnitService.INSERT_PARTITION_COPY_SQL),
        any(SqlParameterSource.class))).thenThrow(AptManagementUnitSqlTest.triggerError());

    assertStatus(() -> service.copyChildRecords(3L, 2L), HttpStatus.BAD_REQUEST,
        "Error: OTHERS in APT_PKG.INSERT_MUH_CHILD_RECORDS when inserting APT_PARTITION record.");
  }

  /** Runs the service's own row mapper over a fake copy row. */
  private static Object copyRow(RowMapper<?> mapper, long newId, long oldId) throws Exception {
    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
    when(rs.getBigDecimal("NEW_ID")).thenReturn(BigDecimal.valueOf(newId));
    when(rs.getBigDecimal("OLD_ID")).thenReturn(BigDecimal.valueOf(oldId));
    when(rs.getString("CODE")).thenReturn("A");
    return mapper.mapRow(rs, 0);
  }

  @Test
  void updatingAnUnknownDeterminationIs404() {
    historiesFound();
    assertStatus(() -> service.updateHistory(7L, 3L, historyRequest(null), USER),
        HttpStatus.NOT_FOUND, "Management unit history 3 not found.");
  }
}
