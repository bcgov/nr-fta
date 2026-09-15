import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend HarvestingSearchDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the cursor record of THE.FTA_005_HVA_SEARCH.
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
  /** Numeric district org-unit number. */
  forestDistrict?: string;
  mgmtUnitType?: string;
  mgmtUnitId?: string;
  forestFileId?: string;
  cuttingPermitId?: string;
  /** A mark that validates is a key search: every other criterion is ignored. */
  timberMark?: string;
  hvaId?: string;
  fileTypeCode?: string;
  /** The screen's "CP Status". */
  harvestAuthStatusCode?: string;
  clientNumber?: string;
  clientLocationCode?: string;
  clientName?: string;
  clientTypeCode?: string;
  issueDateFrom?: string;
  issueDateTo?: string;
  expiryDateFrom?: string;
  expiryDateTo?: string;
  salvageTypeCode?: string;
  zone?: string;
  // ── Oil and Gas panel ──
  invoiceNumber?: string;
  /** 'Y' restricts the search to file type A11. */
  searchOnlyOg?: string;
  /** Labelled "App Determination Number" on screen. */
  ogcNumber?: string;
  geographicIdentifier?: string;
  /** Labelled "Purpose"; filters `HARVESTING_AUTHORITY.licence_to_cut_code`. */
  purposeCode?: string;
  ntsQuarter?: string;
  ntsMapUnit?: string;
  ntsMapBlock?: string;
  ntsMapsheetGrid?: string;
  ntsMapsheetLetter?: string;
  ntsMapsheetSquare?: string;
  /** '1' district, '2' client name, '3' file type. */
  sortBy?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** '1' district, '2' client name, '3' file type — the legacy sort values. */
export const SORT_DISTRICT = '1';
export const SORT_CLIENT = '2';
export const SORT_FILE_TYPE = '3';

/** The file type the "Only Oil and Gas" checkbox restricts the search to. */
export const OIL_AND_GAS_FILE_TYPE = 'A11';

/**
 * Whether to show the five oil and gas result columns. Mirrors the legacy
 * `getShowOilAndGasColumns`: the checkbox being ticked OR the file type being
 * A11. The two are independent — only the checkbox filters rows.
 */
export function showOilAndGasColumns(params: HarvestingSearchParams): boolean {
  return params.searchOnlyOg === 'Y' || params.fileTypeCode === OIL_AND_GAS_FILE_TYPE;
}

/** GET /api/fta/harvesting-authorities — FTA005 harvesting authority search. */
export function searchHarvestingAuthorities(
  params: HarvestingSearchParams,
): Promise<PageableResponse<HarvestingSearchResult>> {
  return apiGet<PageableResponse<HarvestingSearchResult>>(
    `/api/fta/harvesting-authorities${toQuery({ ...params })}`,
  );
}
