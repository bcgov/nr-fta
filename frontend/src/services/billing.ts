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

/** FTA690 Submit: queue a range billing run for a district and year. */
export const submitBillingApproval = (orgUnitNo: string, calendarYear: string) =>
  apiPost<void>('/api/fta/admin/billing/approval', { orgUnitNo, calendarYear });

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
