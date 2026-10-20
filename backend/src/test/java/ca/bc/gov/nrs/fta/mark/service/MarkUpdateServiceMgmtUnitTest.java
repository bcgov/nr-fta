package ca.bc.gov.nrs.fta.mark.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * When a save carries the management unit to the forest file: only when it changed. A unit
 * kept from the certificate is never validated, and a legacy one without a FOREST_MGMT_UNIT
 * row made PFU_FMU_FK fail a district's HX-to-HI save (certificate 70096).
 */
@DisplayName("Unit Test | MarkUpdateService management unit sync")
class MarkUpdateServiceMgmtUnitTest {

  @Test
  void anUnchangedUnitIsLeftAlone() {
    assertThat(MarkUpdateService.mgmtUnitChanged("T", "99", "T", "99")).isFalse();
    assertThat(MarkUpdateService.mgmtUnitChanged("Z", null, "Z", null)).isFalse();
  }

  @Test
  void aChangedTypeOrIdIsCarried() {
    assertThat(MarkUpdateService.mgmtUnitChanged("T", "01", "F", "01")).isTrue();
    assertThat(MarkUpdateService.mgmtUnitChanged("T", "01", "T", "02")).isTrue();
    assertThat(MarkUpdateService.mgmtUnitChanged("T", null, "T", "02")).isTrue();
  }
}
