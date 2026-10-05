import { apiGet, apiPost, toQuery } from './http';

// Mirrors the backend TenureDetailDto (ca.bc.gov.nrs.fta.tenure.dto), which
// ports the file-level GET of THE.FTA_100_TENURE, enriched with the AAC summary
// (THE.FTA_930_AAC) and sale-info summary (THE.FTA_940_SALE_INFO).
export interface TenureDetail {
  // file-level header / common tenure (FTA_100_TENURE)
  forestFileId: string;
  fileTypeCode: string | null;
  fileStatusCode: string | null;
  fileStatusDesc: string | null;
  fileStatusDate: string | null; // ISO date
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  licensee: string | null;
  mgmtUnitType: string | null;
  mgmtUnitId: string | null;
  managementUnit: string | null;
  awardDate: string | null; // ISO date
  expiryDate: string | null; // ISO date
  initialExpiryDate: string | null; // ISO date
  tenureTermMonths: number | null;
  extensionCount: number | null;
  secLicenseeInd: string | null;
  notesLabel: string | null;
  // AAC summary (FTA_930_AAC)
  scheduleAArea: number | null;
  scheduleBArea: number | null;
  allowableAnnualCut: number | null;
  // sale info summary (FTA_940_SALE_INFO)
  saleMethodCode: string | null;
  saleTypeCode: string | null;
  paymentMethodCode: string | null;
  cashSaleEstVol: number | null;
  cashSaleTotDol: number | null;
  ftaBonusBid: number | null;
  ftaBonusOffer: number | null;
  scrtyDepositCode: string | null;
  scrtyDepositAmt: number | null;
}

/** GET /api/fta/tenures/{forestFileId} — tenure detail (FTA_100_TENURE). */
export function getTenureDetail(forestFileId: string): Promise<TenureDetail> {
  return apiGet<TenureDetail>(`/api/fta/tenures/${encodeURIComponent(forestFileId)}${toQuery({})}`);
}

/** One cutting permit of a tenure — mirrors the backend TenureCuttingPermitDto (FTA901). */
export interface TenureCuttingPermit {
  orgUnitCode: string | null;
  /** The CP, or a Fort St. John authority's harvesting authority id. */
  cuttingPermitId: string | null;
  timberMark: string | null;
  statusCode: string | null;
  /** "<code> - <description>". */
  statusDesc: string | null;
  issueDate: string | null; // ISO date
  expiryDate: string | null; // ISO date
  extendDate: string | null; // ISO date
  salvageTypeCode: string | null;
  hvaSkey: number | null;
  fsj: boolean;
}

/** GET /api/fta/tenures/{forestFileId}/cutting-permits — the tenure's cutting permits (FTA901). */
export function getTenureCuttingPermits(forestFileId: string): Promise<TenureCuttingPermit[]> {
  return apiGet<TenureCuttingPermit[]>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/cutting-permits`,
  );
}

/** Mirrors the backend CuttingPermitCreateRequest — a new cutting permit (ESF create). */
export interface CuttingPermitCreateRequest {
  cuttingPermitId: string;
  /** District ORG_UNIT_NO. */
  forestDistrict: string;
  location: string | null;
  /** Months: at most 48 (60 for an A11). */
  tenureTerm: number;
  harvestArea: number | null;
  markingMethodCode: string;
  /** Not needed when the marking method is E. */
  markingInstrumentCode: string | null;
  salvageTypeCode: string | null;
  /** Null for the district's default. */
  cascadeSplitCode: string | null;
  deciduous: boolean;
  catastrophic: boolean;
  cruiseBased: boolean;
}

/**
 * POST /api/fta/tenures/{forestFileId}/cutting-permits — add a cutting permit with
 * its timber mark (FTA_ADMIN). Returns the CP and the mark generated for it.
 */
export function addCuttingPermit(
  forestFileId: string,
  request: CuttingPermitCreateRequest,
): Promise<{ cuttingPermitId: string; timberMark: string }> {
  return apiPost<{ cuttingPermitId: string; timberMark: string }>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/cutting-permits`,
    request,
  );
}
