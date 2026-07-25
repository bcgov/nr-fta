import { apiPost } from './http';

// Mirrors the backend SuspendBlocksRequest (ca.bc.gov.nrs.fta.tenure.dto).
// The cutting permit id is supplied via the URL path, not this body.
export interface SuspendBlocksRequest {
  forestFileId: string;
  cbSkeys: string[];
  suspendAllBlocks: boolean;
  partitionCode: string | null;
  suspOrderNumber: string | null;
  suspStartDate: string | null; // ISO date
  suspEndDate: string | null; // ISO date
  reason: string;
}

/**
 * POST /api/fta/cutting-permits/{cpId}/suspend-blocks — suspend cut blocks on a
 * cutting permit (FTA_ADMIN only). Returns the number of blocks suspended.
 */
export function suspendBlocks(
  cpId: string,
  req: SuspendBlocksRequest,
): Promise<{ suspended: number }> {
  return apiPost<{ suspended: number }>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}/suspend-blocks`,
    req,
  );
}
