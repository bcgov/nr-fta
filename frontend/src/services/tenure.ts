import { apiGet, toQuery } from './http';

// Mirrors the backend TenureSummaryDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_001_TENR_SRCH rec_tenure_results record.
export interface TenureSummary {
  orgUnitCode: string;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  forestFileId: string;
  fileTypeCode: string | null;
  fileClientTypeDesc: string | null;
  fileStatusCode: string | null;
  fileStatusDesc: string | null;
  issueDate: string | null; // ISO date
  expiryDate: string | null;
}

export interface TenureSearchParams {
  forestFileId?: string;
  fileTypeCode?: string;
  orgUnitCode?: string;
  clientName?: string;
  clientNumber?: string;
  fileStatus?: string;
}

/** GET /api/fta/tenures — common tenure search (FTA_001_TENR_SRCH). */
export function searchTenures(params: TenureSearchParams): Promise<TenureSummary[]> {
  return apiGet<TenureSummary[]>(`/api/fta/tenures${toQuery({ ...params })}`);
}
