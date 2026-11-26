import { apiGet, apiPost } from './http';

// Mirrors the backend ca.bc.gov.nrs.fta.tenure.tab.tlblocks DTOs — legacy FTA980
// (TL Block Summary, THE.FTA_980_TLBLOCK).

/** What may be done on the tab (TlBlockRules); role checks are the page's. */
export interface TlBlockRules {
  /** Whether the file is a Timber Licence (A06), so the tab applies at all. */
  timberLicence: boolean;
  retire: boolean;
  retireReason: string | null;
}

/** One block of TL_BLOCK_AREA. */
export interface TlBlock {
  tlBlockId: string;
  grossHa: number | null;
  eliminHa: number | null;
  netHa: number | null;
  retirementDate: string | null; // ISO date; null while active
  revisionCount: number | null;
}

export interface TlBlocksResponse {
  rules: TlBlockRules;
  blocks: TlBlock[];
  totalGrossHa: number;
  totalEliminHa: number;
  totalNetHa: number;
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/tl-blocks`;
const one = (forestFileId: string, tlBlockId: string) =>
  `${base(forestFileId)}/${encodeURIComponent(tlBlockId)}`;

/** GET …/tl-blocks — the licence's blocks, totals and rules. */
export function getTlBlocks(forestFileId: string): Promise<TlBlocksResponse> {
  return apiGet<TlBlocksResponse>(base(forestFileId));
}

/** POST …/tl-blocks/{id}/retire or /unretire — set or clear the retirement date. */
export function setTlBlockRetired(
  forestFileId: string,
  tlBlockId: string,
  retire: boolean,
  revisionCount: number | null,
): Promise<void> {
  return apiPost<void>(`${one(forestFileId, tlBlockId)}/${retire ? 'retire' : 'unretire'}`, {
    revisionCount,
  });
}
