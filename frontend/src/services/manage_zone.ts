import { apiGet, apiPost, toQuery } from './http';

// Mirrors the backend ManageZoneDto (ca.bc.gov.nrs.fta.range.dto),
// which mirrors the legacy THE.FTA_631_RANGE_ZONE rec_range_zone_results record.
export interface ManageZone {
  rangeZoneCode: string;
  zoneDescription: string | null;
  adminForestDistrictNo: number | null;
  contact: string | null;
  contactUserId: string | null;
  contactPhoneNumber: string | null;
  contactEmailAddress: string | null;
  revisionCount: number | null;
  orgUnitCode: string | null;
  orgUnitName: string | null;
}

export interface ManageZoneParams {
  adminForestDistrictNo?: string;
  rangeZoneCode?: string;
}

/** GET /api/fta/admin/range-zones — range zones for a district (FTA_631_RANGE_ZONE). */
export function searchRangeZones(params: ManageZoneParams = {}): Promise<ManageZone[]> {
  return apiGet<ManageZone[]>(`/api/fta/admin/range-zones${toQuery({ ...params })}`);
}

// Mirrors the backend ManageZoneAddRequest (ca.bc.gov.nrs.fta.range.dto),
// which mirrors the SAVE proc of the legacy THE.FTA_631_RANGE_ZONE package.
export interface ManageZoneAddRequest {
  rangeZoneCode: string;
  zoneDescription: string | null;
  adminForestDistrictNo: string | null;
  contact: string | null;
  contactUserId: string | null;
  contactPhoneNumber: string | null;
  contactEmailAddress: string | null;
}

/** POST /api/fta/admin/range-zones — save (insert or update) a range zone (FTA_ADMIN only). */
export function saveRangeZone(
  req: ManageZoneAddRequest,
): Promise<{ rangeZoneCode: string; updated: number }> {
  return apiPost<{ rangeZoneCode: string; updated: number }>('/api/fta/admin/range-zones', req);
}
