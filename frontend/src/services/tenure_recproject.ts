import { apiFetch, readErrorMessage } from './apiFetch';
import { apiDelete, apiGet, apiPost, apiPut } from './http';

import type { CodeOption } from './codeLists';

// The Rec project tab of the tenure detail — legacy FTA701 (Recreation Project
// Details). Mirrors the backend RecProjectDto / RecProjectRequests
// (ca.bc.gov.nrs.fta.tenure.tab.recproject).

/** What may be changed, and why not — legacy FTA_RECREATION_SECURITY. */
export interface RecProjectRules {
  /** Saving the project details. */
  project: boolean;
  projectReason: string | null;
  /** Fees, access types, districts and establishment orders. */
  child: boolean;
  childReason: string | null;
}

export interface RecProjectDistrict {
  districtCode: string;
  districtDesc: string | null;
}

export interface RecProjectFee {
  feeId: number;
  feeCode: string;
  feeDesc: string | null;
  amount: number | null;
  startDate: string | null; // ISO date
  endDate: string | null; // ISO date
  monday: boolean;
  tuesday: boolean;
  wednesday: boolean;
  thursday: boolean;
  friday: boolean;
  saturday: boolean;
  sunday: boolean;
  revisionCount: number;
}

export interface RecProjectAccess {
  accessCode: string;
  accessDesc: string | null;
  subAccessCode: string;
  subAccessDesc: string | null;
  revisionCount: number;
}

export interface RecProjectAttachment {
  attachmentId: number;
  fileName: string;
  sizeBytes: number | null;
}

export interface RecProject {
  /** False for a file FTA701 does not serve (not a RECnnnn recreation file). */
  applicable: boolean;
  notApplicableReason: string | null;
  /** Whether the project details have been saved yet. */
  exists: boolean;
  revisionCount: number | null;
  projectTypeCode: string | null;
  projectTypeDesc: string | null;
  projectLength: number | null;
  projectArea: number | null;
  /** RTR, IFT, TBL or RTE: Right of Way is entered, and required. */
  trailProject: boolean;
  projectName: string | null;
  projectEstablishedDate: string | null;
  riskRatingCode: string | null;
  riskRatingDesc: string | null;
  siteLocation: string | null;
  utmZone: number | null;
  utmEasting: number | null;
  utmNorthing: number | null;
  rightOfWay: number | null;
  featureCode: string | null;
  featureDesc: string | null;
  userDaysCode: string | null;
  userDaysDesc: string | null;
  maintainStdCode: string | null;
  maintainStdDesc: string | null;
  definedCampsites: number;
  campHostInd: string | null;
  overflowCampsites: number | null;
  lowMobilityAccessInd: string | null;
  recreationViewInd: string | null;
  resourceFeatureInd: string | null;
  associatedFiles: boolean;
  controlAccessCode: string | null;
  controlAccessDesc: string | null;
  lastRecInspectionDate: string | null;
  lastHzrdTreeAssessDate: string | null;
  archImpactAssessInd: string | null;
  archImpactDate: string | null;
  bordenNo: string | null;
  aiaComment: string | null;
  siteDescription: string | null;
  rules: RecProjectRules;
  districts: RecProjectDistrict[];
  fees: RecProjectFee[];
  accesses: RecProjectAccess[];
  attachments: RecProjectAttachment[];
}

/** The project details save — numbers as typed, Y/N indicators, ISO dates. */
export interface RecProjectSaveRequest {
  /** Null to create the project. */
  revisionCount: number | null;
  projectName: string;
  riskRatingCode: string | null;
  projectEstablishedDate: string | null;
  siteLocation: string | null;
  utmZone: string | null;
  utmEasting: string | null;
  utmNorthing: string | null;
  rightOfWay: string | null;
  featureCode: string | null;
  userDaysCode: string | null;
  maintainStdCode: string | null;
  campHostInd: string | null;
  overflowCampsites: string | null;
  lowMobilityAccessInd: string | null;
  recreationViewInd: string | null;
  resourceFeatureInd: string | null;
  controlAccessCode: string | null;
  lastRecInspectionDate: string | null;
  lastHzrdTreeAssessDate: string | null;
  archImpactAssessInd: string | null;
  archImpactDate: string | null;
  bordenNo: string | null;
  aiaComment: string | null;
  siteDescription: string | null;
}

