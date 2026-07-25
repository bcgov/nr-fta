import { apiPost } from './http';

// Mirrors the backend AssignMarksRequest (ca.bc.gov.nrs.fta.tenure.dto).

export interface BlockAssignment {
  cbSkey: string;
  cutBlockId: string;
  timberMark: string;
  newTimberMark: string | null;
  revisionCount: string;
}

export interface AssignMarksRequest {
  forestFileId: string;
  hvaSkey: string;
  assignments: BlockAssignment[];
}

/**
 * POST /api/fta/cutting-permits/{cpId}/assign-marks — assign hauling timber
 * marks to the cut blocks of a cutting permit (FTA908, FTA_ADMIN only).
 */
export function assignMarks(
  cpId: string,
  req: AssignMarksRequest,
): Promise<{ cpId: string; updated: number }> {
  return apiPost<{ cpId: string; updated: number }>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}/assign-marks`,
    req,
  );
}
