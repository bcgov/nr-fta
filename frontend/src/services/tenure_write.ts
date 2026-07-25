import { apiPost } from './http';

// Mirrors the backend CreateTenureRequest (ca.bc.gov.nrs.fta.tenure.dto).
export interface CreateTenureRequest {
  forestFileId: string;
  fileTypeCode: string;
  orgUnitCode: string;
  clientNumber: string;
  clientName: string;
  issueDate: string | null; // ISO date
}

/** POST /api/fta/tenures — create a new forest file (FTA_ADMIN only). */
export function createTenure(req: CreateTenureRequest): Promise<{ forestFileId: string }> {
  return apiPost<{ forestFileId: string }>('/api/fta/tenures', req);
}
