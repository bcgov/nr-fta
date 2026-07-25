import { apiGet, toQuery } from './http';

// Mirrors the backend RangeTenureSearchDto (ca.bc.gov.nrs.fta.range.dto),
// which mirrors the legacy THE.FTA_001R_TENR_SRCH rec_tenure_results record.
export interface RangeTenureSummary {
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  forestFileId: string;
  fileTypeCode: string | null;
  fileClientTypeDesc: string | null;
  mgmtUnitType: string | null;
  mgmtUnitId: string | null;
  fileStatusCode: string | null;
  fileStatusDesc: string | null;
  issueDate: string | null; // ISO date
  expiryDate: string | null;
}

export interface RangeTenureSearchParams {
  forestFileId?: string;
  fileTypeCode?: string;
  orgUnitCode?: string;
  zone?: string;
  clientName?: string;
  clientNumber?: string;
  clientLocnCode?: string;
  fileClientType?: string;
  fileStatus?: string;
  mgmtUnitType?: string;
  mgmtUnitId?: string;
  issueDateFrom?: string;
  issueDateTo?: string;
  expiryDateFrom?: string;
  expiryDateTo?: string;
  provisionYear?: string;
  authorizedUseFrom?: string;
  authorizedUseTo?: string;
  temporaryIncreaseFrom?: string;
  temporaryIncreaseTo?: string;
  billableNonUseFrom?: string;
  billableNonUseTo?: string;
  nonBillableNonUseFrom?: string;
  nonBillableNonUseTo?: string;
  totalAnnualUseFrom?: string;
  totalAnnualUseTo?: string;
}

/** GET /api/fta/range-tenures — range tenure search (FTA_001R_TENR_SRCH). */
export function searchRangeTenures(params: RangeTenureSearchParams): Promise<RangeTenureSummary[]> {
  return apiGet<RangeTenureSummary[]>(`/api/fta/range-tenures${toQuery({ ...params })}`);
}
