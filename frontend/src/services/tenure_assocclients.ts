import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

/** One of the tenure's clients (FOREST_FILE_CLIENT) — legacy FTA920. */
export interface TenureAssociatedClient {
  forestFileClientSkey: number;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  /** A main licensee, B licensee, C previous licensee, … */
  fileClientType: string | null;
  /** "CODE - Description". */
  fileClientTypeDesc: string | null;
  licenseeStartDate: string | null; // ISO date
  licenseeEndDate: string | null; // ISO date
  revisionCount: number | null;
  /** A C or S type client, while the file allows it. */
  canDelete: boolean;
}

export interface TenureAssociatedClients {
  rows: TenureAssociatedClient[];
  /** Whether clients may be added or updated. */
  canEdit: boolean;
  editReason: string | null;
  /** Why C/S clients cannot be deleted, when the file forbids it. */
  deleteReason: string | null;
}

export interface AssociatedClientRequest {
  clientNumber: string;
  clientLocnCode: string;
  fileClientType: string;
  licenseeStartDate: string | null; // ISO date
  licenseeEndDate: string | null; // ISO date
  /** Required on update. */
  revisionCount?: number | null;
}

/** A write's result; `note` says when a new Main Licensee demoted the current one. */
export interface AssociatedClientWriteResult {
  list: TenureAssociatedClients;
  note: string | null;
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/associated-clients`;

export const getTenureAssociatedClients = (forestFileId: string) =>
  apiGet<TenureAssociatedClients>(base(forestFileId));

export const addTenureAssociatedClient = (forestFileId: string, request: AssociatedClientRequest) =>
  apiPost<AssociatedClientWriteResult>(base(forestFileId), request);

export const updateTenureAssociatedClient = (
  forestFileId: string,
  skey: number,
  request: AssociatedClientRequest,
) => apiPut<AssociatedClientWriteResult>(`${base(forestFileId)}/${skey}`, request);

export const deleteTenureAssociatedClient = (forestFileId: string, row: TenureAssociatedClient) =>
  apiDelete<TenureAssociatedClients>(
    `${base(forestFileId)}/${row.forestFileClientSkey}${toQuery({
      revisionCount: row.revisionCount,
    })}`,
  );
