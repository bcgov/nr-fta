import { apiGet, apiPost, toQuery } from './http';

// Mirrors the backend RangeClientsDtos (ca.bc.gov.nrs.fta.range.dto) — legacy FTA920R, the range
// tenure's Assoc. Client tab (THE.FTA_920R_ASSO_CLIENTS_DETAILS).

/** One row of "Licensee (Current & Previous)" (FOREST_FILE_CLIENT). */
export interface RangeClient {
  forestFileClientSkey: number | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  clientAddress: string | null;
  /** "Bus: … Res: … Cell:… Fax:…", as legacy formatted it. */
  clientPhoneFax: string | null;
  clientEmail: string | null;
  /** A licensee, B joint venture/partnership, C previous licensee, P previous partner, … */
  fileClientType: string | null;
  fileClientTypeDesc: string | null;
  licenseeStartDate: string | null; // ISO date
  licenseeEndDate: string | null; // ISO date
}

export interface RangeClients {
  /** Current licensees first, then by end date, newest first. */
  rows: RangeClient[];
  /** Whether Save is enabled (legacy's p_save_ok). */
  canEdit: boolean;
  /** Legacy's warnings, e.g. an application in the Inbox. */
  warnings: string[];
}

/** One row of "Selected Clients from the Client System": the client (A) and its partners (B). */
export interface RelatedClient {
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  clientAddress: string | null;
  clientPhoneFax: string | null;
  clientEmail: string | null;
  fileClientType: string | null;
  fileClientTypeDesc: string | null;
}

export interface RangeClientAssignRequest {
  clientNumber: string;
  clientLocnCode: string;
  licenseeStartDate: string; // yyyy-mm-dd
  /** Blank: the package uses the start date. */
  previousLicenseeEndDate: string | null; // yyyy-mm-dd
}

export interface RangeClientAssignResult {
  list: RangeClients;
  message: string;
}

const base = (forestFileId: string) =>
  `/api/fta/range-tenures/${encodeURIComponent(forestFileId)}/clients`;

export const getRangeClients = (forestFileId: string) => apiGet<RangeClients>(base(forestFileId));

/** Legacy's Get. */
export const getRelatedClients = (
  forestFileId: string,
  clientNumber: string,
  clientLocnCode: string,
) =>
  apiGet<RelatedClient[]>(
    `${base(forestFileId)}/related${toQuery({ clientNumber, clientLocnCode })}`,
  );

/** Legacy's Save: Assign/Transfer to Client. */
export const assignRangeClient = (forestFileId: string, request: RangeClientAssignRequest) =>
  apiPost<RangeClientAssignResult>(base(forestFileId), request);
