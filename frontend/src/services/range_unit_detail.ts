import { apiGet } from './http';

// Mirrors the backend RangeUnitDetailDto.Pasture (ca.bc.gov.nrs.fta.range.dto),
// which mirrors the legacy THE.FTA_630_MN_RG_UN_PST rec_m_r_u_p_result record.
export interface RangeUnitPasture {
  pastureId: string;
  pastureName: string | null;
  pastureRevisionCount: number | null;
}

// Mirrors the backend RangeUnitDetailDto (ca.bc.gov.nrs.fta.range.dto), which
// mirrors the GET result of the legacy THE.FTA_630_MN_RG_UN_PST package.
export interface RangeUnitDetail {
  rangeUnitId: string;
  rangeUnitName: string | null;
  statusCode: string | null;
  statusDescription: string | null;
  statusDate: string | null; // ISO date
  region: string | null;
  regionDescription: string | null;
  district: string | null;
  districtDescription: string | null;
  districtAdminZone: string | null;
  revisionCount: number | null;
  pastures: RangeUnitPasture[];
}

/** GET /api/fta/range-units/{unitId} — range unit detail (FTA_630_MN_RG_UN_PST GET). */
export function getRangeUnitDetail(unitId: string): Promise<RangeUnitDetail> {
  return apiGet<RangeUnitDetail>(`/api/fta/range-units/${encodeURIComponent(unitId)}`);
}
