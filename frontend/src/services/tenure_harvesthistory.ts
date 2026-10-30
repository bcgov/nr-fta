import { apiGet } from './http';

// Mirrors the backend HarvestHistoryDtos (ca.bc.gov.nrs.fta.tenure.tab.harvesthistory)
// — legacy FTA990 (FTA_990_HARV_HIST), read-only.

/** Billed volume and dollars for a year, billing type and species — or the year's total. */
export interface HarvestHistoryRow {
  billingYear: number | null;
  /** Billing type description; null on a total row. */
  billingType: string | null;
  /** HDBS tree species; null on a total row. */
  species: string | null;
  volumeBilled: number | null;
  /** Upset + bonus. */
  totalBilled: number | null;
  upsetBilled: number | null;
  bonusBilled: number | null;
  /** The year's total over every billing type and species (listed after them). */
  subtotal: boolean;
}

export interface TenureHarvestHistory {
  /** False when the file is not B-type or has no hauling authority (timber mark). */
  available: boolean;
  unavailableReason: string | null;
  timberMark: string | null;
  /** "yyyy-MM". */
  oldestBillingAccessed: string | null;
  netCruiseVolume: number | null;
  billedVolume: number | null;
  totalBilled: number | null;
  initialRate: number | null;
  currentRate: number | null;
  originalUpsetRate: number | null;
  bonusBid: number | null;
  silvicultureLevy: number | null;
  developmentLevy: number | null;
  /** "Fixed/Adjustable Rate" — the appraisal's ADJUST_QRTERLY_IND as legacy showed it. */
  adjustQuarterlyInd: string | null;
  appraisalStatus: string | null;
  rows: HarvestHistoryRow[];
  /** Notices for parts this database could not read (a missing table). */
  unavailable: string[];
}

/** GET /api/fta/timber-marks/{timberMark}/harvest-history. */
export function getTimberMarkHarvestHistory(timberMark: string): Promise<TenureHarvestHistory> {
  return apiGet<TenureHarvestHistory>(
    `/api/fta/timber-marks/${encodeURIComponent(timberMark)}/harvest-history`,
  );
}

/** GET /api/fta/tenures/{forestFileId}/harvest-history. */
export function getTenureHarvestHistory(forestFileId: string): Promise<TenureHarvestHistory> {
  return apiGet<TenureHarvestHistory>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/harvest-history`,
  );
}
