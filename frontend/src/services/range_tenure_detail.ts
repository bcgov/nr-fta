import { apiGet } from './http';

// Mirrors the backend RangeTenureDetailDto (ca.bc.gov.nrs.fta.range.dto), which
// aggregates the legacy THE.FTA_100RANGE_TENURE detail with its range usage
// (THE.FTA_613R_RANGE_USAGE) and land base (THE.FTA_615_RANGE_LAND_BASE) tabs.

/** One row of range usage per calendar year (FTA_613R rec_range_usage_results). */
export interface RangeUsage {
  forestFileId: string | null;
  calendarYear: number | null;
  authorizedUse: number | null;
  nonUseAgreementNo: number | null;
  nonUseNonbillable: number | null;
  nonUseBillable: number | null;
  rangeNonUseReasonCode: string | null;
  tempIncrease: number | null;
  rangeIncreaseReasonCode: string | null;
  totalAnnualUse: number | null;
  revisionCount: number | null;
}

/** One associated private-land parcel (FTA_615 rec_range_land_base_results). */
export interface RangeLandBase {
  landBaseSkey: number | null;
  rangeLandBasePid: string | null;
  rangeLandBaseId: string | null;
  description: string | null;
  rangeLandOwnershipTypeCode: string | null;
  rangeLandPurposeCode: string | null;
  fencedPasture: number | null;
  unfencedPasture: number | null;
  forageProduction: number | null;
  landBaseComment: string | null;
  leaseStartDate: string | null; // ISO date
  leaseEndDate: string | null; // ISO date
  rangeLandBaseActInd: string | null;
  rangeLandOwnershipTypeDesc: string | null;
  rangeLandPurposeDesc: string | null;
  revisionCount: number | null;
}

/** One special condition on the tenure (FTA_100RANGE rec_spec_condition_results). */
export interface RangeSpecialCondition {
  specialConditionSkey: number | null;
  rangeSpecialConditionCode: string | null;
  conditionTitle: string | null;
  conditionDescription: string | null;
  rscRevisionCount: number | null;
}

/** Aggregated range tenure detail (FTA_100RANGE_TENURE + usage + land base). */
export interface RangeTenureDetail {
  forestFileId: string;
  fileTypeCode: string | null;
  adminOrgUnitNo: string | null;
  forestDistrictNo: string | null;
  districtAdminZone: string | null;
  licensee: string | null;
  fileStatusSt: string | null;
  fileStatusDate: string | null; // ISO date
  mgmtUnitType: string | null;
  mgmtUnitId: string | null;
  fileName: string | null;
  issueDate: string | null; // ISO date
  expiryDate: string | null; // ISO date
  originalIssueDate: string | null; // ISO date
  tenureTermYears: number | null;
  replacementTermYears: number | null;
  replacementCount: number | null;
  rangeInd: string | null;
  rangeUsage: RangeUsage[];
  landBase: RangeLandBase[];
  specialConditions: RangeSpecialCondition[];
}

/** GET /api/fta/range-tenures/{agreementId} — range tenure detail (FTA_100RANGE_TENURE). */
export function getRangeTenureDetail(agreementId: string): Promise<RangeTenureDetail> {
  return apiGet<RangeTenureDetail>(`/api/fta/range-tenures/${encodeURIComponent(agreementId)}`);
}
