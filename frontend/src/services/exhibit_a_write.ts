import { apiPost } from './http';

// Mirrors the backend UploadExhibitARequest (ca.bc.gov.nrs.fta.tenure.dto).
export interface UploadExhibitARequest {
  revisionCount: number | null;
  taiRevisionCount: number | null;
  imageBase64: string | null;
}

/**
 * POST /api/fta/exhibit-a/{esfId}/upload — upload the Exhibit A image for a
 * tenure application (FTA_ADMIN only). Ports FTA_307_UPLOAD_EXHIBIT_A.
 */
export function uploadExhibitA(
  esfId: string,
  req: UploadExhibitARequest,
): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>(
    `/api/fta/exhibit-a/${encodeURIComponent(esfId)}/upload`,
    req,
  );
}
