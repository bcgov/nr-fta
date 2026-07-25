import { apiGet, toQuery } from './http';

// Mirrors the backend HarvestingSearchDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_HVA_SEARCH rec_results record.
export interface HarvestingSearchResult {
  hvaSkey: number | null;
  orgUnitCode: string | null;
  clientName: string | null;
  clientNumber: string | null;
  fileTypeCode: string | null;
  forestFileId: string | null;
  cuttingPermitId: string | null;
  timberMark: string | null;
  ogcNumber: string | null;
  ntsMapblock: string | null;
  ntsMapunit: string | null;
  ntsMapquarter: string | null;
  ntsMapsheetGrid: string | null;
  ntsMapsheetLetter: string | null;
  ntsMapsheetSquare: string | null;
  programNumber: string | null;
  geographicIdentifier: string | null;
}

export interface HarvestingSearchParams {
  cuttingPermitId?: string;
  timberMark?: string;
  forestFileId?: string;
  clientName?: string;
  orgUnitCode?: string;
}

/** GET /api/fta/harvesting-authorities — harvesting authority search (FTA_HVA_SEARCH). */
export function searchHarvestingAuthorities(
  params: HarvestingSearchParams,
): Promise<HarvestingSearchResult[]> {
  return apiGet<HarvestingSearchResult[]>(
    `/api/fta/harvesting-authorities${toQuery({ ...params })}`,
  );
}
