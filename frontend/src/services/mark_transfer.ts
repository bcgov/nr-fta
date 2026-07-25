import { apiPost } from './http';

// Mirrors the backend MarkTransferRequest (ca.bc.gov.nrs.fta.mark.dto).
export interface MarkTransferRequest {
  sourceForestFileId: string;
  sourceCuttingPermitId: string;
  timberMark: string;
  targetForestFileId: string;
  targetCuttingPermitId: string;
  transferEffDate: string; // YYYY-MM-DD
  userOrgNo: string;
}

/** POST /api/fta/marks/transfer — transfer a timber mark to a target file (FTA_ADMIN only). */
export function transferMark(req: MarkTransferRequest): Promise<{ timberMark: string }> {
  return apiPost<{ timberMark: string }>('/api/fta/marks/transfer', req);
}
