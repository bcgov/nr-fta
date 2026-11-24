package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.tenure.dto.CreateTenureRequest;
import ca.bc.gov.nrs.fta.tenure.tab.details.TenureDetailsLookups;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.CallableStatementCreator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The tenure create: refused requests write nothing, a woodlot gets every row ADD_NEW_REC
 * writes, and a failing write propagates out of the one transactional method (so Spring rolls
 * all of it back).
 */
@DisplayName("Unit Test | TenureWriteService create")
class TenureWriteServiceTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final JdbcTemplate plain = mock(JdbcTemplate.class);
  private final TenureDetailsLookups lookups = mock(TenureDetailsLookups.class);
  private final TenureWriteService service = new TenureWriteService(jdbc, lookups);

  private static CreateTenureRequest woodlot(String adminOrg, String muId) {
    return new CreateTenureRequest(null, "A04", adminOrg, null, "F", muId, null,
        null, null, null, null, null, null, null, null, null);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void database() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    // The file type is current; a timber tenure type, not recreation, not a designate type.
    when(jdbc.queryForObject(contains("the.file_type_code"), anyMap(), eq(Long.class)))
        .thenReturn(1L);
    when(jdbc.queryForObject(contains("fta_valid_recreation_file_type"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn("N");
    when(jdbc.queryForObject(contains("fta_valid_timber_tenure_type"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn("Y");
    when(jdbc.queryForObject(contains("fta_valid_designate_file_type"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn("N");
    when(jdbc.queryForObject(contains("fta_valid_minor_tsl_file_type"),
        any(SqlParameterSource.class), eq(String.class))).thenReturn("N");
    // Org 1867 is a current district.
    when(jdbc.query(contains("FROM the.org_unit"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of((Object) new String[] {"D", "Y"}));
    when(lookups.mgmtUnitExists("F", "W1234")).thenReturn(true);
    when(lookups.options()).thenReturn(Map.<String, List<CodeOptionDto>>of());
    when(lookups.headquarters()).thenReturn(new String[] {"1", "HQ"});
    // FTA_GENERATE_FILE_ID gives W1234.
    when(plain.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenReturn(new String[] {"W1234", null});
    // The new id isn't in use, nor any timber mark starting with the next designate.
    when(jdbc.queryForObject(contains("FROM the.prov_forest_use"), anyMap(), eq(Long.class)))
        .thenReturn(0L);
    when(jdbc.queryForObject(contains("timber_mark LIKE"), anyMap(), eq(Long.class)))
        .thenReturn(0L);
    // No designate yet; the woodlot counter is at WAAA.
    when(jdbc.query(contains("SELECT mark_designate FROM the.timber_tenure"), anyMap(),
        any(RowMapper.class))).thenReturn(List.of());
    when(jdbc.query(contains("FROM the.mrk_dsg_num_ctl"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.<Object>of("WAAA"));
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
  }

  @Test
  void createIsOneTransaction() throws NoSuchMethodException {
    assertThat(TenureWriteService.class
        .getMethod("create", CreateTenureRequest.class, String.class)
        .getAnnotation(Transactional.class)).isNotNull();
  }

  @Test
  void aWoodlotGetsEveryRowAddNewRecWrites() {
    String id = service.create(woodlot("1867", "W1234"), "IDIR\\USER").forestFileId();

    assertThat(id).isEqualTo("W1234");
    verify(jdbc).update(contains("INSERT INTO the.prov_forest_use"),
        any(SqlParameterSource.class));
    verify(jdbc).update(contains("INSERT INTO the.timber_tenure ("),
        any(SqlParameterSource.class));
    verify(jdbc).update(contains("INSERT INTO the.tenure_term"), any(SqlParameterSource.class));
    verify(jdbc).update(contains("INSERT INTO the.harvest_sale"), any(SqlParameterSource.class));
    // The W designate: the counter moves on and the file gets it.
    verify(jdbc).update(contains("UPDATE the.mrk_dsg_num_ctl"),
        eq(Map.of("d", "WAAB", "key", "6")));
    verify(jdbc).update(contains("UPDATE the.timber_tenure"), any(SqlParameterSource.class));
    verify(jdbc, never()).update(contains("INSERT INTO the.harvesting_authority"),
        any(SqlParameterSource.class));
  }

  @Test
  void aRefusedRequestWritesNothing() {
    // A04 needs a district as Admin Org; 1867 is a region here.
    when(jdbc.query(contains("FROM the.org_unit"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of((Object) new String[] {"R", "Y"}));

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("For A04 files, Admin Org must be a District.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    verify(jdbc, never()).update(anyString(), anyMap());
    verify(plain, never()).execute(any(CallableStatementCreator.class),
        any(CallableStatementCallback.class));
  }

  @Test
  void anInvalidManagementUnitIsRefusedBeforeAnyIdIsTaken() {
    assertThatThrownBy(() -> service.create(woodlot("1867", "NOPE"), "IDIR\\USER"))
        .hasMessageContaining("Invalid Management Unit type/id combination.");
    verify(plain, never()).execute(any(CallableStatementCreator.class),
        any(CallableStatementCallback.class));
  }

  @Test
  void aFailingWritePropagatesSoTheTransactionRollsBack() {
    when(jdbc.update(contains("INSERT INTO the.harvest_sale"), any(SqlParameterSource.class)))
        .thenThrow(new DataIntegrityViolationException("ORA-02291"));

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Nothing after the failure ran.
    verify(jdbc, never()).update(contains("UPDATE the.mrk_dsg_num_ctl"), anyMap());
  }
}
