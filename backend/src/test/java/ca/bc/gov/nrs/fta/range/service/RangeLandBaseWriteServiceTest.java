package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.RemoveRequest;
import ca.bc.gov.nrs.fta.range.dto.RangeLandBaseDtos.SaveRequest;
import ca.bc.gov.nrs.fta.range.service.RangeLandBaseWriteService.Stored;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | RangeLandBaseWriteService (FTA615R Save and Remove)")
class RangeLandBaseWriteServiceTest {

  private static final String FILE = "RAN001";
  private static final String USER = "IDIR\\ME";

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RangeFileAccess access = mock(RangeFileAccess.class);
  private final RangeLandBaseWriteService service = new RangeLandBaseWriteService(jdbc, access);

  private void stored(Stored row) {
    doReturn(row == null ? List.of() : List.of(row))
        .when(jdbc).query(eq(RangeLandBaseWriteService.ROW_SQL), any(SqlParameterSource.class),
            any(RowMapper.class));
  }

  @BeforeEach
  void setUp() {
    doReturn(List.of(new CodeOptionDto("PL", "PL - Private")))
        .when(jdbc).query(eq(RangeLandBaseWriteService.OWNERSHIP_TYPES_SQL), anyMap(),
            any(RowMapper.class));
    doReturn(List.of(new CodeOptionDto("HAY", "HAY - Hay")))
        .when(jdbc).query(eq(RangeLandBaseWriteService.PURPOSES_SQL), anyMap(),
            any(RowMapper.class));
    stored(new Stored("PL", "HAY", null, 4));
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
  }

  private static SaveRequest request(Integer revision) {
    return new SaveRequest("Lot 1", "012345678", "lot 1", "PL", "HAY", "10.5", "", "", "c",
        "2025-01-01", "2025-12-31", revision);
  }

  @Test
  void addInsertsAnActiveRecordFromTheSequence() {
    assertThat(service.add(FILE, request(null), USER).message()).isEqualTo("Save successful.");
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(access).requireEditable(FILE);
    verify(jdbc).update(eq(RangeLandBaseWriteService.INSERT_SQL), p.capture());
    assertThat(p.getValue().getValue("landBaseId")).isEqualTo("LOT 1");
    assertThat(p.getValue().getValue("leaseStartDate")).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(p.getValue().getValue("unfencedPasture")).isNull();
    assertThat(p.getValue().getValue("userId")).isEqualTo(USER);
    assertThat(RangeLandBaseWriteService.INSERT_SQL).contains("range_land_base_seq.NEXTVAL");
  }

  @Test
  void theDistrictGateRunsFirst() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireEditable(FILE);
    assertThatThrownBy(() -> service.add(FILE, request(null), USER))
        .isInstanceOf(ResponseStatusException.class);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void theFormsChecksRefuseBeforeTheWrite() {
    SaveRequest bad = new SaveRequest(null, "123", null, null, null, null, null, null, null,
        null, null, null);
    assertThatThrownBy(() -> service.add(FILE, bad, USER))
        .hasMessageContaining(RangeLandBaseRules.DESCRIPTION_WITH_PID)
        .hasMessageContaining(RangeLandBaseRules.PID_LENGTH)
        .hasMessageContaining(RangeLandBaseRules.OWNERSHIP_REQUIRED);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void updateWritesUnderTheRevisionRead() {
    service.update(FILE, 7L, request(4), USER);
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).update(eq(RangeLandBaseWriteService.UPDATE_SQL), p.capture());
    assertThat(p.getValue().getValue("rlbSkey")).isEqualTo(7L);
    assertThat(p.getValue().getValue("revisionCount")).isEqualTo(4);
  }

  @Test
  void anUpdateNobodyElseMadeFirstIsAConflictOtherwise() {
    when(jdbc.update(eq(RangeLandBaseWriteService.UPDATE_SQL), any(SqlParameterSource.class)))
        .thenReturn(0);
    assertThatThrownBy(() -> service.update(FILE, 7L, request(3), USER))
        .hasMessageContaining(RangeLandBaseWriteService.MODIFIED)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void anInactiveOrUnknownRecordIsNotFound() {
    stored(null);
    assertThatThrownBy(() -> service.update(FILE, 7L, request(4), USER))
        .hasMessageContaining(RangeLandBaseWriteService.NOT_FOUND)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void removeNeedsAComment() {
    assertThatThrownBy(() -> service.remove(FILE, 7L, new RemoveRequest(" ", 4), USER))
        .hasMessageContaining(RangeLandBaseRules.REMOVE_COMMENT_REQUIRED);
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void removeStoresANewCommentThenDeactivatesUnderTheNextRevision() {
    assertThat(service.remove(FILE, 7L, new RemoveRequest("sold", 4), USER).message())
        .isEqualTo("Land Base record has been de-activated.");
    InOrder order = inOrder(jdbc);
    order.verify(jdbc).update(eq(RangeLandBaseWriteService.COMMENT_SQL),
        any(SqlParameterSource.class));
    ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    order.verify(jdbc).update(eq(RangeLandBaseWriteService.REMOVE_SQL), p.capture());
    assertThat(p.getValue().getValue("revisionCount")).isEqualTo(5);
    assertThat(RangeLandBaseWriteService.REMOVE_SQL).contains("range_land_base_act_ind = 'N'");
  }

  @Test
  void removeWithTheStoredCommentOnlyDeactivates() {
    stored(new Stored("PL", "HAY", "sold", 4));
    service.remove(FILE, 7L, new RemoveRequest("sold", 4), USER);
    verify(jdbc, never()).update(eq(RangeLandBaseWriteService.COMMENT_SQL),
        any(SqlParameterSource.class));
    verify(jdbc).update(eq(RangeLandBaseWriteService.REMOVE_SQL), any(SqlParameterSource.class));
  }

  @Test
  void removeOfAStaleRevisionIsAConflict() {
    assertThatThrownBy(() -> service.remove(FILE, 7L, new RemoveRequest("sold", 3), USER))
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }
}
