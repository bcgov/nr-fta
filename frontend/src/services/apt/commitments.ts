import { apiDelete, apiGet, apiPost, apiPut, toQuery } from '../http';

/**
 * APT commitments — /api/fta/apt/commitments and /api/fta/apt/forest-file-ids, mirroring the
 * backend's AptCommitmentController and AptForestFileIdController. Field names are APT2's.
 * Dates are ISO `YYYY-MM-DD`; timestamps ISO date-times.
 */

/** Mirrors the backend AptCommitmentDto. */
export interface AptCommitment {
  id: number;
  forestFileId: string;
  clientName: string | null;
  /** Partition type code. */
  partitionCode: string;
  partitionDescription: string | null;
  /** Commitment type code. */
  type: string;
  allowableAnnualCut: number | null;
  /** `Y` when it counts towards the licence's AAC. */
  includeInLicAac: 'Y' | 'N' | string;
  determinationDate: string | null;
  effectiveDate: string | null;
  expiryDate: string | null;
  /** The partition apportionment it belongs to, if any. */
  paptId: number | null;
  totalAac: number | null;
  totalIncludedAac: number | null;
}

/** Mirrors the backend AptCommitmentRequest — the body of a create or update. */
export interface AptCommitmentRequest {
  paptId: number | null;
  forestFileId: string;
  aptPartitionSt: string;
  commitTypeSt: string;
  aac: number;
  inclInLicAacInd: 'Y' | 'N';
  determinationDate: string;
  effectiveDate: string;
  expiryDate: string | null;
  /** Journal note; required to change an existing commitment. */
  reason: string | null;
}

/** Mirrors the backend AptCommitmentHistoryDto — one journal entry. */
export interface AptCommitmentHistory {
  rowId: string;
  /** INS, UPD or DEL. */
  jnOperation: string;
  jnOracleUser: string | null;
  jnDatetime: string | null;
  jnNotes: string | null;
  jnAppln: string | null;
  jnSession: number | null;
  id: number;
  forestFileId: string | null;
  aptPartitionSt: string | null;
  commitTypeSt: string | null;
  allowableAnnualCut: number | null;
  includeInLicAacInd: string | null;
  determinationDate: string | null;
  effectiveDate: string | null;
  expiryDate: string | null;
  paptId: number | null;
}

export interface AptCommitmentSearchParams {
  managementUnitType?: string;
  /** The management unit's number (APT2 called it `management_unit_id`). */
  managementUnitNumber?: string;
  foaType?: string;
  partitionType?: string;
}

/** The journal note's limit (APT_COMMITMENTS_JN.JN_NOTES). */
export const COMMITMENT_REASON_MAX = 240;

const BASE = '/api/fta/apt/commitments';

/** GET /api/fta/apt/commitments — APT_COMMITMENTS_GET_BY_MU. */
export function searchAptCommitments(params: AptCommitmentSearchParams): Promise<AptCommitment[]> {
  return apiGet<AptCommitment[]>(`${BASE}${toQuery({ ...params })}`);
}

/** GET /api/fta/apt/commitments/{id} — 404 when there is none. */
export function getAptCommitment(id: number | string): Promise<AptCommitment> {
  return apiGet<AptCommitment>(`${BASE}/${id}`);
}

/** GET /api/fta/apt/commitments/{id}/history — the journal, newest first. */
export function getAptCommitmentHistory(id: number | string): Promise<AptCommitmentHistory[]> {
  return apiGet<AptCommitmentHistory[]>(`${BASE}/${id}/history`);
}

/** POST /api/fta/apt/commitments — returns the commitment as stored. */
export function createAptCommitment(body: AptCommitmentRequest): Promise<AptCommitment> {
  return apiPost<AptCommitment>(BASE, body);
}

/** PUT /api/fta/apt/commitments/{id} — returns the commitment as stored. */
export function updateAptCommitment(
  id: number,
  body: AptCommitmentRequest,
): Promise<AptCommitment> {
  return apiPut<AptCommitment>(`${BASE}/${id}`, body);
}

/** DELETE /api/fta/apt/commitments/{id}; the reason, if any, goes to the journal. */
export function deleteAptCommitment(id: number, reason?: string): Promise<void> {
  return apiDelete<void>(`${BASE}/${id}`, { reason: reason?.trim() || null });
}

/**
 * GET /api/fta/apt/forest-file-ids — APT_FOREST_FILE_ID_GET: the licences a partition
 * apportionment's commitment can name.
 */
export function getAptForestFileIds(managementUnitId: number, foaType: string): Promise<string[]> {
  return apiGet<string[]>(`/api/fta/apt/forest-file-ids${toQuery({ managementUnitId, foaType })}`);
}
