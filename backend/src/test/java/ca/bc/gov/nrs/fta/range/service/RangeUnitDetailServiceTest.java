package ca.bc.gov.nrs.fta.range.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | RangeUnitDetailService")
class RangeUnitDetailServiceTest {

  @Test
  @DisplayName("the region reads code - name")
  void regionDescription() {
    assertThat(RangeUnitDetailService.regionDescription("RCB", "Cariboo Natural Resource Region"))
        .isEqualTo("RCB - Cariboo Natural Resource Region");
  }

  @Test
  @DisplayName("a missing region never reads \"null\"")
  void regionDescriptionMissing() {
    assertThat(RangeUnitDetailService.regionDescription(null, null)).isNull();
    assertThat(RangeUnitDetailService.regionDescription("RCB", null)).isEqualTo("RCB - ");
    assertThat(RangeUnitDetailService.regionDescription(null, "Cariboo")).isEqualTo(" - Cariboo");
  }
}
