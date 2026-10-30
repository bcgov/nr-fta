import { apiGet, apiPost, toQuery } from './http';

// Mirrors the backend AssignMarksDto and AssignMarksRequest (ca.bc.gov.nrs.fta.tenure.dto).

export interface AssignableBlock {
  cbSkey: number;
  cutBlockId: string;
  timberMark: string | null;
  revisionCount: number;
}

export interface AssignMarksData {
  hvaSkey: number;
  forestFileId: string;
  authorizationId: string;
  statusCode: string | null;
  approvalDate: string | null;
  expiryDate: string | null;
  licensee: string | null;
  higherLevelPlanReference: string | null;
  blocks: AssignableBlock[];
  marks: string[];
}

export interface BlockAssignment {
  cbSkey: number;
  timberMark: string | null;
  newTimberMark: string | null;
  revisionCount: number;
}

export interface AssignMarksRequest {
  forestFileId: string;
  hvaSkey: number;
  assignToAllMark: string | null;
  assignments: BlockAssignment[];
}

const path = (cpId: string) => `/api/fta/cutting-permits/${encodeURIComponent(cpId)}/assign-marks`;

/** GET /api/fta/cutting-permits/{cpId}/assign-marks — what FTA908 shows. */
export function getAssignMarks(cpId: string, forestFileId: string): Promise<AssignMarksData> {
  return apiGet<AssignMarksData>(`${path(cpId)}${toQuery({ forestFileId })}`);
}

/**
 * POST /api/fta/cutting-permits/{cpId}/assign-marks — assign timber marks to the
 * authorization's cut blocks (FTA908), all or none.
 */
export function assignMarks(
  cpId: string,
  req: AssignMarksRequest,
): Promise<{ cpId: string; updated: number }> {
  return apiPost<{ cpId: string; updated: number }>(path(cpId), req);
}
