package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.dto.BillingApprovalRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | BillingApprovalService validation")
class BillingApprovalServiceTest {

  @Test
  void districtAndAFourDigitYearAreRequired() {
    assertThat(BillingApprovalService.validate(new BillingApprovalRequest(null, "25")))
        .containsExactly(BillingApprovalService.DISTRICT_REQUIRED, BillingApprovalService.YEAR_INVALID);
    assertThat(BillingApprovalService.validate(new BillingApprovalRequest(1L, "2025"))).isEmpty();
  }
}
