import { apiGet, apiPost } from './http';

/**
 * FTA640 criteria. Mirrors the backend ArchiveTenuresRequest
 * (ca.bc.gov.nrs.fta.tenure.dto): the archive covers every active grazing and hay
 * range tenure in the region (or the one district) whose term expired in the year.
 */
export interface ArchiveTenuresCriteria {
  /** ORG_UNIT_NO of the region. */
  regionNo: string;
  /** ORG_UNIT_NO of a district in that region, or null for the whole region. */
  districtNo: string | null;
  expiryYear: number;
}

/** One district's count. Mirrors ArchiveTenuresSummaryDto. */
export interface ArchiveTenuresSummary {
  district: string;
  grazingCount: number;
  hayCount: number;
}

/** GET /api/fta/admin/archive-tenures — what an archive would change, per district. */
export function getArchiveTenuresSummary(
  c: ArchiveTenuresCriteria,
): Promise<ArchiveTenuresSummary[]> {
  const q = new URLSearchParams({ regionNo: c.regionNo, expiryYear: String(c.expiryYear) });
  if (c.districtNo) q.set('districtNo', c.districtNo);
  return apiGet<ArchiveTenuresSummary[]>(`/api/fta/admin/archive-tenures?${q.toString()}`);
}

/**
 * POST /api/fta/admin/archive-tenures — set every tenure the summary counts to
 * status AR (FTA_ADMIN only). Refused until the expiry year is over.
 */
export function archiveTenures(c: ArchiveTenuresCriteria): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>('/api/fta/admin/archive-tenures', {
    regionNo: Number(c.regionNo),
    districtNo: c.districtNo ? Number(c.districtNo) : null,
    expiryYear: c.expiryYear,
  });
}
