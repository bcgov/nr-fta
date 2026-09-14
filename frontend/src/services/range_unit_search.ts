import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend RangeUnitSearchDto (ca.bc.gov.nrs.fta.range.dto),
// which mirrors the legacy THE.FTA_006_RU_SRCH rec_range_unit_results record.
export interface RangeUnitSummary {
  rangeUnitId: string;
  pastureId: string | null;
  rangeUnitName: string | null;
  pastureName: string | null;
  rangeUnitStatusDesc: string | null;
}

export interface RangeUnitSearchParams {
  /** Numeric ORG_UNIT_NO. A region matches every district rolling up to it. */
  orgUnitNo?: string;
  rangeUnitName?: string;
  pastureName?: string;
  rangeStatus?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/range-units — range unit / pasture search (FTA_006_RU_SRCH). */
export function searchRangeUnits(
  params: RangeUnitSearchParams,
): Promise<PageableResponse<RangeUnitSummary>> {
  return apiGet<PageableResponse<RangeUnitSummary>>(
    `/api/fta/range-units${toQuery({ ...params })}`,
  );
}
