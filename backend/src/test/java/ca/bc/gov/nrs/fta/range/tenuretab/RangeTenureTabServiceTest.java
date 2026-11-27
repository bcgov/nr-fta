package ca.bc.gov.nrs.fta.range.tenuretab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.billing.service.InvoicePreviewService;
import ca.bc.gov.nrs.fta.range.dto.ManageZoneDto;
import ca.bc.gov.nrs.fta.range.service.ManageZoneService;
import ca.bc.gov.nrs.fta.range.service.RangeFileAccess;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureLookups.StoredCondition;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Action;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Condition;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Create;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Result;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureRequests.Save;
import ca.bc.gov.nrs.fta.range.tenuretab.RangeTenureTabDto.ConditionCode;
import ca.bc.gov.nrs.fta.security.LoggedUserHelper;
import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import ca.bc.gov.nrs.fta.shared.service.CodeListService;
import ca.bc.gov.nrs.fta.tenure.tab.details.TenureDetailsLookups;
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
import org.springframework.web.server.ResponseStatusException;

/** FTA100R through a mocked FTA_100RANGE_TENURE.mainline, gated as legacy and the roles do. */
@DisplayName("Unit Test | RangeTenureTabService (FTA100R)")
class RangeTenureTabServiceTest {

  private final RangeTenurePackage pkg = mock(RangeTenurePackage.class);
  private final RangeTenureLookups lookups = mock(RangeTenureLookups.class);
  private final TenureDetailsLookups tenureLookups = mock(TenureDetailsLookups.class);
  private final RangeFileAccess access = mock(RangeFileAccess.class);
  private final LoggedUserHelper auth = mock(LoggedUserHelper.class);
  private final ManageZoneService zones = mock(ManageZoneService.class);
  private final CodeListService codeLists = mock(CodeListService.class);
  private final InvoicePreviewService invoice = mock(InvoicePreviewService.class);
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-26T18:00:00Z"), ZoneId.of("America/Vancouver"));
  private final RangeTenureTabService service = new RangeTenureTabService(pkg, lookups,
      tenureLookups, access, auth, zones, codeLists, invoice, clock);

  private final Map<String, String> get = new HashMap<>();
  private String saveReply = "";

