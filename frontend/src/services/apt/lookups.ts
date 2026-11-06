import { apiGet, toQuery } from '../http';

/**
 * The code lists the APT screens share — GET /api/fta/apt/lookups/*, mirroring the backend's
 * AptLookupController. Each is one of APT2's lookup functions.
 */

/** Mirrors the backend CodeOptionDto. */
export interface AptCodeOption {
  code: string;
  description: string | null;
}

/** Mirrors the backend AptOrgUnitDto. */
export interface AptOrgUnit {
  orgUnitNo: number;
  code: string;
  name: string | null;
  /** `CODE - NAME`, as APT2 showed it. */
  displayName: string | null;
}

const BASE = '/api/fta/apt/lookups';

/** Current regions (`R`), districts (`D`), or both (no level). */
export function getAptOrgUnits(level?: 'R' | 'D'): Promise<AptOrgUnit[]> {
  return apiGet<AptOrgUnit[]>(`${BASE}/org-units${toQuery({ level })}`);
}

export function getManagementUnitTypes(): Promise<AptCodeOption[]> {
  return apiGet<AptCodeOption[]>(`${BASE}/management-unit-types`);
}

export function getManagementUnitStatuses(): Promise<AptCodeOption[]> {
  return apiGet<AptCodeOption[]>(`${BASE}/management-unit-statuses`);
}

/** FOA licence types valid for a management unit type. */
export function getFoaTypes(managementUnitType?: string): Promise<AptCodeOption[]> {
  return apiGet<AptCodeOption[]>(`${BASE}/foa-types${toQuery({ managementUnitType })}`);
}

export function getCommitmentTypes(): Promise<AptCodeOption[]> {
  return apiGet<AptCodeOption[]>(`${BASE}/commitment-types`);
}

/** All partition types, or with both MU arguments, those that MU's partitions use. */
export function getPartitionTypes(
  managementUnitType?: string,
  managementUnitNumber?: string,
): Promise<AptCodeOption[]> {
  return apiGet<AptCodeOption[]>(
    `${BASE}/partition-types${toQuery({ managementUnitType, managementUnitNumber })}`,
  );
}
