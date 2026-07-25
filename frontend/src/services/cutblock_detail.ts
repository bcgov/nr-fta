import { apiGet, toQuery } from './http';

// Mirrors the backend CutblockDetailDto (ca.bc.gov.nrs.fta.tenure.dto), which
// ports the legacy THE.FTA_904_CUTBLKDETAIL.GET output columns. Nullable
// columns are `| null`; dates arrive as ISO date strings and areas as numbers.
export interface CutblockDetail {
  forestFileId: string | null;
  cuttingPermitId: string | null;
  cutBlockId: string | null;
  timberMark: string | null;
  forestDistrict: string | null;
  markStatus: string | null;
  markIssueDate: string | null; // ISO date
  markExpiryDate: string | null;
  markTerm: string | null;
  blockStatus: string | null;
  blockStatusDate: string | null;
  cutBlockDescription: string | null;
  spExemptInd: string | null;
  plannedGrossBlockArea: number | null;
  plannedNetBlockArea: number | null;
  disturbanceGrossArea: number | null;
  disturbanceStartDate: string | null;
  disturbanceEndDate: string | null;
  plannedHarvestDate: string | null;
  opening: string | null;
  openingId: number | null;
  referenceName: string | null;
  salvageTypeCode: string | null;
  cutRegulationCode: string | null;
  reforestDeclareTypeCode: string | null;
  harvestTypeCode: string | null;
  decisionDate: string | null;
  issuanceDate: string | null;
  fireHarvestingReasonCode: string | null;
  underPartitionOrder: string | null;
  reportedFireDate: string | null;
}

export interface CutblockDetailParams {
  forestFileId?: string;
  cuttingPermitId?: string;
}

/** GET /api/fta/cut-blocks/{blockId} — cut block detail (FTA_904_CUTBLKDETAIL). */
export function getCutblockDetail(
  blockId: string,
  params: CutblockDetailParams = {},
): Promise<CutblockDetail> {
  return apiGet<CutblockDetail>(
    `/api/fta/cut-blocks/${encodeURIComponent(blockId)}${toQuery({ ...params })}`,
  );
}
