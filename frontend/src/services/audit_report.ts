import { apiGet, toQuery } from './http';

// Mirrors the backend AuditReportDto (ca.bc.gov.nrs.fta.shared.dto),
// which mirrors the legacy THE.FTA_402_PKG rec_FTA402 record (FTA402
// Private Mark Certificate report).
export interface AuditReport {
  timberMark: string;
  markIssueDate: string | null; // ISO date
  markExpiryDate: string | null; // ISO date
  fileTypeDesc: string | null;
  grantedAcqrdDate: string | null;
  crownGrantedAcqDesc: string | null;
  markAmendDate: string | null; // ISO date
  amendedUserid: string | null;
  activatedUserid: string | null;
  district: string | null;
  region: string | null;
  mainLicensee: string | null;
  address1: string | null;
  address2: string | null;
  address3: string | null;
  city: string | null;
  province: string | null;
  country: string | null;
  postalCode: string | null;
  pOfCOrLegal: string | null;
  mapReferenceId: string | null;
  secondaryClientCount: number | null;
}

export interface AuditReportParams {
  timberMark?: string;
  mainLicensee?: string;
}

/** GET /api/fta/admin/audit — FTA402 Private Mark Certificate report (FTA_402_PKG). */
export function fetchAuditReport(params: AuditReportParams): Promise<AuditReport[]> {
  return apiGet<AuditReport[]>(`/api/fta/admin/audit${toQuery({ ...params })}`);
}