export interface RecProjectFeeRequest {
  /** The fee's, when updating one. */
  revisionCount: number | null;
  feeCode: string;
  amount: string;
  startDate: string | null;
  endDate: string | null;
  monday: boolean;
  tuesday: boolean;
  wednesday: boolean;
  thursday: boolean;
  friday: boolean;
  saturday: boolean;
  sunday: boolean;
}

export interface RecProjectLookups {
  riskRatings: CodeOption[];
  features: CodeOption[];
  userDays: CodeOption[];
  controlAccess: CodeOption[];
  maintainStandards: CodeOption[];
  fees: CodeOption[];
  accessTypes: CodeOption[];
  subAccessTypes: CodeOption[];
  /** Which sub types each access type allows. */
  accessPairs: { accessCode: string; subAccessCode: string }[];
  districts: CodeOption[];
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/rec-project`;

/** GET /api/fta/tenures/{id}/rec-project — the project and its child lists (FTA701). */
export function getRecProject(forestFileId: string): Promise<RecProject> {
  return apiGet<RecProject>(base(forestFileId));
}

let lookups: Promise<RecProjectLookups> | null = null;

/** GET /api/fta/tenure-lookups/rec-project — FTA701's dropdowns (cached for the session). */
export function getRecProjectLookups(): Promise<RecProjectLookups> {
  lookups ??= apiGet<RecProjectLookups>('/api/fta/tenure-lookups/rec-project').catch(
    (err: unknown) => {
      lookups = null;
      throw err;
    },
  );
  return lookups;
}

/** PUT — create or update the project details. Returns legacy's confirmation messages. */
export function saveRecProject(
  forestFileId: string,
  request: RecProjectSaveRequest,
): Promise<{ warnings: string[] }> {
  return apiPut<{ warnings: string[] }>(base(forestFileId), request);
}

export function addRecDistrict(forestFileId: string, districtCode: string): Promise<void> {
  return apiPost<void>(`${base(forestFileId)}/districts`, { districtCode });
}

export function removeRecDistrict(forestFileId: string, districtCode: string): Promise<void> {
  return apiDelete<void>(`${base(forestFileId)}/districts/${encodeURIComponent(districtCode)}`);
}

export function addRecFee(forestFileId: string, request: RecProjectFeeRequest): Promise<void> {
  return apiPost<void>(`${base(forestFileId)}/fees`, request);
}

export function updateRecFee(
  forestFileId: string,
  feeId: number,
  request: RecProjectFeeRequest,
): Promise<void> {
  return apiPut<void>(`${base(forestFileId)}/fees/${feeId}`, request);
}

export function deleteRecFee(
  forestFileId: string,
  feeId: number,
  revisionCount: number,
): Promise<void> {
  return apiDelete<void>(`${base(forestFileId)}/fees/${feeId}?revisionCount=${revisionCount}`);
}

export function addRecAccess(
  forestFileId: string,
  accessCode: string,
  subAccessCode: string,
): Promise<void> {
  return apiPost<void>(`${base(forestFileId)}/accesses`, { accessCode, subAccessCode });
}

export function deleteRecAccess(forestFileId: string, access: RecProjectAccess): Promise<void> {
  return apiDelete<void>(
    `${base(forestFileId)}/accesses/${encodeURIComponent(access.accessCode)}/${encodeURIComponent(
      access.subAccessCode,
    )}?revisionCount=${access.revisionCount}`,
  );
}

/** The file's bytes, base64 — the upload goes as JSON like every other write. */
async function toBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = '';
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

/** POST — upload an establishment order (a PDF). */
export async function addRecAttachment(forestFileId: string, file: File): Promise<void> {
  return apiPost<void>(`${base(forestFileId)}/attachments`, {
    fileName: file.name,
    contentBase64: await toBase64(file),
  });
}

/** GET — an establishment order's PDF. */
export async function getRecAttachment(forestFileId: string, attachmentId: number): Promise<Blob> {
  const res = await apiFetch(`${base(forestFileId)}/attachments/${attachmentId}`, {
    method: 'GET',
    headers: { Accept: 'application/pdf' },
  });
  if (!res.ok) {
    const msg = await readErrorMessage(res);
    throw new Error(msg || `Request failed (${res.status})`);
  }
  return res.blob();
}

export function deleteRecAttachment(forestFileId: string, attachmentId: number): Promise<void> {
  return apiDelete<void>(`${base(forestFileId)}/attachments/${attachmentId}`);
}
