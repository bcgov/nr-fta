import { apiPost } from './http';

// Mirrors the backend MarkApplicationRequest (ca.bc.gov.nrs.fta.mark.dto).
export interface MarkApplicationRequest {
  markNumber: string;
  holderName: string;
  holderClient: string;
  orgUnit: string;
  timberOrigin: string;
}

/** POST /api/fta/marks — submit a new private mark application (FTA_ADMIN only). */
export function createMarkApplication(
  req: MarkApplicationRequest,
): Promise<{ markNumber: string }> {
  return apiPost<{ markNumber: string }>('/api/fta/marks', req);
}
