import { apiGet, apiGetForBlob } from './http';

/** The two reports FTA675 offers. */
export type AuditReportName = 'FTA403' | 'FTA405';

export interface AuditReportCriteria {
  report: AuditReportName | '';
  /** A region's ORG_UNIT_NO, or '*'. */
  region: string;
  /** A district's ORG_UNIT_NO, or '*'. */
  district: string;
  /** yyyy-mm-dd, or '' for no start. */
  fromDate: string;
  /** yyyy-mm-dd, or '' for no end. */
  toDate: string;
  /** Up to five, in order; '' for an unused one, '*' first for every type. */
  fileTypes: string[];
}

/**
 * GET /api/fta/admin/audit — FTA675 Audit Report: the FTA403 District Override Audit or the
 * FTA405 Delete EE Audit, as a PDF.
 */
export function getAuditReport(c: AuditReportCriteria): Promise<Blob> {
  const qs = new URLSearchParams();
  qs.set('report', c.report);
  qs.set('region', c.region);
  qs.set('district', c.district);
  if (c.fromDate) qs.set('fromDate', c.fromDate);
  if (c.toDate) qs.set('toDate', c.toDate);
  c.fileTypes.filter((t) => t !== '').forEach((t) => qs.append('fileType', t));
  return apiGetForBlob(`/api/fta/admin/audit?${qs.toString()}`);
}

/** Each district's ORG_UNIT_NO to its region's ORG_UNIT_NO. */
export const getDistrictRegions = () =>
  apiGet<Record<string, string>>('/api/fta/admin/audit/district-regions');
