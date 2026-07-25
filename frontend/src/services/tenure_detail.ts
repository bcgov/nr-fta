import { apiGet, toQuery } from './http';

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
  return apiGet<TenureDetail>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}${toQuery({})}`,
  );
}
