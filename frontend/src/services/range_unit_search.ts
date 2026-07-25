import { apiGet, toQuery } from './http';

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
  orgUnitNo?: string;
  rangeUnitName?: string;
  pastureName?: string;
  rangeStatus?: string;
}

/** GET /api/fta/range-units — range unit / pasture search (FTA_006_RU_SRCH). */
export function searchRangeUnits(params: RangeUnitSearchParams): Promise<RangeUnitSummary[]> {
  return apiGet<RangeUnitSummary[]>(`/api/fta/range-units${toQuery({ ...params })}`);
}
