import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

import type { CodeOption } from './codeLists';

/**
 * What the caller may do to the permit — the backend's CuttingPermitRules, which refuses on write
 * whatever this says is off.
 */
export interface CuttingPermitRules {
  canSave: boolean;
  termsLocked: boolean;
  canEditExtend: boolean;
  canEditArea: boolean;
  canEditPurpose: boolean;
  canEditMarking: boolean;
  canDelete: boolean;
  canRetire: boolean;
  canManageConditions: boolean;
  canPostpone: boolean;
  canChangePostponements: boolean;
  canRescindAnytime: boolean;
  canAssignMarks: boolean;
  canSuspendBlocks: boolean;
}

// Mirrors the backend CuttingPermitDetailDto (ca.bc.gov.nrs.fta.tenure.dto),
// which ports the legacy THE.FTA_902_CP_DETAIL cutting-permit detail screen.
export interface CuttingPermitDetail {
  hvaSkey: number;
  forestFileId: string | null;
  cuttingPermitId: string | null;
  timberMark: string | null;
  fileTypeCode: string | null;
  fileTypeDescription: string | null;
  adminOrgCode: string | null;
  licensee: string | null;
  statusCode: string | null;
  statusDesc: string | null;
  statusDate: string | null; // ISO date
  issueDate: string | null;
  expiryDate: string | null;
  extendDate: string | null;
  extendReasonCode: string | null;
  extendCount: number | null;
  tenureTermYears: number | null;
  tenureTermMonths: number | null;
  forestDistrict: string | null;
  quotaTypeCode: string | null;
  salvageTypeCode: string | null;
  deciduousInd: string | null;
  catastrophicInd: string | null;
  cruiseBasedInd: string | null;
  crownLandsRegionCode: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  districtAdmnZone: string | null;
  harvestArea: number | null;
  location: string | null;
  mgmtUnitId: string | null;
  mgmtUnitTypeCode: string | null;
  licenceToCutCode: string | null;
  extAppFeeWaived: string | null;
  extAppFeePaid: string | null;
  withinFibreRecoveryZone: string | null;
  retirementDate: string | null;
  revisionCount: number;
  rules: CuttingPermitRules;
}

/** A write's outcome: legacy's warnings, which do not stop the save. */
export interface PermitWriteResult {
  warnings: string[];
}

export interface PermitSaveRequest {
  revisionCount: number;
  statusCode: string;
  statusDate: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  extendDate: string | null;
  extendReasonCode: string | null;
  extendCount: string | null;
  termYears: string | null;
  termMonths: string | null;
  quotaTypeCode: string | null;
  salvageTypeCode: string | null;
  deciduousInd: string;
  catastrophicInd: string;
  cruiseBasedInd: string;
  crownLandsRegionCode: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  districtAdmnZone: string | null;
  location: string | null;
  harvestArea: string | null;
  mgmtUnitTypeCode: string | null;
  mgmtUnitId: string | null;
  purposeCode: string | null;
  extAppFeeWaived: string | null;
  extAppFeePaid: string | null;
  withinFibreRecoveryZone: string | null;
}

export interface PermitCondition {
  id: number;
  themeCode: string;
  themeDescription: string;
  description: string | null;
}

export interface PermitPostponement {
  id: number;
  startDate: string | null;
  expiryDate: string | null;
  rescinded: string | null;
  previousExpiryDate: string | null;
  termMonths: number | null;
  termYears: number | null;
}

export interface PermitPostponementRequest {
  revisionCount: number;
  startDate: string | null;
  expiryDate: string | null;
  termYears: string | null;
  termMonths: string | null;
  rescinded: string;
}

/** The edit dialog's lists that the shared code-list API does not carry. */
export interface PermitLookups {
  quotaTypes: CodeOption[];
  extendReasons: CodeOption[];
  crownLandsRegions: CodeOption[];
  conditionThemes: CodeOption[];
}

const permitPath = (hvaSkey: number) => `/api/fta/cutting-permits/${hvaSkey}`;

/**
 * GET /api/fta/cutting-permits/{cpId} — cutting permit detail (FTA_902_CP_DETAIL). A permit id is
 * unique only within its tenure, so the tenure is required.
 */
export function getCuttingPermitDetail(
  cpId: string,
  forestFileId: string,
): Promise<CuttingPermitDetail> {
  return apiGet<CuttingPermitDetail>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}${toQuery({ forestFileId })}`,
  );
}

/** PUT /api/fta/cutting-permits/{hvaSkey} — FTA902's Save. */
export const savePermit = (hvaSkey: number, req: PermitSaveRequest) =>
  apiPut<PermitWriteResult>(permitPath(hvaSkey), req);

/** DELETE /api/fta/cutting-permits/{hvaSkey} — FTA902's Delete, of an EE permit. */
export const deletePermit = (hvaSkey: number, revisionCount: number) =>
  apiDelete<PermitWriteResult>(`${permitPath(hvaSkey)}${toQuery({ revisionCount })}`);

/** POST /api/fta/cutting-permits/{hvaSkey}/retire — FTA902's Retire. */
export const retirePermit = (hvaSkey: number, revisionCount: number) =>
  apiPost<PermitWriteResult>(`${permitPath(hvaSkey)}/retire`, { revisionCount });

export const getPermitConditions = (hvaSkey: number) =>
  apiGet<PermitCondition[]>(`${permitPath(hvaSkey)}/conditions`);

export const addPermitCondition = (
  hvaSkey: number,
  req: { themeCode: string; description: string | null },
) => apiPost<PermitWriteResult>(`${permitPath(hvaSkey)}/conditions`, req);

export const deletePermitCondition = (hvaSkey: number, id: number) =>
  apiDelete<PermitWriteResult>(`${permitPath(hvaSkey)}/conditions/${id}`);

export const getPermitPostponements = (hvaSkey: number) =>
  apiGet<PermitPostponement[]>(`${permitPath(hvaSkey)}/postponements`);

export const addPermitPostponement = (hvaSkey: number, req: PermitPostponementRequest) =>
  apiPost<PermitWriteResult>(`${permitPath(hvaSkey)}/postponements`, req);

export const updatePermitPostponement = (
  hvaSkey: number,
  id: number,
  req: PermitPostponementRequest,
) => apiPut<PermitWriteResult>(`${permitPath(hvaSkey)}/postponements/${id}`, req);

export const deletePermitPostponement = (hvaSkey: number, id: number, revisionCount: number) =>
  apiDelete<PermitWriteResult>(
    `${permitPath(hvaSkey)}/postponements/${id}${toQuery({ revisionCount })}`,
  );

/** GET /api/fta/tenure-lookups/cutting-permit — fetched when a dialog needs them. */
export const getPermitLookups = () =>
  apiGet<PermitLookups>('/api/fta/tenure-lookups/cutting-permit');
