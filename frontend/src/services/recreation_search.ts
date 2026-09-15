import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend RecreationSearchDto (ca.bc.gov.nrs.fta.recreation.dto).
// The legacy cursor returns eight columns but the screen renders five; one of
// the three it drops is a duplicate of `projectType`.
export interface RecreationSearchResult {
  forestFileId: string | null;
  fileStatusCode: string | null;
  orgUnitCode: string | null;
  /** Carried because the "Admin Org" sort orders by name while the grid shows the code. */
  orgUnitName: string | null;
  projectName: string | null;
  projectType: string | null;
}

export interface RecreationSearchParams {
  /** Numeric admin org unit. The one mandatory criterion. */
  orgUnit?: string;
  mgmtUnitType?: string;
  mgmtUnitNumber?: string;
  /** Exact match unless you include `%` — legacy adds no wildcard of its own. */
  fileId?: string;
  fileStatus?: string;
  fileStatusFrom?: string;
  fileStatusTo?: string;
  projectName?: string;
  projectType?: string;
  riskRating?: string;
  controlledAccessType?: string;
  maintenanceStandard?: string;
  /** Matches projects with MORE than this many campsites, not exactly this many. */
  definedCampingSpaces?: string;
  /** 'Y' old files only, 'N' new files only, absent for both. */
  oldFileInd?: string;
  recreationDistrict?: string;
  resourceFeatureInd?: string;
  /** 'FID' | 'AOU' | 'FS' | 'PN'. */
  sortBy?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

export const SORT_FILE_ID = 'FID';
export const SORT_ADMIN_ORG = 'AOU';
export const SORT_FILE_STATUS = 'FS';
export const SORT_PROJECT_NAME = 'PN';

/** Restrict to the older REC_PROJECT generation (file ids like 900…). */
export const OLD_FILES = 'Y';

/** Restrict to the newer RECREATION_PROJECT generation (file ids like REC…). */
export const NEW_FILES = 'N';

/**
 * Whether the chosen criteria cannot all be honoured.
 *
 * Risk rating, controlled access, maintenance standard and resource feature are
 * columns on `RECREATION_PROJECT` only — `REC_PROJECT` does not have them. Asking
 * for old files while setting any of those means those filters cannot apply.
 * Legacy drops them silently and returns a wider result set; we say so instead.
 */
export function hasUnappliedCriteria(params: RecreationSearchParams): boolean {
  if (params.oldFileInd !== OLD_FILES) return false;
  return Boolean(
    params.riskRating ||
    params.controlledAccessType ||
    params.maintenanceStandard ||
    params.resourceFeatureInd,
  );
}

/** GET /api/fta/recreation — FTA007 recreation search. */
export function searchRecreation(
  params: RecreationSearchParams,
): Promise<PageableResponse<RecreationSearchResult>> {
  return apiGet<PageableResponse<RecreationSearchResult>>(
    `/api/fta/recreation${toQuery({ ...params })}`,
  );
}