  @BeforeEach
  void setUp() {
    get.put("p_forest_file_id", "RAN001");
    get.put("p_file_type_code", "E01");
    get.put("p_prev_file_type_code", "E01");
    get.put("p_file_status_st", "A");
    get.put("p_prev_file_status_st", "A");
    get.put("p_file_status_date", "2020-01-01");
    get.put("p_prev_file_status_date", "2020-01-01");
    get.put("p_admin_org_unit_no", "1");
    get.put("p_forest_district_no", "1");
    get.put("p_mgmt_unit_type", "Q");
    get.put("p_mgmt_unit_id", "01");
    get.put("p_district_admin_zone", "Z1");
    get.put("p_issue_year", "2015");
    get.put("p_prev_issue_year", "2015");
    get.put("p_expiry_year", "2034");
    get.put("p_prev_expiry_year", "2034");
    get.put("p_tenure_term_years", "20");
    get.put("p_prev_tenure_term_years", "20");
    get.put("p_replacement_term_years", "");
    get.put("p_prev_replacement_term_years", "");
    get.put("p_show_tenure_term_ind", "Y");
    get.put("p_range_ind", "Y");
    get.put("p_disable_save_ind", "N");
    get.put("p_disable_replace_licence_ind", "N");
    get.put("p_disable_retire_ind", "Y");
    get.put("p_pfu_revision_count", "3");
    get.put("p_tt_revision_count", "2");
    get.put("p_rt_revision_count", "1");
    get.put("p_error_message", "");

    when(tenureLookups.headquarters()).thenReturn(new String[] {"1", "HQ"});
    when(tenureLookups.mgmtUnitExists(anyString(), any())).thenReturn(true);
    when(tenureLookups.expiryMatchesTerm(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);
    when(lookups.statuses()).thenReturn(List.of(new CodeOptionDto("A", "A - Active"),
        new CodeOptionDto("CA", "CA - Cancelled"), new CodeOptionDto("PA", "PA - Pending")));
    when(lookups.allStatuses()).thenReturn(List.of(new CodeOptionDto("A", "A - Active")));
    when(lookups.fileTypes()).thenReturn(List.of(new CodeOptionDto("E01", "E01 - Grazing"),
        new CodeOptionDto("E02", "E02 - Grazing Permit"), new CodeOptionDto("H01", "H01 - Hay")));
    when(lookups.allFileTypes()).thenReturn(List.of(new CodeOptionDto("E01", "E01 - Grazing")));
    when(lookups.createTypes()).thenReturn(List.of(new CodeOptionDto("E01", "E01 - Grazing")));
    when(lookups.conditionCodes()).thenReturn(List.of(
        new ConditionCode("GRZ", "Grazing", "Standard grazing text"),
        new ConditionCode("OTH", "Other", "")));
    when(lookups.orgUnitCode("1")).thenReturn("DCC");
    when(lookups.orgUnitCode("2")).thenReturn("DKA");
    when(codeLists.districts()).thenReturn(List.of(new CodeOptionDto("1", "DCC - Cariboo"),
        new CodeOptionDto("2", "DKA - Kamloops")));
    when(zones.search("1", null)).thenReturn(List.of(
        new ManageZoneDto("Z1", "North zone", 1L, "Jane Smith", null, null, null, 0L, "DCC", "")));
    when(access.mayEdit("RAN001")).thenReturn(true);
    when(auth.mayEditRangeDistrict("DCC")).thenReturn(true);
    when(pkg.call(anyMap())).thenAnswer(inv -> {
      Map<String, String> in = inv.getArgument(0);
      if ("GET".equals(in.get("p_action"))) {
        return new HashMap<>(get);
      }
      Map<String, String> out = new HashMap<>(in);
      out.put("p_error_message", saveReply);
      return out;
    });
  }

  private static Map<String, String> revisions() {
    return Map.of("pfuRevisionCount", "3", "ttRevisionCount", "2", "rtRevisionCount", "1");
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> lastCall() {
    ArgumentCaptor<Map<String, String>> in = ArgumentCaptor.forClass(Map.class);
    verify(pkg, atLeastOnce()).call(in.capture());
    List<Map<String, String>> calls = in.getAllValues();
    return calls.get(calls.size() - 1);
  }

  @Test
  void getDescribesTheFileAndItsZone() {
    RangeTenureTabDto dto = service.get("RAN001");

    assertThat(dto.values()).containsEntry("fileStatusSt", "A").containsEntry("issueYear", "2015");
    assertThat(dto.descriptions()).containsEntry("fileStatusSt", "A - Active")
        .containsEntry("fileTypeCode", "E01 - Grazing")
        .containsEntry("zoneDescription", "North zone")
        .containsEntry("zoneContact", "Jane Smith");
    assertThat(dto.revisions()).containsEntry("pfuRevisionCount", "3");
    assertThat(dto.fileTypes()).extracting(CodeOptionDto::code).containsExactly("E01", "E02");
    assertThat(dto.editable()).isTrue();
    assertThat(dto.replaceable()).isTrue();
  }

  @Test
  void getForSomeoneWhoMayNotEditOpensNothing() {
    when(access.mayEdit("RAN001")).thenReturn(false);
    get.put("p_disable_retire_ind", "N");

    RangeTenureTabDto dto = service.get("RAN001");
    assertThat(dto.editable()).isFalse();
    assertThat(dto.editableFields()).isEmpty();
    assertThat(dto.retirable()).isFalse();
  }

  @Test
  void getOfATimberFileIs404() {
    get.put("p_range_ind", "N");
    assertThatThrownBy(() -> service.get("RAN001"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void saveSendsTheWholeFormWithTheEditsAndTheStatusDateDefaulted() {
    Result r = service.save("RAN001", new Save(revisions(), Map.of(
        "fileStatusSt", "CA", "fileName", "  Big Meadow ", "forestDistrictNo", "2")),
        "IDIR\\ME");

    assertThat(r.message()).isEqualTo("Save successful.");
    Map<String, String> in = lastCall();
    assertThat(in.get("p_action")).isEqualTo("SAVE");
    assertThat(in.get("p_userid")).isEqualTo("IDIR\\ME");
    assertThat(in.get("p_file_status_st")).isEqualTo("CA");
    assertThat(in.get("p_file_status_date")).isEqualTo("2026-11-26");
    assertThat(in.get("p_file_name")).isEqualTo("Big Meadow");
    // District is a Super User's (FTA_ADMIN): ignored for the range tech.
    assertThat(in.get("p_forest_district_no")).isEqualTo("1");
    assertThat(in.get("p_pfu_revision_count")).isEqualTo("3");
    assertThat(in.get("p_district_admin_authority_ind")).isEqualTo("N");
  }

  @Test
  void ftaAdminMayMoveTheFileOnlyToADistrictItAdministers() {
    when(auth.isAdmin()).thenReturn(true);
    when(auth.mayEditRangeDistrict("DKA")).thenReturn(false);

    assertThatThrownBy(() -> service.save("RAN001",
        new Save(revisions(), Map.of("forestDistrictNo", "2")), "IDIR\\ME"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void theRetireAllowedWarningComesBack() {
    saveReply = "fta.web.warning.retire.allowed:~W";
    Result r = service.save("RAN001", new Save(revisions(), Map.of("fileStatusSt", "CA")),
        "IDIR\\ME");
    assertThat(r.warnings()).contains("Tenure may now be retired.");
  }

  @Test
  void aStaleFormIsRefused() {
    assertThatThrownBy(() -> service.save("RAN001", new Save(Map.of("pfuRevisionCount", "2",
        "ttRevisionCount", "2", "rtRevisionCount", "1"), Map.of()), "IDIR\\ME"))
        .hasMessageContaining("changed by someone else");
  }

  @Test
  void aFormErrorNeverReachesThePackage() {
    assertThatThrownBy(() -> service.save("RAN001",
        new Save(revisions(), Map.of("districtAdminZone", "")), "IDIR\\ME"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Zone is mandatory.");
    assertThat(lastCall().get("p_action")).isEqualTo("GET");
  }

  @Test
  void aPackageErrorFailsTheSave() {
    saveReply = "fta.status.change.invalid:A,PA;";
    assertThatThrownBy(() -> service.save("RAN001",
        new Save(revisions(), Map.of("fileStatusSt", "PA")), "IDIR\\ME"))
        .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
          assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(e.getReason()).isEqualTo("Status change from A to PA is invalid.");
        });
  }

  @Test
  void writesNeedTheUsersDistrict() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no"))
        .when(access).requireEditable("RAN001");
    assertThatThrownBy(() -> service.save("RAN001", new Save(revisions(), Map.of()), "U"))
        .isInstanceOf(ResponseStatusException.class);
    verify(pkg, never()).call(anyMap());
  }

  @Test
  void retireRunsOnlyWhenSetRetiredIndAllowsIt() {
    assertThatThrownBy(() -> service.retire("RAN001", new Action(revisions()), "U"))
        .hasMessageContaining("cannot be retired");

    get.put("p_disable_retire_ind", "N");
    Result r = service.retire("RAN001", new Action(revisions()), "U");
    assertThat(r.message()).isEqualTo("Retirement successful.");
    assertThat(lastCall().get("p_action")).isEqualTo("RETIRE");
  }

  @Test
  void cancelReplacementNeedsAReplacement() {
    assertThatThrownBy(() -> service.cancelReplacement("RAN001", new Action(revisions()), "U"))
        .hasMessageContaining("no replacement");

    get.put("p_replacement_term_years", "20");
    Result r = service.cancelReplacement("RAN001", new Action(revisions()), "U");
    assertThat(r.message()).isEqualTo("Cancel Replacement successful.");
    assertThat(lastCall().get("p_action")).isEqualTo("CANCEL_REPLACEMENT");
  }

  @Test
  void aNewConditionTakesItsCodesTitle() {
    service.saveCondition("RAN001", null, new Condition("grz", "ignored", "Keep off", null), "U");

    Map<String, String> in = lastCall();
    assertThat(in.get("p_action")).isEqualTo("SAVE_COND");
    assertThat(in.get("p_range_special_condition_code")).isEqualTo("GRZ");
    assertThat(in.get("p_condition_title")).isEqualTo("Grazing");
    assertThat(in.get("p_rtspc_skey")).isEmpty();
  }

  @Test
  void anOtherConditionTakesTheTypedTitle() {
    service.saveCondition("RAN001", null, new Condition("OTH", "Water", "Fence it", null), "U");
    assertThat(lastCall().get("p_condition_title")).isEqualTo("Water");
  }

  @Test
  void anUpdateKeepsTheCodeAndSendsTheRevision() {
    when(lookups.condition("RAN001", 7L)).thenReturn(new StoredCondition("GRZ", "Grazing", 4));
    service.saveCondition("RAN001", 7L, new Condition("OTH", "x", "New text", 4), "U");

    Map<String, String> in = lastCall();
    assertThat(in.get("p_range_special_condition_code")).isEqualTo("GRZ");
    assertThat(in.get("p_condition_title")).isEqualTo("Grazing");
    assertThat(in.get("p_rtspc_skey")).isEqualTo("7");
    assertThat(in.get("p_rsc_revision_count")).isEqualTo("4");
  }

  @Test
  void aDuplicateConditionIsLegacysMessage() {
    saveReply = "fta.web.error.usr.duplicateNonOtherRangeCondition:GRZ;";
    assertThatThrownBy(() -> service.saveCondition("RAN001", null,
        new Condition("GRZ", null, "x", null), "U"))
        .hasMessageContaining("Range tenure already has a special condition of type GRZ.");
  }

  @Test
  void deleteRunsDeleteCond() {
    when(lookups.condition("RAN001", 7L)).thenReturn(new StoredCondition("GRZ", "Grazing", 4));
    Result r = service.deleteCondition("RAN001", 7L, 4, "U");
    assertThat(r.message()).isEqualTo("Delete successful.");
    Map<String, String> in = lastCall();
    assertThat(in.get("p_action")).isEqualTo("DELETE_COND");
    assertThat(in.get("p_rsc_revision_count")).isEqualTo("4");
  }

  @Test
  void createIsAddNewRecAsPa() {
    when(pkg.call(anyMap())).thenAnswer(inv -> {
      Map<String, String> out = new HashMap<>(inv.<Map<String, String>>getArgument(0));
      out.put("p_forest_file_id", "RAN999");
      out.put("p_error_message", "");
      return out;
    });

    var created = service.create(
        new Create("E01", "1", "", "q", "01", "z1", "New range"), "IDIR\\ME");

    assertThat(created.forestFileId()).isEqualTo("RAN999");
    Map<String, String> in = lastCall();
    assertThat(in.get("p_action")).isEqualTo("SAVE");
    assertThat(in.get("p_pfu_revision_count")).isNull();
    assertThat(in.get("p_file_status_st")).isEqualTo("PA");
    assertThat(in.get("p_file_status_date")).isEqualTo("2026-11-26");
    assertThat(in.get("p_district_admin_zone")).isEqualTo("Z1");
    assertThat(in.get("p_mgmt_unit_type")).isEqualTo("Q");
  }

  @Test
  void createRefusesATimberTypeAndAnotherDistrict() {
    assertThatThrownBy(() -> service.create(
        new Create("A01", "1", "", "Q", "01", "Z1", null), "U"))
        .hasMessageContaining("You are not authorized to add A01 files.");

    assertThatThrownBy(() -> service.create(
        new Create("E01", "2", "", "Q", "01", "Z1", null), "U"))
        .isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void createNeedsAZoneOfTheDistrict() {
    assertThatThrownBy(() -> service.create(
        new Create("E01", "1", "", "Q", "01", "", null), "U"))
        .hasMessageContaining("Zone is mandatory.");
    assertThatThrownBy(() -> service.create(
        new Create("E01", "1", "", "Q", "01", "ZZ", null), "U"))
        .hasMessageContaining("Invalid Zone.");
  }
}
