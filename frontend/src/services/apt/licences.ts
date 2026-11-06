import { apiGet, apiPut, toQuery } from '../http';

import type { PageableResponse } from '../paging';
import type { AptCommitment } from './commitments';

/**
 * APT licences — /api/fta/apt/licences, mirroring the backend's AptLicenceController. Field
 * names are APT2's, in camelCase. Dates are ISO `YYYY-MM-DD`; timestamps ISO date-times.
 */

/** Mirrors the backend AptLicenceDto. */
export interface AptLicence {
  orgUnitName: string | null;
  aptMgmtUnitSt: string | null;
  mgmtUnitNumber: string | null;
  mgmtUnitName: string | null;
  fileStatusSt: string | null;
  fileStatus: string | null;
  fileStatusDate: string | null;
  forestFileId: string;
  legalEffectiveDt: string | null;
  initialExpiryDt: string | null;
  currentExpiryDt: string | null;
  tenureTerm: number | null;
  clientNumber: string | null;
  clientName: string | null;
  controlCompany: string | null;
  /** APT's form of agreement type; null until set. */
  aptFoaSt: string | null;
  replacementDate: string | null;
  /** Months. */
  replacementTerm: number | null;
  comments: string | null;
  addTimestamp: string | null;
  addUserid: string | null;
  updateTimestamp: string | null;
  updateUserid: string | null;
}

export interface AptLicenceSearchParams {
  /** Required; `%` and `_` act as wildcards. */
  forestFileId: string;
  clientName?: string;
  orgUnitName?: string;
  managementUnitName?: string;
  managementUnitType?: string;
  managementUnitNumber?: string;
  /** A results-table column sort, `<column key>,asc|desc` (lib/columnSort). */
  sort?: string;
  /** 0-indexed. */
  page?: number;
  size?: number;
}

/** Mirrors the backend AptLicenceUpdateRequest. */
export interface AptLicenceUpdateRequest {
  foaType: string;
  replacementDate: string | null;
  replacementTerm: number | null;
  comments: string | null;
}

const BASE = '/api/fta/apt/licences';
const one = (forestFileId: string) => `${BASE}/${encodeURIComponent(forestFileId)}`;

/** GET /api/fta/apt/licences — APT_LICENCES_GET, one page. */
export function searchAptLicences(
  params: AptLicenceSearchParams,
): Promise<PageableResponse<AptLicence>> {
  return apiGet<PageableResponse<AptLicence>>(`${BASE}${toQuery({ ...params })}`);
}

/** GET /api/fta/apt/licences/{forestFileId} — 404 when there is none. */
export function getAptLicence(forestFileId: string): Promise<AptLicence> {
  return apiGet<AptLicence>(one(forestFileId));
}

/** PUT /api/fta/apt/licences/{forestFileId} — APT_LICENCE_INS_UPD; returns it as stored. */
export function updateAptLicence(
  forestFileId: string,
  body: AptLicenceUpdateRequest,
): Promise<AptLicence> {
  return apiPut<AptLicence>(one(forestFileId), body);
}

/** GET /api/fta/apt/licences/{forestFileId}/commitments — APT_COMMITMENTS_GET_BY_FFID. */
export function getAptLicenceCommitments(forestFileId: string): Promise<AptCommitment[]> {
  return apiGet<AptCommitment[]>(`${one(forestFileId)}/commitments`);
}
