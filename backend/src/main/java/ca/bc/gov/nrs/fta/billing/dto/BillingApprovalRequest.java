package ca.bc.gov.nrs.fta.billing.dto;

/**
 * FTA690 Tenure Approval Submission: queue a range billing run for one district
 * and year ({@code FTA_690_TENURE_APPROVAL.SUBMIT}).
 *
 * @param orgUnitNo    the district (ORG_UNIT_NO)
 * @param calendarYear the billing year, four digits
 */
public record BillingApprovalRequest(Long orgUnitNo, String calendarYear) {}
