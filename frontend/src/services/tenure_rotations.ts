import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

import type { CodeOption } from './codeLists';

// Mirrors the backend RotationsDtos (ca.bc.gov.nrs.fta.tenure.tab.rotations): the tenure
// detail's Grazing rotation (legacy FTA611), Hay cutting rotation (FTA612) and Copy rotation
// (FTA613) tabs. Numbers the user types go up as strings, so the server can answer with
// legacy's own messages.

/** A rotation tab's gate: whether it applies to the file, and whether it may be changed. */
export interface RotationsRules {
  applies: boolean;
  edit: boolean;
  /** Why not, when `applies` or `edit` is false. */
  reason: string | null;
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/rotations`;

// ---------------------------------------------------------------- grazing (FTA611)

export interface GrazingProvision {
  exists: boolean;
  nonUseForageTonnes: number | null;
  billableNonUseInd: string | null;
  totalAuthorizedGrazableForage: number | null;
  totalPrivateLandGrazableForage: number | null;
  /** Non-Use + TTL AUMs − PLD. */
  netAuthorized: number | null;
  maxCattle: number | null;
  maxHorses: number | null;
  maxSheep: number | null;
  maxOtherLivestock: number | null;
  revisionCount: number | null;
}

export interface GrazingRotation {
  livestockRotationSkey: number;
  calendarYear: number;
  livestockCode: string | null;
  /** "CODE - description". */
  livestockDesc: string | null;
  livestockCount: number | null;
  /** MM-DD. */
  beginRotationDate: string | null;
  /** MM-DD. */
  endRotationDate: string | null;
  rangeUnitId: string | null;
  pastureId: string | null;
  authorizedGrazableForage: number | null;
  privateLandGrazableForage: number | null;
  rotationLineNo: number | null;
  revisionCount: number;
}

export interface GrazingRotations {
  rules: RotationsRules;
  /** The term's years — the Year dropdown. */
  years: number[];
  calendarYear: number | null;
  provision: GrazingProvision | null;
  rotations: GrazingRotation[];
}

export interface GrazingProvisionRequest {
  nonUseForageTonnes: string;
  billableNonUseInd: string;
  maxCattle: string;
  maxHorses: string;
  maxSheep: string;
  maxOtherLivestock: string;
  revisionCount: number | null;
}

export interface GrazingRotationRequest {
  livestockCode: string;
  livestockCount: string;
  beginRotationDate: string;
  endRotationDate: string;
  rangeUnitId: string;
  pastureId: string;
  /** Blank: calculated from the head count and the days. */
  authorizedGrazableForage: string;
  privateLandGrazableForage: string;
  revisionCount: number | null;
}

/** GET …/rotations/grazing — the year asked for, or legacy's default year. */
export function getGrazingRotations(
  forestFileId: string,
  year?: number | null,
): Promise<GrazingRotations> {
  return apiGet<GrazingRotations>(`${base(forestFileId)}/grazing${toQuery({ year })}`);
}

/** PUT …/rotations/grazing/{year}/provision — Save Provision. */
export function saveGrazingProvision(
  forestFileId: string,
  year: number,
  body: GrazingProvisionRequest,
): Promise<void> {
  return apiPut<void>(`${base(forestFileId)}/grazing/${year}/provision`, body);
}

/** POST …/rotations/grazing/{year} — add a livestock rotation. */
export function addGrazingRotation(
  forestFileId: string,
  year: number,
  body: GrazingRotationRequest,
): Promise<void> {
  return apiPost<void>(`${base(forestFileId)}/grazing/${year}`, body);
}

/** PUT …/rotations/grazing/{year}/{skey} — change a livestock rotation. */
export function updateGrazingRotation(
  forestFileId: string,
  year: number,
  skey: number,
  body: GrazingRotationRequest,
): Promise<void> {
  return apiPut<void>(`${base(forestFileId)}/grazing/${year}/${skey}`, body);
}

/** DELETE …/rotations/grazing/{year}/{skey}?revisionCount= — delete a livestock rotation. */
export function deleteGrazingRotation(
  forestFileId: string,
  year: number,
  skey: number,
  revisionCount: number,
): Promise<void> {
  return apiDelete<void>(
    `${base(forestFileId)}/grazing/${year}/${skey}${toQuery({ revisionCount })}`,
  );
}

/** GET /api/fta/tenure-lookups/livestock-codes — the current livestock codes. */
export function getLivestockCodes(): Promise<CodeOption[]> {
  return apiGet<CodeOption[]>('/api/fta/tenure-lookups/livestock-codes');
}

// ---------------------------------------------------------------- hay cutting (FTA612)

export interface HayProvision {
  authorizedForageTonnes: number | null;
  nonUse: number | null;
  billableInd: string | null;
  /** The year's rotations' harvest, summed. */
  plusAuthHarvest: number | null;
  equalAuthorized: number | null;
  revisionCount: number | null;
}

export interface HayRotation {
  meadowRotationSkey: number;
  calendarYear: number;
  permitBlockId: string | null;
  rangeUnitId: string | null;
  meadowName: string | null;
  authorizedHarvestableForage: number | null;
  updateTimestamp: string | null; // ISO date
  updateUserid: string | null;
  revisionCount: number;
}

export interface HayRotations {
  rules: RotationsRules;
  years: number[];
  calendarYear: number | null;
  provision: HayProvision | null;
  rotations: HayRotation[];
}

export interface HayRowRequest {
  /** Null for a new row. */
  meadowRotationSkey: number | null;
  revisionCount: number | null;
  delete: boolean;
  permitBlockId: string;
  rangeUnitId: string;
  meadowName: string;
  authorizedHarvest: string;
}

export interface HaySaveRequest {
  nonUse: string;
  billableInd: string;
  provisionRevisionCount: number | null;
  rows: HayRowRequest[];
}

export interface HaySaveResult {
  saved: number;
  deleted: number;
  message: string;
}

/** GET …/rotations/hay-cutting — the year asked for, or legacy's default year. */
export function getHayRotations(forestFileId: string, year?: number | null): Promise<HayRotations> {
  return apiGet<HayRotations>(`${base(forestFileId)}/hay-cutting${toQuery({ year })}`);
}

/** PUT …/rotations/hay-cutting/{year} — the provision and the whole grid, all or nothing. */
export function saveHayRotations(
  forestFileId: string,
  year: number,
  body: HaySaveRequest,
): Promise<HaySaveResult> {
  return apiPut<HaySaveResult>(`${base(forestFileId)}/hay-cutting/${year}`, body);
}

// ---------------------------------------------------------------- copy (FTA613)

export interface CopyRotationTab {
  rules: RotationsRules;
  /** Which rotations a copy writes; null when the tab does not apply. */
  kind: 'GRAZING' | 'HAY' | null;
  termStartYear: number | null;
  termEndYear: number | null;
  pfuRevisionCount: number | null;
}

export interface CopyRotationRequest {
  sourceForestFileId: string;
  sourceYear: string;
  sourceRangeUnitId: string;
  targetYears: string[];
  everyOtherYearFrom: string;
  overwrite: boolean;
  pfuRevisionCount: number | null;
}

export interface CopyRotationResult {
  /** False: nothing was written; `message` asks to confirm overwriting `targetYears`. */
  copied: boolean;
  message: string;
  targetYears: number[];
}

/** GET …/rotations/copy. */
export function getCopyRotationTab(forestFileId: string): Promise<CopyRotationTab> {
  return apiGet<CopyRotationTab>(`${base(forestFileId)}/copy`);
}

/** POST …/rotations/copy. */
export function copyRotations(
  forestFileId: string,
  body: CopyRotationRequest,
): Promise<CopyRotationResult> {
  return apiPost<CopyRotationResult>(`${base(forestFileId)}/copy`, body);
}
