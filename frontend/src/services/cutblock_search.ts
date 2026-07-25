import { apiGet, toQuery } from './http';

// Mirrors the backend CutblockSearchDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_003_CUTBLK_SRCH rec_cut_block_results record.
export interface CutblockSearchResult {
  cbSkey: number | null;
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientName: string | null;
  forestFileId: string;
  cuttingPermitId: string | null;
  timberMark: string | null;
  cutBlockId: string | null;
  blockStatusSt: string | null;
  disturbanceStartDate: string | null; // ISO date
  disturbanceEndDate: string | null; // ISO date
}

export interface CutblockSearchParams {
  forestFileId?: string;
  cuttingPermitId?: string;
  timberMark?: string;
  cutBlockId?: string;
  blockStatusSt?: string;
  orgUnitNo?: string;
  clientNumber?: string;
  clientLocnCode?: string;
  clientName?: string;
  managedByFile?: string;
  managedByCp?: string;
  harvestStartDateFrom?: string;
  harvestStartDateTo?: string;
  districtAdminZone?: string;
}

/** GET /api/fta/cut-blocks — cut block search (FTA_003_CUTBLK_SRCH). */
export function searchCutBlocks(params: CutblockSearchParams): Promise<CutblockSearchResult[]> {
  return apiGet<CutblockSearchResult[]>(`/api/fta/cut-blocks${toQuery({ ...params })}`);
}
