import { apiGet, apiPost, apiPut } from './http';

import type { CodeOption } from './codeLists';

// The FTA615R Associated Land Base editors (backend RangeLandBaseWriteController). Areas and
// dates go as text, as legacy's form took them; dates are yyyy-mm-dd.

/** The current ownership types and land purposes. */
export interface RangeLandBaseOptions {
  ownershipTypes: CodeOption[];
  purposes: CodeOption[];
}

/** A land base record as entered (legacy's edit row). */
export interface RangeLandBaseSave {
  description: string;
  rangeLandBasePid: string;
  rangeLandBaseId: string;
  rangeLandOwnershipTypeCode: string;
  rangeLandPurposeCode: string;
  fencedPasture: string;
  unfencedPasture: string;
  forageProduction: string;
  landBaseComment: string;
  leaseStartDate: string;
  leaseEndDate: string;
  /** The record's revision as read; null on an add. */
  revisionCount: number | null;
}

const base = (forestFileId: string) =>
  `/api/fta/range-tenures/${encodeURIComponent(forestFileId)}/land-base`;

/** GET .../land-base/options. */
export function getRangeLandBaseOptions(forestFileId: string): Promise<RangeLandBaseOptions> {
  return apiGet<RangeLandBaseOptions>(`${base(forestFileId)}/options`);
}

/** POST .../land-base — add a record. */
export function addRangeLandBase(
  forestFileId: string,
  body: RangeLandBaseSave,
): Promise<{ message: string }> {
  return apiPost(base(forestFileId), body);
}

/** PUT .../land-base/{rlbSkey} — update a record. */
export function updateRangeLandBase(
  forestFileId: string,
  rlbSkey: number,
  body: RangeLandBaseSave,
): Promise<{ message: string }> {
  return apiPut(`${base(forestFileId)}/${rlbSkey}`, body);
}

/** POST .../land-base/{rlbSkey}/remove — de-activate a record, with its comment. */
export function removeRangeLandBase(
  forestFileId: string,
  rlbSkey: number,
  landBaseComment: string,
  revisionCount: number | null,
): Promise<{ message: string }> {
  return apiPost(`${base(forestFileId)}/${rlbSkey}/remove`, { landBaseComment, revisionCount });
}
