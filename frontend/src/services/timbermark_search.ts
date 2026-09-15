import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend TimbermarkSearchDto (ca.bc.gov.nrs.fta.mark.dto),
// which mirrors the legacy THE.FTA_002_MARK_SRCH rec_tenure_results record.
export interface TimbermarkSummary {
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  fileTypeCode: string | null;
  forestFileId: string | null;
  cuttingPermitId: string | null;
  timberMark: string | null;
  certificate: string | null;
  markStatusSt: string | null;
  markIssueDate: string | null; // ISO date
  markExpiryDate: string | null; // ISO date
  salvageInd: string | null;
  hvaSkey: number | null;
}

export interface TimbermarkSearchParams {
  /** Numeric district org-unit number. */
  adminOrgUnitNo?: string;
  districtAdminZone?: string;
  forestFileId?: string;
  cuttingPermitId?: string;
  /** A valid mark is a key search: every other criterion is ignored. */
  timberMark?: string;
  fileTypeCode?: string;
  markStatusSt?: string;
  clientNumber?: string;
  clientLocnCode?: string;
  clientName?: string;
  /** A (main) or B (secondary); absent means both. */
  fileClientType?: string;
  mgmtUnitType?: string;
  mgmtUnitId?: string;
  issueDateFrom?: string;
  issueDateTo?: string;
  expiryDateFrom?: string;
  expiryDateTo?: string;
  /** A salvage type code, or the literal 'ALL' for "has any salvage type". */
  salvageTypeCode?: string;
  // ── Private mark panel ──
  certificate?: string;
  /** PRIMARY_LAND_INDEX_CODE, despite the label. */
  landDistrict?: string;
  /** SECONDARY_LAND_INDEX_CODE, despite the label. */
  primaryId?: string;
  primaryDetail?: string;
  /** 'Y' restricts results to private mark file types. */
  privateMarkOnlyInd?: string;
  /** 'district' | 'client' | 'fileType'. */
  sortBy?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/timber-marks — timber mark search (FTA_002_MARK_SRCH). */
export function searchTimbermarks(
  params: TimbermarkSearchParams,
): Promise<PageableResponse<TimbermarkSummary>> {
  return apiGet<PageableResponse<TimbermarkSummary>>(
    `/api/fta/timber-marks${toQuery({ ...params })}`,
  );
}
