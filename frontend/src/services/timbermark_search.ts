import { apiGet, toQuery } from './http';

// Mirrors the backend TimbermarkSearchDto (ca.bc.gov.nrs.fta.mark.dto),
// which mirrors the legacy THE.FTA_002_MARK_SRCH rec_tenure_results record.
export interface TimbermarkSummary {
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  fileTypeCode: string | null;
  forestFileId: string | null;
  cuttingPermitId: string | null;
  timberMark: string | null;
  certificate: string | null;
  markStatusSt: string | null;
  markIssueDate: string | null; // ISO date
  markExpiryDate: string | null; // ISO date
  salvageInd: string | null;
  hvaSkey: number | null;
}

export interface TimbermarkSearchParams {
  forestFileId?: string;
  timberMark?: string;
  cuttingPermitId?: string;
  fileTypeCode?: string;
  markStatusSt?: string;
  certificate?: string;
  salvageInd?: string;
  clientNumber?: string;
  clientName?: string;
}

/** GET /api/fta/timber-marks — timber mark search (FTA_002_MARK_SRCH). */
export function searchTimbermarks(params: TimbermarkSearchParams): Promise<TimbermarkSummary[]> {
  return apiGet<TimbermarkSummary[]>(`/api/fta/timber-marks${toQuery({ ...params })}`);
}
