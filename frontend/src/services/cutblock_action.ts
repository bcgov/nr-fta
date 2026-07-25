import { apiPost } from './http';

// Mirrors the backend CutblockActionRequest (ca.bc.gov.nrs.fta.tenure.dto).
// blockId is passed in the path, not the body.
export interface CutblockActionRequest {
  action: 'amend' | 'suspend' | 'relabel';
  reason: string | null;
  newCutBlockId: string | null;
}

export interface CutblockActionResult {
  blockId: string;
  action: string;
  updated: number;
}

/**
 * POST /api/fta/cut-blocks/{blockId}/actions — amend / suspend / re-label a cut
 * block (FTA905 / FTA914 / FTA231, FTA_ADMIN only).
 */
export function performCutblockAction(
  blockId: string,
  req: CutblockActionRequest,
): Promise<CutblockActionResult> {
  return apiPost<CutblockActionResult>(
    `/api/fta/cut-blocks/${encodeURIComponent(blockId)}/actions`,
    req,
  );
}
