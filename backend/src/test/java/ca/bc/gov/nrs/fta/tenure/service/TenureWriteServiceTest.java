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
import static org.mockito.Mockito.times;
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
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void database() {
    when(jdbc.getJdbcTemplate()).thenReturn(plain);
    // The file type is one Add New offers and Headquarters may add; a timber tenure type, not
    // recreation, not a designate type.
    when(jdbc.queryForObject(contains("the.file_type_addnew_code"), anyMap(), eq(Long.class)))
        .thenReturn(1L);
    when(jdbc.queryForObject(contains("the.fta_file_level_authority"), anyMap(),
        eq(Long.class))).thenReturn(1L);
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
    // FTA_GENERATE_FILE_ID gives W1234; then FTA_100_TENURE's ASSIGN_MARK_DESIGNATE succeeds.
    when(plain.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenReturn(new String[] {"W1234", null}).thenReturn(new String[] {null, null});
    // The new id isn't in use, nor any timber mark starting with the next designate.
    when(jdbc.queryForObject(contains("FROM the.prov_forest_use"), anyMap(), eq(Long.class)))
        .thenReturn(0L);
    when(jdbc.queryForObject(contains("timber_mark LIKE"), anyMap(), eq(Long.class)))
        .thenReturn(0L);
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
    // The W designate comes from the package (the file id, then the designate), not the
    // counter table, which the application may only read.
    verify(plain, times(2)).execute(any(CallableStatementCreator.class),
        any(CallableStatementCallback.class));
    verify(jdbc, never()).update(contains("mrk_dsg_num_ctl"), anyMap());
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
  void aTypeAddNewDoesNotOfferIsRefused() {
    when(jdbc.queryForObject(contains("the.file_type_addnew_code"), anyMap(), eq(Long.class)))
        .thenReturn(0L);

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .hasMessageContaining("is not a current file type that can be added.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aTypeHeadquartersMayNotAddIsRefused() {
    when(jdbc.queryForObject(contains("the.fta_file_level_authority"), anyMap(),
        eq(Long.class))).thenReturn(0L);

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .hasMessageContaining("You are not authorized to add A04 files.");
    verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
  }

  @Test
  void aRangeTypeIsRefused() {
    CreateTenureRequest range = new CreateTenureRequest(null, "E01", null, "1867", "F", "W1234",
        null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.create(range, "IDIR\\USER"))
        .hasMessageContaining("You are not authorized to add E01 files.");
  }

  @Test
  void aB07AsksQuotaBctsFundPaymentAndSpatialLater() {
    CreateTenureRequest b07 = new CreateTenureRequest(null, "B07", null, "1867", "F", "W1234",
        null, "OT", "Z1", null, null, null, null, null, null, null, null, null, null, null);

    assertThatThrownBy(() -> service.create(b07, "IDIR\\USER"))
        .hasMessageContaining("Quota type is mandatory if file type is B07.")
        .hasMessageContaining("BCTS Fund is mandatory.")
        .hasMessageContaining("Payment Method is mandatory.")
        .hasMessageContaining("Submit Spatial Later is mandatory.");
  }

  @Test
  void anA18AsksSalvage() {
    CreateTenureRequest a18 = new CreateTenureRequest(null, "A18", "1867", null, "F", "W1234",
        null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    when(jdbc.query(contains("FROM the.org_unit"), anyMap(), any(RowMapper.class)))
        .thenReturn(List.of((Object) new String[] {"R", "Y"}));

    assertThatThrownBy(() -> service.create(a18, "IDIR\\USER"))
        .hasMessageContaining("Salvage is mandatory.");
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
    // Nothing after the failure ran: only the file id was taken, no designate.
    verify(plain, times(1)).execute(any(CallableStatementCreator.class),
        any(CallableStatementCallback.class));
  }

  @Test
  void aDesignateThePackageRefusesFailsTheCreate() {
    when(plain.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenReturn(new String[] {"W1234", null}).thenReturn(new String[] {null,
            "fta.web.error.user.custom.msg:Assigned Mark designate of WABC may result in"
                + " duplicate timber marks.  Please reclick Assign Mark Designate button to get"
                + " another one"});

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Assigned Mark designate of WABC may result in duplicate timber"
            + " marks.")
        .hasMessageNotContaining("reclick");
  }

  @Test
  void anUnexpectedPackageErrorIsAServerError() {
    when(plain.execute(any(CallableStatementCreator.class), any(CallableStatementCallback.class)))
        .thenReturn(new String[] {"W1234", null}).thenReturn(new String[] {null,
            "fta.web.usr.database.proc.unexpected:FTA_100_TENURE.ASSIGN_MARK_DESIGNATE,ORA-01;"});

    assertThatThrownBy(() -> service.create(woodlot("1867", "W1234"), "IDIR\\USER"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void theDesignateCallBindsEveryMainlineParameter() {
    String block = Fta100TenureMainline.block("ASSIGN_MARK_DESIGNATE",
        List.of("p_file_type_code"), "p_mark_designate");
    assertThat(Fta100TenureMainline.PARAMS).hasSize(177).doesNotHaveDuplicates();
    Fta100TenureMainline.PARAMS.forEach(p -> assertThat(block).contains(p + " => v_" + p));
  }
}
