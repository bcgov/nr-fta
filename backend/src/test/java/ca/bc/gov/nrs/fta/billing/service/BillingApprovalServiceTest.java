package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.dto.BillingApprovalRequest;
import ca.bc.gov.nrs.fta.billing.dto.BillingRequestDto;
import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | BillingApprovalService")
class BillingApprovalServiceTest {

  private static final Timestamp STARTED = Timestamp.valueOf("2026-11-27 13:00:00");

  @Test
  void districtAndAFourDigitYearAreRequired() {
    assertThat(BillingApprovalService.validate(new BillingApprovalRequest(null, "25")))
        .containsExactly(BillingApprovalService.DISTRICT_REQUIRED, BillingApprovalService.YEAR_INVALID);
    assertThat(BillingApprovalService.validate(new BillingApprovalRequest(1L, "2025"))).isEmpty();
  }

  @Test
  void anUnprocessedRequestIsQueuedUntilThePackageStartsIt() {
    assertThat(BillingApprovalService.status("N", null, null, null))
        .isEqualTo(BillingRequestDto.QUEUED);
    assertThat(BillingApprovalService.status(null, null, null, null))
        .isEqualTo(BillingRequestDto.QUEUED);
    assertThat(BillingApprovalService.status("N", STARTED, null, null))
        .isEqualTo(BillingRequestDto.PROCESSING);
  }

  @Test
  void aProcessedRequestIsCompleteOnlyWhenSuccessfulWithoutAMessage() {
    assertThat(BillingApprovalService.status("Y", STARTED, "Y", null))
        .isEqualTo(BillingRequestDto.COMPLETE);
    assertThat(BillingApprovalService.status("Y", STARTED, "Y", " "))
        .isEqualTo(BillingRequestDto.COMPLETE);
    // The package's aborts mark the row successful with a message and no invoices.
    assertThat(BillingApprovalService.status("Y", STARTED, "Y", "*** No instructions found ***"))
        .isEqualTo(BillingRequestDto.FAILED);
    assertThat(BillingApprovalService.status("Y", STARTED, "N", "ORA-00001"))
        .isEqualTo(BillingRequestDto.FAILED);
  }

  @Test
  void submitPicksUpTheDistrictAndYearsUnprocessedRequestOldestFirst() {
    assertThat(BillingApprovalService.UNPROCESSED_SQL)
        .contains("org_unit_no = :orgUnitNo")
        .contains("calendar_year = TO_NUMBER(:year)")
        .contains("NVL(processed_ind, 'N') = 'N'")
        .contains("ORDER BY range_bill_request_id");
  }
}
