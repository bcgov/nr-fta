import { apiPost } from './http';

/**
 * Mirrors the backend CreateTenureRequest (ca.bc.gov.nrs.fta.tenure.dto): what legacy FTA100's
 * Add New asked for and has no default, by file type. Null where the type doesn't ask.
 */
export interface CreateTenureRequest {
  /** Typed for A02, A06, A30 and B40; optional for B10; FTA assigns every other type's. */
  forestFileId: string | null;
  fileTypeCode: string;
  /** ORG_UNIT_NO; file types starting A, U or P. */
  adminOrgUnitNo: string | null;
  /** ORG_UNIT_NO; every type except A… and P01. */
  districtNo: string | null;
  mgmtUnitType: string | null;
  mgmtUnitId: string | null;
  issueDate: string | null; // ISO date
  /** B05, B06, B07. */
  purposeCode: string | null;
  /** B05, B06, B07, B10. */
  districtAdminZone: string | null;
  location: string | null;
  quotaTypeCode: string | null;
  /** A30. */
  initialArea: string | null;
  /** B10. */
  fupTypeCode: string | null;
  fupFnUsageCode: string | null;
  fupCedarSpeciesInd: string | null;
  fupTreatyPurposeInd: string | null;
}

/** POST /api/fta/tenures — create a tenure (FTA_ADMIN). Returns the new file's id. */
export function createTenure(req: CreateTenureRequest): Promise<{ forestFileId: string }> {
  return apiPost<{ forestFileId: string }>('/api/fta/tenures', req);
}
