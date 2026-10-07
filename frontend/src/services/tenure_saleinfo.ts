import { apiGet, apiPut } from './http';

import type { CodeOption } from './codeLists';

/** Mirrors the backend SaleInfoRules — FTA940's gating and protection states. */
export interface SaleInfoRules {
  editable: boolean;
  reason: string | null;
  saleMethodMandatory: boolean;
  /** The Sale Method a new record starts with (D for some file types). */
  defaultSaleMethodCode: string | null;
  /** The only Sale Methods this file type takes; empty for any. */
  saleMethodAllowed: string[];
  /** BCTS file: bonus bid / offer come from the awarded bidder and are read-only. */
  bcts: boolean;
  /** Planned BCTS Cat. and Planned Sale Date are required. */
  bctsFileType: boolean;
  soldCatRequired: boolean;
  category3Allowed: boolean;
  withinAdminArea: boolean;
  adminAreaFile: boolean;
  salvage: boolean;
  minorFacility: boolean;
  /** The Cash Sale section applies. */
  cashSale: boolean;
  estimatedVolume: boolean;
  cashFieldsRequired: boolean;
  tenderRequired: boolean;
}

/** GET /api/fta/tenures/{id}/sale-info (SaleInfoDtos.SaleInfoResponse). Descriptions are "CODE - description". */
export interface SaleInfo {
  forestFileId: string;
  fileTypeCode: string | null;
  fileStatusCode: string | null;
  recordExists: boolean;
  saleMethodCode: string | null;
  saleMethodDesc: string | null;
  saleTypeCode: string | null;
  saleTypeDesc: string | null;
  paymentMethodCode: string | null;
  paymentMethodDesc: string | null;
  salvageInd: string | null;
  minorFacilityInd: string | null;
  adminAreaInd: string | null;
  adminAreaFile: string | null;
  plannedSaleDate: string | null;
  tenderOpeningDate: string | null;
  cashSaleEstVol: number | null;
  cashSaleTotDol: number | null;
  saleVolume: number | null;
  totalBidders: number | null;
  ftaBonusBid: number | null;
  ftaBonusOffer: number | null;
  bctsBonusBid: number | null;
  bctsLumpSumBonusOffer: number | null;
  bctsFundInd: string | null;
  bctsOrgUnitNo: string | null;
  bctsOrgDesc: string | null;
  plannedSbCatCode: string | null;
  plannedSbCatDesc: string | null;
  soldSbCatCode: string | null;
  soldSbCatDesc: string | null;
  scrtyDepositCode: string | null;
  scrtyDepositDesc: string | null;
  scrtyDepositAmt: number | null;
  otherDepositCode: string | null;
  otherDepositDesc: string | null;
  otherDepositAmt: number | null;
  /** An amount ("1234.50"), "N/A", or null. */
  annualRent: string | null;
  legalEffectiveDate: string | null;
  hsRevisionCount: number | null;
  tdRevisionCount: number | null;
  auRevisionCount: number | null;
  rules: SaleInfoRules;
}

/** PUT /api/fta/tenures/{id}/sale-info (SaleInfoDtos.SaleInfoUpdateRequest). */
export interface SaleInfoUpdateRequest {
  saleMethodCode: string | null;
  saleTypeCode: string | null;
  salvageInd: string | null;
  minorFacilityInd: string | null;
  adminAreaInd: string | null;
  adminAreaFile: string | null;
  plannedSaleDate: string | null;
  tenderOpeningDate: string | null;
  cashSaleEstVol: number | null;
  cashSaleTotDol: number | null;
  saleVolume: number | null;
  totalBidders: number | null;
  ftaBonusBid: number | null;
  ftaBonusOffer: number | null;
  plannedSbCatCode: string | null;
  soldSbCatCode: string | null;
  scrtyDepositCode: string | null;
  scrtyDepositAmt: number | null;
  otherDepositCode: string | null;
  otherDepositAmt: number | null;
  hsRevisionCount: number | null;
  tdRevisionCount: number | null;
  auRevisionCount: number | null;
}

const path = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/sale-info`;

export const getTenureSaleInfo = (forestFileId: string) => apiGet<SaleInfo>(path(forestFileId));

/** Saves the record; resolves with legacy's non-blocking warnings. */
export const saveTenureSaleInfo = (forestFileId: string, body: SaleInfoUpdateRequest) =>
  apiPut<{ warnings: string[] }>(path(forestFileId), body);

const lookups = new Map<string, Promise<CodeOption[]>>();

/** The edit form's code lists ("CODE - description", current codes), fetched once per page load. */
const lookup = (name: string): Promise<CodeOption[]> => {
  let hit = lookups.get(name);
  if (!hit) {
    hit = apiGet<CodeOption[]>(`/api/fta/tenure-lookups/${name}`);
    lookups.set(name, hit);
    hit.catch(() => lookups.delete(name));
  }
  return hit;
};

export const getSaleMethods = () => lookup('sale-info-sale-methods');
export const getSaleTypes = () => lookup('sale-info-sale-types');
export const getDepositTypes = () => lookup('sale-info-deposit-types');
export const getBctsCategories = () => lookup('sale-info-bcts-categories');
