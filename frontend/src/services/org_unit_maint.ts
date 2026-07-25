import { apiPost } from './http';

// Mirrors the backend OrgUnitMaintRequest (ca.bc.gov.nrs.fta.shared.dto).
export interface OrgUnitMaintRequest {
  orgUnitCode: string;
}

/** POST /api/fta/admin/org-unit-default — set the user's default org unit (SIL99). */
export function setDefaultOrgUnit(req: OrgUnitMaintRequest): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>('/api/fta/admin/org-unit-default', req);
}
