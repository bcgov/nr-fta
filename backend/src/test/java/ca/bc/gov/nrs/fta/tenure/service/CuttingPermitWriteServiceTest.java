package ca.bc.gov.nrs.fta.tenure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.fta.tenure.dto.CuttingPermitWriteDtos.PermitSaveRequest;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("Unit Test | CuttingPermitWriteService checks")
class CuttingPermitWriteServiceTest {

  /** A save request with only the named fields set. */
  private static PermitSaveRequest request(Map<String, Object> fields) throws Exception {
    RecordComponent[] parts = PermitSaveRequest.class.getRecordComponents();
    Object[] args = Arrays.stream(parts).map(p -> fields.get(p.getName())).toArray();
    return (PermitSaveRequest) PermitSaveRequest.class
        .getDeclaredConstructor(Arrays.stream(parts).map(RecordComponent::getType)
            .toArray(Class[]::new))
        .newInstance(args);
  }

  @Test
  void aTermIsGivenUnlessBothPartsAreBlankOrZero() {
    assertThat(CuttingPermitWriteService.termGiven(null, null)).isFalse();
    assertThat(CuttingPermitWriteService.termGiven("0", "")).isFalse();
    assertThat(CuttingPermitWriteService.termGiven("0", "0")).isFalse();
    assertThat(CuttingPermitWriteService.termGiven("2", null)).isTrue();
    assertThat(CuttingPermitWriteService.termGiven("0", "6")).isTrue();
  }

  @Test
  void warnsWhenIssuedBeforeTheFileAward() {
    LocalDate award = LocalDate.of(2020, 5, 1);
    assertThat(CuttingPermitWriteService.issuePrecedesAward(LocalDate.of(2020, 4, 30), award))
        .isEqualTo(CuttingPermitWriteService.MSG_ISSUE_PRECEDES_AWARD);
    assertThat(CuttingPermitWriteService.issuePrecedesAward(award, award)).isNull();
    assertThat(CuttingPermitWriteService.issuePrecedesAward(null, award)).isNull();
    assertThat(CuttingPermitWriteService.issuePrecedesAward(award, null)).isNull();
  }

  @Test
  void areaHasSevenWholeDigits() throws Exception {
    CuttingPermitWriteService.checkFormats(request(Map.of("harvestArea", "9999999.9999")));
    assertThatThrownBy(() ->
        CuttingPermitWriteService.checkFormats(request(Map.of("harvestArea", "10000000"))))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void zoneAndManagementUnitFitTheirColumns() throws Exception {
    CuttingPermitWriteService.checkFormats(request(Map.of(
        "districtAdmnZone", "ABCD", "mgmtUnitTypeCode", "U", "mgmtUnitId", "1234")));
    assertThatThrownBy(() -> CuttingPermitWriteService.checkFormats(
        request(Map.of("districtAdmnZone", "ABCDE"))))
        .hasMessageContaining("Zone must not exceed 4");
    assertThatThrownBy(() -> CuttingPermitWriteService.checkFormats(
        request(Map.of("mgmtUnitTypeCode", "UU"))))
        .hasMessageContaining("Management Unit Type must not exceed 1");
    assertThatThrownBy(() -> CuttingPermitWriteService.checkFormats(
        request(Map.of("mgmtUnitId", "12345"))))
        .hasMessageContaining("Management Unit must not exceed 4");
  }
}
