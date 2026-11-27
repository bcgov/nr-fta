import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

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
  /** ORG_UNIT_NO of the district. */
  district: string | null;
  districtDescription: string | null;
  /** ORG_UNIT_CODE of the district. */
  districtCode: string | null;
  districtAdminZone: string | null;
  revisionCount: number | null;
  /** Legacy's p_ind: the user may edit range files of the unit's district. */
  canEdit: boolean;
  pastures: RangeUnitPasture[];
}

/** GET /api/fta/range-units/{unitId} — range unit detail (FTA_630_MN_RG_UN_PST GET). */
export function getRangeUnitDetail(unitId: string): Promise<RangeUnitDetail> {
  return apiGet<RangeUnitDetail>(`/api/fta/range-units/${encodeURIComponent(unitId)}`);
}

/** Mirrors the backend RangeUnitSaveRequest: FTA630's Save Range. */
export interface RangeUnitSaveRequest {
  rangeUnitName: string;
  /** Ignored on an add, which is always P. */
  status?: string;
  /** yyyy-MM-dd; blank means today. */
  asOf?: string;
  /** ORG_UNIT_NO of the district. */
  district: string;
  districtAdminZone: string;
  /** The unit's revision count as read; required on a save. */
  revisionCount?: number | null;
}

/** Mirrors the backend RangeUnitPastureRequest: a pasture's Save. */
export interface RangeUnitPastureRequest {
  pastureId?: string;
  pastureName: string;
  pastureRevisionCount?: number | null;
  revisionCount?: number | null;
}

/** Mirrors the backend RangeUnitWriteResultDto. */
export interface RangeUnitWriteResult {
  rangeUnitId: string;
  /** Shown with "Save successful." — the As of date set to today. */
  warnings: string[];
}

const unitPath = (unitId: string) => `/api/fta/range-units/${encodeURIComponent(unitId)}`;

/** POST /api/fta/range-units — Add New, then Save Range; the id is generated. */
export const addRangeUnit = (body: RangeUnitSaveRequest) =>
  apiPost<RangeUnitWriteResult>('/api/fta/range-units', body);

/** PUT /api/fta/range-units/{unitId} — Save Range. */
export const saveRangeUnit = (unitId: string, body: RangeUnitSaveRequest) =>
  apiPut<RangeUnitWriteResult>(unitPath(unitId), body);

/** DELETE /api/fta/range-units/{unitId} — Delete Range, with its pastures. */
export const deleteRangeUnit = (unitId: string, revisionCount: number | null) =>
  apiDelete<void>(`${unitPath(unitId)}${toQuery({ revisionCount: revisionCount ?? undefined })}`);

/** POST /api/fta/range-units/{unitId}/pastures — add a pasture. */
export const addRangeUnitPasture = (unitId: string, body: RangeUnitPastureRequest) =>
  apiPost<RangeUnitWriteResult>(`${unitPath(unitId)}/pastures`, body);

/** PUT /api/fta/range-units/{unitId}/pastures/{pastureId} — update a pasture's description. */
export const updateRangeUnitPasture = (
  unitId: string,
  pastureId: string,
  body: RangeUnitPastureRequest,
) =>
  apiPut<RangeUnitWriteResult>(
    `${unitPath(unitId)}/pastures/${encodeURIComponent(pastureId)}`,
    body,
  );

/** DELETE /api/fta/range-units/{unitId}/pastures/{pastureId} — delete a pasture. */
export const deleteRangeUnitPasture = (
  unitId: string,
  pastureId: string,
  pastureRevisionCount: number | null,
) =>
  apiDelete<void>(
    `${unitPath(unitId)}/pastures/${encodeURIComponent(pastureId)}${toQuery({
      pastureRevisionCount: pastureRevisionCount ?? undefined,
    })}`,
  );
