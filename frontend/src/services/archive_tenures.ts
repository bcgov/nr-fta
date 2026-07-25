import { apiPost } from './http';

// Mirrors the backend ArchiveTenuresRequest (ca.bc.gov.nrs.fta.tenure.dto).
export interface ArchiveTenuresRequest {
  /** The exact forest files the user selected to archive. */
  forestFileIds: string[];
}

/**
 * POST /api/fta/admin/archive-tenures — archive the selected forest files
 * (FTA640, FTA_ADMIN only). Only still-active files among those listed are
 * archived; returns the number archived.
 */
export function archiveTenures(req: ArchiveTenuresRequest): Promise<{ updated: number }> {
  return apiPost<{ updated: number }>('/api/fta/admin/archive-tenures', req);
}
