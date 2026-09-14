import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

export type { PageableResponse };

// Mirrors the backend TenureSummaryDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_001_TENR_SRCH rec_tenure_results record.
export interface TenureSummary {
  orgUnitCode: string;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  forestFileId: string;
  fileTypeCode: string | null;
  fileClientTypeDesc: string | null;
  // Supplied by the legacy package; null while the backend reads tables directly.
  mgmtUnitType: string | null;
  mgmtUnitId: string | null;
  fileStatusCode: string | null;
  fileStatusDesc: string | null;
  issueDate: string | null; // ISO date
  expiryDate: string | null;
}

/**
 * The FTA001 search criteria. Field for field, these are the legacy screen's
 * inputs and the `THE.FTA_001_TENR_SRCH.MAINLINE` parameters behind them.
 */
export interface TenureSearchParams {
  /** Numeric ORG_UNIT_NO. Matches by region or district depending on its level. */
  adminOrgUnitNo?: string;
  forestFileId?: string;
  /** One code, or several comma-separated. */
  fileTypeCode?: string;
  /** T (timber), R (range) or F (recreation). */
  tenureType?: string;
  fileStatus?: string;
  clientNumber?: string;
  clientLocnCode?: string;
  clientName?: string;
  /** A (main licensee) or B (secondary). */
  fileClientType?: string;
  mgmtUnitType?: string;
  mgmtUnitId?: string;
  fileSource?: string;
  assocFileId?: string;
  /** Recreation project name. */
  fileName?: string;
  issueDateFrom?: string;
  issueDateTo?: string;
  expiryDateFrom?: string;
  expiryDateTo?: string;
  salvageInd?: string;
  cashSaleInd?: string;
  /** Only meaningful when the file type is M01. */
  mapNotationTypeCode?: string;
  /** 'org' | 'client' | 'fileType'. */
  sortBy?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/tenures — common tenure search (FTA_001_TENR_SRCH). */
export function searchTenures(
  params: TenureSearchParams,
): Promise<PageableResponse<TenureSummary>> {
  return apiGet<PageableResponse<TenureSummary>>(`/api/fta/tenures${toQuery({ ...params })}`);
}
