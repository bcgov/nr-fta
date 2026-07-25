import { apiPost } from './http';

// Mirrors the backend BillingSubmitRequest (ca.bc.gov.nrs.fta.shared.dto).
export interface BillingSubmitRequest {
  calendarYear: string;
  orgUnitNo: string;
}

/**
 * POST /api/fta/admin/billing — queue a Range Billing invoicing request
 * (ports FTA690 SUBMIT; FTA_ADMIN only). Resolves with the rows written.
 */
export function submitBilling(req: BillingSubmitRequest): Promise<{ submitted: number }> {
  return apiPost<{ submitted: number }>('/api/fta/admin/billing', req);
}
