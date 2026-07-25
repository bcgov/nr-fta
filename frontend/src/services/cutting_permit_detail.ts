import { apiGet, toQuery } from './http';

// Mirrors the backend CuttingPermitDetailDto (ca.bc.gov.nrs.fta.tenure.dto),
// which ports the legacy THE.FTA_902_CP_DETAIL cutting-permit detail screen.
export interface CuttingPermitDetail {
  forestFileId: string | null;
  cuttingPermitId: string | null;
  timberMark: string | null;
  fileTypeCode: string | null;
  fileTypeDescription: string | null;
  adminOrgCode: string | null;
  licensee: string | null;
  statusCode: string | null;
  statusDesc: string | null;
  statusDate: string | null; // ISO date
  issueDate: string | null;
  expiryDate: string | null;
  extendDate: string | null;
  extendReasonCode: string | null;
  extendCount: number | null;
  tenureTermYears: number | null;
  tenureTermMonths: number | null;
  forestDistrict: string | null;
  quotaTypeCode: string | null;
  salvageTypeCode: string | null;
  deciduousInd: string | null;
  catastrophicInd: string | null;
  cruiseBasedInd: string | null;
  crownLandsRegionCode: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  districtAdmnZone: string | null;
  harvestArea: number | null;
  location: string | null;
  mgmtUnitId: string | null;
  mgmtUnitTypeCode: string | null;
}

export interface CuttingPermitDetailParams {
  forestFileId?: string;
}

/** GET /api/fta/cutting-permits/{cpId} — cutting permit detail (FTA_902_CP_DETAIL). */
export function getCuttingPermitDetail(
  cpId: string,
  params: CuttingPermitDetailParams = {},
): Promise<CuttingPermitDetail> {
  return apiGet<CuttingPermitDetail>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}${toQuery({ ...params })}`,
  );
}
