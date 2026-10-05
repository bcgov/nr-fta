import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

import type { CodeOption } from './codeLists';

/** Mirrors the backend AacRules — what may be changed on the AAC tab (FTA930). */
export interface AacRules {
  validFileType: boolean;
  edit: boolean;
  editReason: string | null;
  areas: boolean;
  areasReason: string | null;
  /** Whether area type A (Private/Schedule A) may be used: A02, A04, A44, A28, A29 only. */
  scheduleAAllowed: boolean;
}

/** One AAC history row — an allocation amount within its period (AacDtos.AacRow). */
export interface AacRow {
  amountId: number;
  periodId: number;
  effectiveDate: string; // ISO date
  unitOfMeasureCode: string;
  areaTypeCode: string;
  areaTypeDesc: string | null;
  cutTypeCode: string;
  cutTypeDesc: string | null;
  amount: number;
  reasonCode: string | null;
  reasonDesc: string | null;
  /** Y, N, or U (unknown). */
  revenueShareable: string | null;
  fra2003Volume: number | null;
  comment: string | null;
  entryUserid: string | null;
  updateUserid: string | null;
  periodRevisionCount: number;
  amountRevisionCount: number;
}

/** GET /api/fta/tenures/{id}/aac (AacDtos.AacResponse). */
export interface AacData {
  fileTypeCode: string | null;
  awardDate: string | null;
  expiryDate: string | null;
  scheduleAArea: number | null;
  scheduleBArea: number | null;
  /** Null when the tenure has no TIMBER_TENURE record. */
  areaRevisionCount: number | null;
  /** Most recent period first. */
  rows: AacRow[];
  rules: AacRules;
}

/** Add or change an AAC history row (AacDtos.AacSaveRequest). */
export interface AacSaveRequest {
  effectiveDate: string;
  unitOfMeasureCode: string;
  areaTypeCode: string;
  cutTypeCode: string;
  amount: number;
  reasonCode: string;
  comment: string | null;
  /** Y, N, or null for unknown. */
  revenueShareable: string | null;
  fra2003Volume: number | null;
  periodRevisionCount?: number;
  amountRevisionCount?: number;
}

const base = (forestFileId: string) => `/api/fta/tenures/${encodeURIComponent(forestFileId)}/aac`;

export const getTenureAac = (forestFileId: string) => apiGet<AacData>(base(forestFileId));

export const addAac = (forestFileId: string, body: AacSaveRequest) =>
  apiPost<void>(base(forestFileId), body);

export const updateAac = (forestFileId: string, amountId: number, body: AacSaveRequest) =>
  apiPut<void>(`${base(forestFileId)}/${amountId}`, body);

export const deleteAac = (forestFileId: string, row: AacRow) =>
  apiDelete<void>(
    `${base(forestFileId)}/${row.amountId}${toQuery({
      periodRevisionCount: row.periodRevisionCount,
      amountRevisionCount: row.amountRevisionCount,
    })}`,
  );

export const saveAacAreas = (
  forestFileId: string,
  body: { scheduleAArea: number | null; scheduleBArea: number | null; revisionCount: number },
) => apiPut<void>(`${base(forestFileId)}/areas`, body);

const lookups = new Map<string, Promise<CodeOption[]>>();

/** The dialog's code lists ("CODE - description", current codes), fetched once per page load. */
const lookup = (name: string): Promise<CodeOption[]> => {
  let hit = lookups.get(name);
  if (!hit) {
    hit = apiGet<CodeOption[]>(`/api/fta/tenure-lookups/${name}`);
    lookups.set(name, hit);
    hit.catch(() => lookups.delete(name));
  }
  return hit;
};

export const getAacAreaTypes = () => lookup('aac-area-types');
export const getAacCutTypes = () => lookup('aac-cut-types');
export const getAacAdjustmentReasons = () => lookup('aac-adjustment-reasons');
export const getAacHarvestUnits = () => lookup('aac-harvest-units');
