import { apiGet, toQuery } from './http';

// Mirrors the backend RoadDetailDto (ca.bc.gov.nrs.fta.tenure.dto), which
// mirrors the legacy THE.FTA_131_ROADSECTION rec_rsection record.
export interface RoadDetail {
  forestFileId: string | null;
  roadSectionId: string;
  markList: string | null;
  sectionCurrentAmendId: string | null;
  roadSectName: string | null;
  roadSectLength: string | null;
  roadOrigLength: string | null;
  roadSectionStatusCode: string | null;
  withinAlrInd: string | null;
  districtAdmnZone: string | null;
  sectionWidth: number | null;
  xferedFromForestFileId: string | null;
  xferedFromRoadSectionId: string | null;
  xferedToForestFileId: string | null;
  xferedToRoadSectionId: string | null;
  retirementDate: string | null; // ISO date
  revisionCount: number | null;
  updateEnabledInd: string | null;
  segmentsEnabledInd: string | null;
}

export interface RoadDetailParams {
  forestFileId?: string;
}

/** GET /api/fta/roads/{roadId} — road-section detail (FTA_131_ROADSECTION). */
export function getRoadDetail(roadId: string, params: RoadDetailParams = {}): Promise<RoadDetail> {
  return apiGet<RoadDetail>(`/api/fta/roads/${encodeURIComponent(roadId)}${toQuery({ ...params })}`);
}
