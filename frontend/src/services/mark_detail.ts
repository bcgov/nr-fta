import { apiGet } from './http';

// Mirrors the backend MarkDetailDto (ca.bc.gov.nrs.fta.mark.dto), which ports
// the legacy THE.FTA_510_PRIVATE_MARK.GET record enriched with the land index
// (FTA_511), associated clients (FTA_513) and amendment history.

export interface MarkLandIndex {
  primaryLandIndexCode: string | null;
  secondaryLandIndexCode: string | null;
  primaryLandIndexCodeDesc: string | null;
  secondaryLandIndexCodeDesc: string | null;
  markLandIndexDesc: string | null;
  indexDeactivateDate: string | null; // ISO date
  markLandIndexSkey: number | null;
  revisionCount: number | null;
}

export interface MarkAssociatedClient {
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  clientCity: string | null;
  forClientLinkSkey: number | null;
  fileClientType: string | null;
  fileClientTypeDesc: string | null;
  licenseeStartDt: string | null; // ISO date
  licenseeEndDate: string | null; // ISO date
  revisionCount: number | null;
}

export interface MarkAmendment {
  amendRequestDate: string | null; // ISO date
  prvMrkAmdStsSt: string | null;
  revisionCount: number | null;
}

export interface MarkDetail {
  /** Null until the application is issued — the record is then known by its certificate. */
  timberMark: string | null;
  certificate: string | null;
  fileTypeCode: string | null;
  markStatusCode: string | null;
  markStatusDate: string | null; // ISO date
  markApplicationDate: string | null;
  markIssueDate: string | null;
  markExpiryDate: string | null;
  markCancelDate: string | null;
  tenureTerm: number | null;
  forestDistrict: string | null;
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  crownGrantedAcqDesc: string | null;
  grantedAcqrdDate: string | null;
  permitBlockLocn: string | null;
  permitBlockArea: number | null;
  proofOfCrownOrLegal: string | null;
  landIndex: MarkLandIndex[];
  clients: MarkAssociatedClient[];
  amendments: MarkAmendment[];
}

/**
 * GET /api/fta/marks/{id} — private mark detail (FTA_510/511/513). The id is a
 * timber mark, or with `byCertificate` the certificate of an application that
 * has no timber mark yet.
 */
export function getMarkDetail(id: string, byCertificate = false): Promise<MarkDetail> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiGet<MarkDetail>(`/api/fta/marks/${encodeURIComponent(id)}${query}`);
}

/** The detail route for a list row: by timber mark, else by certificate. */
export function markDetailPath(
  timberMark: string | null,
  certificate: string | null,
): string | null {
  if (timberMark) return `/marks/${encodeURIComponent(timberMark)}`;
  if (certificate) return `/marks/${encodeURIComponent(certificate)}?by=certificate`;
  return null;
}
