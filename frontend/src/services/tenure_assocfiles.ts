import { apiDelete, apiGet, apiPost, toQuery } from './http';

import type { CodeOption } from './codeLists';

/** One association on the tenure's Associated files tab (legacy FTA910). */
export interface TenureAssociatedFile {
  /** The other file, or another system's id for a non-F source. */
  associatedFileId: string;
  fileSourceCode: string;
  /** "CODE - Description". */
  fileSourceDesc: string | null;
  fileAssociationTypeCode: string | null;
  /** "CODE - Description". */
  fileAssociationTypeDesc: string | null;
  associationEndDate: string | null; // ISO date
  revisionCount: number | null;
  /** Whether the associated file is an FTA file (source F) — linkable. */
  tenure: boolean;
}

export interface TenureAssociatedFiles {
  rows: TenureAssociatedFile[];
  canAdd: boolean;
  /** Why not, when `canAdd` is false. */
  addReason: string | null;
}

export interface AssociatedFileAddRequest {
  associatedFileId: string;
  fileSourceCode: string;
  fileAssociationTypeCode: string | null;
  associationEndDate: string | null; // ISO date
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/associated-files`;

export const getTenureAssociatedFiles = (forestFileId: string) =>
  apiGet<TenureAssociatedFiles>(base(forestFileId));

/** Adds an association; `warning` is legacy's AAC file-type warning (the add still saved). */
export const addTenureAssociatedFile = (forestFileId: string, request: AssociatedFileAddRequest) =>
  apiPost<{ list: TenureAssociatedFiles; warning: string | null }>(base(forestFileId), request);

/** Deletes an association (source F: in both directions). */
export const deleteTenureAssociatedFile = (forestFileId: string, row: TenureAssociatedFile) =>
  apiDelete<TenureAssociatedFiles>(
    `${base(forestFileId)}/${encodeURIComponent(row.associatedFileId)}${toQuery({
      fileSourceCode: row.fileSourceCode,
      revisionCount: row.revisionCount,
    })}`,
  );

/** The current file association types (the sources are `getFileSources`). */
export const getFileAssociationTypes = () =>
  apiGet<CodeOption[]>('/api/fta/tenure-lookups/file-association-types');
