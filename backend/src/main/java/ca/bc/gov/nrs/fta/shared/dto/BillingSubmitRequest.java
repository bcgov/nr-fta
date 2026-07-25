package ca.bc.gov.nrs.fta.shared.dto;

/**
 * Request body for submitting a Range Billing invoicing request ({@code POST
 * /api/fta/admin/billing}). Fields mirror the input parameters of the legacy
 * {@code Fta_690_Tenure_Approval.SUBMIT} procedure, which queues a request row
 * in {@code THE.FTA_RANGE_BILL_REQUEST} for the batch invoice engine.
 */
public record BillingSubmitRequest(String calendarYear, String orgUnitNo) {}
