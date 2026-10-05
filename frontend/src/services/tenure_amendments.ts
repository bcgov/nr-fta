import { apiGet } from './http';

// Mirrors the backend AmendmentsDtos (ca.bc.gov.nrs.fta.tenure.tab.amendments)
// — legacy FTA905 (FTA_905_CP_AMEND / FTA_905_BLK_AMEND), read-only.

/** A cut block with amendments, and their totals. */
export interface AmendedBlock {
  /** The CP, or a Fort St. John authority's HVA id (`fsj`). */
  cuttingPermitId: string | null;
  fsj: boolean;
  timberMark: string | null;
  cutBlockId: string | null;
  cbSkey: number | null;
  totalNetArea: number | null;
  totalGrossArea: number | null;
  totalCruiseVolume: number | null;
}

export interface TenureAmendments {
  /** False for a file type FTA905 rejects (with legacy's reason). */
  available: boolean;
  unavailableReason: string | null;
  blocks: AmendedBlock[];
}

/** One amendment of a block; amendment 0 is the original. */
export interface BlockAmendment {
  amendmentId: number | null;
  netArea: number | null;
  grossArea: number | null;
  cruiseVolume: number | null;
  applicationDate: string | null; // ISO date
  statusCode: string | null;
  statusDate: string | null; // ISO date
  reasonCode: string | null;
  hasImage: boolean;
  imageMimeTypeCode: string | null;
  tenureAppId: number | null;
}

export interface BlockAmendments {
  cuttingPermitId: string | null;
  fsj: boolean;
  timberMark: string | null;
  cutBlockId: string | null;
  cbSkey: number | null;
  blockStatusCode: string | null;
  /** "CODE - Description". */
  blockStatus: string | null;
  blockStatusDate: string | null; // ISO date
  plannedNetArea: number | null;
  plannedGrossArea: number | null;
  disturbanceGrossArea: number | null;
  amendments: BlockAmendment[];
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/cp-cb-amendments`;

/** GET /api/fta/tenures/{forestFileId}/cp-cb-amendments. */
export function getTenureAmendments(forestFileId: string): Promise<TenureAmendments> {
  return apiGet<TenureAmendments>(base(forestFileId));
}

/** GET /api/fta/tenures/{forestFileId}/cp-cb-amendments/blocks/{cbSkey}. */
export function getBlockAmendments(forestFileId: string, cbSkey: number): Promise<BlockAmendments> {
  return apiGet<BlockAmendments>(`${base(forestFileId)}/blocks/${cbSkey}`);
}
