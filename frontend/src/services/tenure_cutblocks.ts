import { apiDelete, apiGet, apiPost } from './http';

import type { CodeOption } from './codeLists';

// Mirrors the backend CutBlocksDtos (ca.bc.gov.nrs.fta.tenure.tab.cutblocks) — the tenure
// detail's Cut block tab, legacy FTA903 Cut Block List.

/** One cut block of the tenure (FTA_903_CUTBLK_LST.GET). */
export interface TenureCutBlock {
  cbSkey: number;
  /** The CP, or a Fort St. John authority's harvesting authority id ("CP/HVA ID"). */
  cuttingPermitId: string | null;
  timberMark: string | null;
  salvageTypeCode: string | null;
  markStatusCode: string | null;
  cutBlockId: string;
  blockStatusCode: string | null;
  /** "<code> - <description>". */
  blockStatus: string | null;
  startDate: string | null; // ISO date
  endDate: string | null; // ISO date
  /** Areas, legacy-formatted with 4 decimals. */
  plannedGross: string | null;
  plannedNet: string | null;
  actualGross: string | null;
  /** Legacy's "Authorized File / CP": set for blocks managed by, or owned by, another file. */
  authorizedFileId: string | null;
  authorizedCpId: string | null;
  /** CUT_BLOCK's own file and CP — what the cut block detail page keys on. */
  blockForestFileId: string | null;
  blockCuttingPermitId: string | null;
  revisionCount: number;
  canDelete: boolean;
  deleteReason: string | null;
}

/** One suspension of one of the tenure's blocks (FTA_903_CB_SUSP_LIST.GET). */
export interface TenureCutBlockSuspension {
  cuttingPermitId: string | null;
  cutBlockId: string;
  suspensionOrderNo: string | null;
  /** "<code> - <description>". */
  underPartition: string | null;
  startDate: string | null;
  endDate: string | null;
}

/** A permit a block may be added to, with legacy's FTA903_ADD_NEW verdict. */
export interface TenureCutBlockPermit {
  hvaSkey: number;
  cuttingPermitId: string | null;
  timberMark: string | null;
  statusCode: string | null;
  salvageTypeCode: string | null;
  eligible: boolean;
  reason: string | null;
}

export interface TenureCutBlocksRules {
  /** False for the file types FTA903 refuses — it lists nothing for them. */
  listable: boolean;
  add: boolean;
  addReason: string | null;
}

export interface TenureCutBlocks {
  rules: TenureCutBlocksRules;
  blocks: TenureCutBlock[];
  suspensions: TenureCutBlockSuspension[];
  permits: TenureCutBlockPermit[];
}

/** A block to add (FTA904 add mode). Dates are yyyy-mm-dd. */
export interface TenureCutBlockCreateRequest {
  hvaSkey: number;
  cutBlockId: string;
  blockStatusDate: string | null;
  description: string | null;
  plannedGrossArea: number;
  plannedNetArea: number;
  plannedStartDate: string | null;
  spExempt: 'Y' | 'N';
  wasteAssessmentRequired: 'Y' | 'N';
  underPartitionOrder: 'Y' | 'N' | null;
  fireHarvestingReasonCode: string | null;
  reportedFireDate: string | null;
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/cut-blocks`;

/** GET /api/fta/tenures/{forestFileId}/cut-blocks — the Cut block tab (FTA903). */
export const getTenureCutBlocks = (forestFileId: string) =>
  apiGet<TenureCutBlocks>(base(forestFileId));

/** POST /api/fta/tenures/{forestFileId}/cut-blocks — add a block (status PP). */
export const addTenureCutBlock = (forestFileId: string, body: TenureCutBlockCreateRequest) =>
  apiPost<{ cbSkey: number; cutBlockId: string; timberMark: string | null }>(
    base(forestFileId),
    body,
  );

/** DELETE /api/fta/tenures/{forestFileId}/cut-blocks/{cbSkey} — with the deletion comment. */
export const deleteTenureCutBlock = (
  forestFileId: string,
  cbSkey: number,
  body: { revisionCount: number; comment: string },
) => apiDelete<void>(`${base(forestFileId)}/${cbSkey}`, body);

/** GET /api/fta/tenure-lookups/fire-harvesting-reasons — the add dialog's list. */
export const getFireHarvestingReasons = () =>
  apiGet<CodeOption[]>('/api/fta/tenure-lookups/fire-harvesting-reasons');
