import { apiGet, apiPost, apiPut, toQuery } from './http';

import type { CodeOption } from './codeLists';

/** What the block allows (the backend's CutBlockRules, also enforced on its writes). */
export interface CutBlockRules {
  canEdit: boolean;
  statusLocked: boolean;
  plannedLocked: boolean;
  disturbanceLocked: boolean;
  spExemptLocked: boolean;
  canRelabel: boolean;
  canSuspend: boolean;
  canEditSuspension: boolean;
  /** Rollbacks are open to this user, who must then give a District Override Reason. */
  seniorAuthority: boolean;
}

export interface CutBlockSuspension {
  suspensionId: number | null;
  underPartition: string | null;
  orderNumber: string | null;
  startDate: string | null; // ISO date
  endDate: string | null;
}

// Mirrors the backend CutblockDetailDto (ca.bc.gov.nrs.fta.tenure.dto), which
// ports the legacy THE.FTA_904_CUTBLKDETAIL.GET output columns. Nullable
// columns are `| null`; dates arrive as ISO date strings and areas as numbers.
export interface CutblockDetail {
  cbSkey: number;
  hvaSkey: number | null;
  cbRevisionCount: number;
  cboaRevisionCount: number | null;
  fileTypeCode: string | null;
  forestFileId: string | null;
  cuttingPermitId: string | null;
  cutBlockId: string | null;
  timberMark: string | null;
  forestDistrict: string | null;
  markStatusCode: string | null;
  markStatus: string | null;
  markIssueDate: string | null; // ISO date
  markExpiryDate: string | null;
  markTerm: string | null;
  blockStatus: string | null;
  blockStatusDescription: string | null;
  blockStatusDate: string | null;
  cutBlockDescription: string | null;
  spExemptInd: string | null;
  plannedGrossBlockArea: number | null;
  plannedNetBlockArea: number | null;
  disturbanceGrossArea: number | null;
  disturbanceStartDate: string | null;
  disturbanceEndDate: string | null;
  plannedHarvestDate: string | null;
  opening: string | null;
  openingId: number | null;
  referenceName: string | null;
  salvageTypeCode: string | null;
  cutRegulationCode: string | null;
  reforestDeclareTypeCode: string | null;
  harvestTypeCode: string | null;
  decisionDate: string | null;
  issuanceDate: string | null;
  fireHarvestingReasonCode: string | null;
  underPartitionOrder: string | null;
  reportedFireDate: string | null;
  wasteAssessmentRequired: string | null;
  wasteDescription: string | null;
  totalLoggedArea: number | null;
  suspensions: CutBlockSuspension[];
  rules: CutBlockRules;
}

export interface CutblockDetailParams {
  forestFileId: string;
  cuttingPermitId?: string;
}

/** Mirrors the backend CutBlockEditRequest. */
export interface CutBlockEdit {
  blockStatus: string;
  blockStatusDate: string | null;
  cutBlockDescription: string | null;
  cutRegulationCode: string | null;
  reforestDeclareTypeCode: string | null;
  fireHarvestingReasonCode: string | null;
  underPartitionOrder: string | null;
  reportedFireDate: string | null;
  wasteAssessmentRequired: string | null;
  plannedGrossBlockArea: number | null;
  plannedNetBlockArea: number | null;
  plannedHarvestDate: string | null;
  disturbanceGrossArea: number | null;
  disturbanceStartDate: string | null;
  disturbanceEndDate: string | null;
  spExemptInd: string | null;
  overrideReason: string | null;
  cbRevisionCount: number;
  cboaRevisionCount: number;
}

export interface CutBlockSaved {
  cutBlockId: string;
  warnings: string[];
}

/**
 * GET /api/fta/cut-blocks/{blockId} — cut block detail (FTA_904_CUTBLKDETAIL). A label repeats
 * across files, so the file is required; the permit is needed where it repeats within one.
 */
export function getCutblockDetail(
  blockId: string,
  params: CutblockDetailParams,
): Promise<CutblockDetail> {
  return apiGet<CutblockDetail>(
    `/api/fta/cut-blocks/${encodeURIComponent(blockId)}${toQuery({ ...params })}`,
  );
}

/** PUT /api/fta/cut-blocks/{cbSkey} — FTA904's Save. */
export function saveCutBlock(cbSkey: number, edit: CutBlockEdit): Promise<CutBlockSaved> {
  return apiPut<CutBlockSaved>(`/api/fta/cut-blocks/${cbSkey}`, edit);
}

/** POST /api/fta/cut-blocks/{cbSkey}/relabel — FTA231's Save. */
export function relabelCutBlock(
  cbSkey: number,
  req: { newCutBlockId: string; reason: string; revisionCount: number },
): Promise<CutBlockSaved> {
  return apiPost<CutBlockSaved>(`/api/fta/cut-blocks/${cbSkey}/relabel`, req);
}

export const getCutRegulationCodes = () =>
  apiGet<CodeOption[]>('/api/fta/tenure-lookups/cut-regulation-codes');

export const getReforestDeclareTypes = () =>
  apiGet<CodeOption[]>('/api/fta/tenure-lookups/reforest-declare-types');

/** The query string that names one block on links to its screens. */
export const blockQuery = (forestFileId: string, cuttingPermitId?: string | null) => {
  const qs = new URLSearchParams({ forestFileId });
  if (cuttingPermitId) qs.set('cuttingPermitId', cuttingPermitId);
  return `?${qs.toString()}`;
};
