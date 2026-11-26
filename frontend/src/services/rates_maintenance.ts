import { apiGet, apiPut } from './http';

/** One of the 12 values FTA699 shows. Mirrors RatesMaintenanceDto.Rate. */
export interface RateValue {
  /** Legacy's rate type code: GGR, GUF, GNF, HGR, HUF, HNF, GLF, GPF, NGI, HLF, HPF, NHI. */
  code: string;
  label: string;
  /** Null when the year is corrupt. */
  rate: number | null;
}

/**
 * One calendar year of rates and fees. Mirrors the backend RatesMaintenanceDto
 * (ca.bc.gov.nrs.fta.shared.dto), the OUT parameters of THE.FTA_699_RATEFEE.get.
 */
export interface RatesYear {
  calendarYear: number;
  /** yyyy-MM-dd HH:mm:ss; sent back on save as the lock. Null for a year not yet entered. */
  updateTimestamp: string | null;
  updateUserid: string | null;
  /** The stored rows are not the year's 24: no values, and Save is off. */
  corrupt: boolean;
  rates: RateValue[];
}

/** GET /api/fta/admin/rates/years — the Calendar Year dropdown, latest first. */
export function getRateYears(): Promise<number[]> {
  return apiGet<number[]>('/api/fta/admin/rates/years');
}

/** GET /api/fta/admin/rates?calendarYear= — one year's 12 values (FTA_699_RATEFEE.get). */
export function getRates(calendarYear: number): Promise<RatesYear> {
  return apiGet<RatesYear>(`/api/fta/admin/rates?calendarYear=${calendarYear}`);
}

/** Mirrors the backend RatesSaveRequest. */
export interface RatesSaveRequest {
  calendarYear: number;
  updateTimestamp: string | null;
  /** The 12 values as typed, keyed by code. */
  rates: Record<string, string>;
}

/** PUT /api/fta/admin/rates — save one year (FTA699, FTA_ADMIN only). */
export function saveRates(req: RatesSaveRequest): Promise<{ updated: number }> {
  return apiPut<{ updated: number }>('/api/fta/admin/rates', req);
}
