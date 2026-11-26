import { apiPost } from './http';

// Mirrors the backend MarkTransferRequest (ca.bc.gov.nrs.fta.mark.dto). All required.
export interface MarkTransferRequest {
  sourceForestFileId: string;
  sourceCuttingPermitId: string;
  targetForestFileId: string;
  targetCuttingPermitId: string;
  transferEffDate: string; // YYYY-MM-DD
}

/**
 * POST /api/fta/marks/transfer — moves the source file/CP's primary timber mark to the
 * target file/CP (FTA_ADMIN only). Returns the mark moved.
 */
export function transferMark(req: MarkTransferRequest): Promise<{ timberMark: string }> {
  return apiPost<{ timberMark: string }>('/api/fta/marks/transfer', req);
}
