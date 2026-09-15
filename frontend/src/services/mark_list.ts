import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend MarkListDto (ca.bc.gov.nrs.fta.mark.dto), which mirrors
// the legacy THE.FTA_500_MARK_LIST rec_mark_list record.
export interface MarkListRow {
  processType: string | null;
  certificate: string | null;
  timberMark: string | null;
  markApplDate: string | null; // ISO date
  orgUnitCode: string | null;
  markStatusSt: string | null;
  clientName: string | null;
  disablePrintInd: string | null;
  disableAckInd: string | null;
  tmRevisionCount: number | null;
  amendRevisionCount: number | null;
  idir: string | null;
}

export interface MarkListParams {
  /** Administrative district org-unit number — the only criterion legacy offers. */
  hdrDistrict?: string;
  timberMark?: string;
  /** A PRIVATE_MARK_STATUS_CODE value (HN, PA, PI, DV, HI, HX). */
  markStatusSt?: string;
  orgUnitCode?: string;
  clientName?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/marks — private mark application/amendment list (FTA_500_MARK_LIST). */
export function listMarks(params: MarkListParams): Promise<PageableResponse<MarkListRow>> {
  return apiGet<PageableResponse<MarkListRow>>(`/api/fta/marks${toQuery({ ...params })}`);
}
