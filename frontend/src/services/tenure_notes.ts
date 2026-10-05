import { apiGet, apiPost } from './http';

// Mirrors the backend NotesDtos (ca.bc.gov.nrs.fta.tenure.tab.notes) — the
// tenure's forest notes, legacy FTA970 (FTA_970_FOREST_NOTE).

/** Legacy Fta970ForestNotesForm's limit; the backend enforces it too. */
export const TENURE_NOTE_MAX_LENGTH = 4000;

export interface TenureNote {
  entryUserid: string | null;
  /** ISO date-time. */
  entryTimestamp: string | null;
  note: string | null;
}

export interface TenureNotes {
  /** Whether a note may be added to the file (not when its status is PE). */
  canAdd: boolean;
  /** Legacy's reason, when `canAdd` is false. */
  addBlockedReason: string | null;
  /** Newest first. */
  notes: TenureNote[];
}

const path = (forestFileId: string) => `/api/fta/tenures/${encodeURIComponent(forestFileId)}/notes`;

/** GET /api/fta/tenures/{forestFileId}/notes. */
export function getTenureNotes(forestFileId: string): Promise<TenureNotes> {
  return apiGet<TenureNotes>(path(forestFileId));
}

/** POST /api/fta/tenures/{forestFileId}/notes — returns the refreshed list. */
export function addTenureNote(forestFileId: string, note: string): Promise<TenureNotes> {
  return apiPost<TenureNotes>(path(forestFileId), { note });
}
