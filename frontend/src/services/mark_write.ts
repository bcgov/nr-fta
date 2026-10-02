import { apiPost } from './http';

/**
 * Mirrors the backend MarkApplicationRequest (ca.bc.gov.nrs.fta.mark.dto) — the
 * fields legacy FTA510 "Add New" stores. Dates are ISO yyyy-mm-dd.
 */
export interface MarkApplicationRequest {
  applicationDate: string;
  /** Months: 6, 12, 24, 36, 48 or 60. */
  tenureTerm: number;
  /** ORG_UNIT_NO of the district. */
  forestDistrict: string;
  permitBlockLocn: string;
  proofOfCrownOrLegal: string;
  bcaaFolioNumber: string;
  permitBlockArea: number;
  mgmtUnitTypeCode: string;
  mgmtUnitId: string | null;
  cascadeSplitCode: string;
  mapReferenceReg: string | null;
  mapReferenceComp: string | null;
  /** The mark holder — optional; with a number, the location is required. */
  clientNumber: string | null;
  clientLocnCode: string | null;
}

/**
 * POST /api/fta/marks — create a private mark application (FTA_ADMIN and the two
 * timber mark roles). Returns its certificate, which is how a new
 * application is known until a timber mark is assigned.
 */
export function createMarkApplication(
  req: MarkApplicationRequest,
): Promise<{ certificate: string }> {
  return apiPost<{ certificate: string }>('/api/fta/marks', req);
}

/** The legacy notes form's limit (Fta970ForestNotesForm); the backend enforces it too. */
export const MARK_NOTE_MAX_LENGTH = 4000;

/**
 * POST /api/fta/marks/{id}/notes — add a note to the mark's forest file
 * (FTA_970_FOREST_NOTE.ADD). FTA_ADMIN and the two timber mark roles only.
 */
export function addMarkNote(
  id: string,
  note: string,
  byCertificate = false,
): Promise<{ forestFileId: string }> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPost<{ forestFileId: string }>(
    `/api/fta/marks/${encodeURIComponent(id)}/notes${query}`,
    { note },
  );
}
