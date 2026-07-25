import { apiGet, apiPost, toQuery } from './http';

// Mirrors the backend ApplicationDetailDto (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_952X_TAMF_DET mainline header parameters
// plus the tenure_application record columns.
export interface ApplicationDetail {
  tenureAppId: string;
  forestFileId: string | null;
  fileTypeCode: string | null;
  fileTypeDesc: string | null;
  adminOrg: string | null;
  licencee: string | null;
  clientNumber: string | null;
  status: string | null;
  statusDate: string | null; // ISO date
  awardDate: string | null; // ISO date
  expiryDate: string | null; // ISO date
  tenureAppType: string | null;
  description: string | null;
  purposeDesc: string | null;
  harvestTypeCode: string | null;
}

export interface ApplicationDetailParams {
  forestFileId?: string;
}

/** GET /api/fta/applications/{esfId} — application detail (FTA_952X_TAMF_DET). */
export function getApplicationDetail(
  esfId: string,
  params: ApplicationDetailParams = {},
): Promise<ApplicationDetail> {
  return apiGet<ApplicationDetail>(
    `/api/fta/applications/${encodeURIComponent(esfId)}${toQuery({ ...params })}`,
  );
}

// Mirrors the backend ApplicationAdjudicateRequest (ca.bc.gov.nrs.fta.tenure.dto),
// which mirrors the legacy THE.FTA_302_ADJUDCOMMENT mainline write parameters.
// `action` selects the mainline branch: 'ADJUDICATION' (full adjudication) or
// 'SAVE' (comment-only save).
export interface AdjudicateApplicationRequest {
  action: string;
  adjudicationComment?: string | null;
  revisionCount?: number | null;
}

/**
 * POST /api/fta/applications/{esfId}/actions — adjudicate a tenure application
 * (FTA_302_ADJUDCOMMENT). Returns the number of rows updated (FTA_ADMIN only).
 */
export function adjudicateApplication(
  esfId: string,
  req: AdjudicateApplicationRequest,
): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>(
    `/api/fta/applications/${encodeURIComponent(esfId)}/actions`,
    req,
  );
}
