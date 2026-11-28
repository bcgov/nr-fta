import { apiGet, apiGetForBlob, apiPost, toQuery } from './http';

/**
 * Range billing — /api/fta/admin/billing (FTA_ADMIN only). Ports FTA680 Tenure
 * Billing Instructions, FTA695 Invoice Preview, FTA690 Tenure Approval
 * Submission, and the FTA685 / FTA686 pre- and post-billing reports.
 */

/** FTA680's search header; mirrors the backend BillingInstructionsCriteria. */
export interface BillingInstructionsCriteria {
  orgUnitNo: string;
  calendarYear: string;
  forestFileId?: string;
  fileTypeCode?: string;
  clientName?: string;
}

/** One FTA680 row; mirrors the backend BillingInstructionRowDto. */
export interface BillingInstructionRow {
  forestFileId: string;
  fileTypeCode: string;
  clientName: string;
  /** The instruction shown; '' for none. */
  instructionCode: string;
  /** An invoice number, 'GBS', or ''. */
  invoiceNumber: string;
  certifiedDate: string | null;
  certifiedBy: string | null;
  /** The instruction cannot be changed. */
  locked: boolean;
}

export const getBillingInstructions = (c: BillingInstructionsCriteria) =>
  apiGet<BillingInstructionRow[]>(`/api/fta/admin/billing/instructions${toQuery({ ...c })}`);

/**
 * FTA680 Prepare: one instruction per row of the last search, in the same
 * order. Resolves with whether any new instruction was recorded.
 */
export const prepareBillingInstructions = (
  criteria: BillingInstructionsCriteria,
  instructions: { forestFileId: string; instructionCode: string }[],
) =>
  apiPost<{ created: boolean }>('/api/fta/admin/billing/instructions/prepare', {
    criteria,
    instructions,
  });

/** FTA695's invoice; mirrors the backend InvoicePreviewDto. */
export interface InvoicePreview {
  forestFileId: string;
  calendarYear: string;
  fileTypeDescription: string;
  regionCode: string | null;
  districtCode: string | null;
  clientName: string;
  address1: string | null;
  address2: string | null;
  address3: string | null;
  city: string | null;
  province: string | null;
  postalCode: string | null;
  lines: { quantity: string | null; description: string; amount: number | null }[];
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  total: number;
}

export const getInvoicePreview = (forestFileId: string, calendarYear: string) =>
  apiGet<InvoicePreview>(
    `/api/fta/admin/billing/invoice-preview${toQuery({ forestFileId, calendarYear })}`,
  );

/** An FTA690 request's state; mirrors the backend BillingRequestDto. */
export type BillingRequestStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETE' | 'FAILED';

/** One FTA690 billing request and its outcome; mirrors the backend BillingRequestDto. */
export interface BillingRequest {
  rangeBillRequestId: number;
  orgUnitNo: number | null;
  orgUnitCode: string | null;
  orgUnitName: string | null;
  calendarYear: number | null;
  submitUserid: string | null;
  submitDate: string | null;
  startDate: string | null;
  endDate: string | null;
  status: BillingRequestStatus;
  messageText: string | null;
  totalInvoices: number | null;
  firstInvoiceNumber: string | null;
  lastInvoiceNumber: string | null;
  totalInvoiceValue: number | null;
}

/** FTA690 Submit: records the request, bills it, and returns the outcome (a few seconds). */
export const submitBillingApproval = (orgUnitNo: string, calendarYear: string) =>
  apiPost<BillingRequest>('/api/fta/admin/billing/approval', { orgUnitNo, calendarYear });

/** The FTA411 Approval Summary of a processed request, as a PDF. */
export const getBillingRequestSummary = (id: number) =>
  apiGetForBlob(`/api/fta/admin/billing/approval/requests/${id}/summary`);

/** FTA685: the FTA408 Pre-Billing Report for a district and year, as a PDF. */
export const getPreBillingReport = (orgUnitNo: string, calendarYear: string) =>
  apiGetForBlob(`/api/fta/admin/billing/pre-billing-report${toQuery({ orgUnitNo, calendarYear })}`);

/** FTA686's Sort By: 1 client name, 2 tenure, 3 file type, 4 invoice number. */
export type PostBillingSort = '1' | '2' | '3' | '4';

/**
 * FTA686: the FTA409 Post-Billing Report as a PDF. `orgUnit` is a district's
 * number, or '*' for every district.
 */
export const getPostBillingReport = (
  orgUnit: string,
  calendarYear: string,
  sortBy: PostBillingSort,
) =>
  apiGetForBlob(
    `/api/fta/admin/billing/post-billing-report${toQuery({ orgUnit, calendarYear, sortBy })}`,
  );
