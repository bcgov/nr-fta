import { apiGet, apiPost } from './http';

import type { CodeOption } from './codeLists';

/** Mirrors the backend OrgUnitMaintDto (ca.bc.gov.nrs.fta.shared.dto). */
export interface OrgUnitMaint {
  /** The signed-in user, whose default this is. */
  userId: string;
  /** The user's org units, one radio button each (code = ORG_UNIT_NO). */
  orgUnits: CodeOption[];
  /** The ORG_UNIT_NO checked, or null when none is. */
  defaultOrgUnitNo: string | null;
  /** Why the user has nothing to choose from, or null. */
  error: string | null;
}

/** Mirrors the backend UserOrgUnitDefaultDto. Every field is null when the user has no default. */
export interface UserOrgUnitDefault {
  orgUnitNo: string | null;
  orgUnitCode: string | null;
  /** D district, R region, H headquarters. */
  orgLevelCode: string | null;
  /** ORG_UNIT_NO of the region the default rolls up to. */
  rollupRegionNo: string | null;
}

/** GET /api/fta/admin/org-unit-default — Org Unit Maintenance (SIL99). */
export const getOrgUnitMaint = () => apiGet<OrgUnitMaint>('/api/fta/admin/org-unit-default');

let defaultRequest: Promise<UserOrgUnitDefault> | null = null;

/**
 * GET /api/fta/org-unit-default — the signed-in user's default org unit, which the search and
 * admin screens default their district to, as legacy's did. Fetched once and shared; a failed
 * request is dropped so the next caller retries.
 */
export function getUserOrgUnitDefault(): Promise<UserOrgUnitDefault> {
  if (!defaultRequest) {
    const request = apiGet<UserOrgUnitDefault>('/api/fta/org-unit-default');
    defaultRequest = request;
    request.catch(() => {
      if (defaultRequest === request) defaultRequest = null;
    });
  }
  return defaultRequest;
}

/** POST /api/fta/admin/org-unit-default — Save the user's default org unit. */
export async function saveDefaultOrgUnit(orgUnitNo: string): Promise<OrgUnitMaint> {
  const saved = await apiPost<OrgUnitMaint>('/api/fta/admin/org-unit-default', { orgUnitNo });
  // The other screens read the new default from here on, as legacy's SilUser took it at once.
  defaultRequest = null;
  return saved;
}
