import { apiGet, apiPut, toQuery } from './http';

// Mirrors the backend RatesMaintenanceDto (ca.bc.gov.nrs.fta.shared.dto),
// which mirrors the legacy THE.FTA_699_RATEFEE RANGE_BILL_RATE row (c_rate cursor).
export interface RatesMaintenanceRate {
  rangeBillRateId: number | null;
  calendarYear: number | null;
  updateTimestamp: string | null; // ISO date
  rangeFileTypeCode: string | null;
  rangeRateTypeCode: string | null;
  revenueClassnCode: string | null;
  rangeRate: number | null;
  updateUserid: string | null;
  rngTenrRateDesc: string | null;
}

export interface RatesMaintenanceParams {
  calendarYear?: number;
}

/** GET /api/fta/admin/rates — standard Range Billing rates/fees (FTA_699_RATEFEE). */
export function getRates(params: RatesMaintenanceParams = {}): Promise<RatesMaintenanceRate[]> {
  return apiGet<RatesMaintenanceRate[]>(`/api/fta/admin/rates${toQuery({ ...params })}`);
}

// Mirrors the backend RatesSaveRequest.RateItem (ca.bc.gov.nrs.fta.shared.dto).
export interface RatesSaveRateItem {
  rangeBillRateId: number | null;
  rangeFileTypeCode: string | null;
  rangeRateTypeCode: string | null;
  revenueClassnCode: string | null;
  rangeRate: number | null;
  rngTenrRateDesc: string | null;
}

// Mirrors the backend RatesSaveRequest (ca.bc.gov.nrs.fta.shared.dto).
export interface RatesSaveRequest {
  calendarYear: number | null;
  rates: RatesSaveRateItem[];
}

/** PUT /api/fta/admin/rates — save standard Range Billing rates/fees (FTA699, FTA_ADMIN only). */
export function saveRates(req: RatesSaveRequest): Promise<{ updated: number }> {
  return apiPut<{ updated: number }>('/api/fta/admin/rates', req);
}
