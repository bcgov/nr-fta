import { apiGet, toQuery } from './http';

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
  hdrDistrict?: string;
  timberMark?: string;
  markStatusSt?: string;
  orgUnitCode?: string;
  clientName?: string;
}

/** GET /api/fta/marks — private mark application/amendment list (FTA_500_MARK_LIST). */
export function listMarks(params: MarkListParams): Promise<MarkListRow[]> {
  return apiGet<MarkListRow[]>(`/api/fta/marks${toQuery({ ...params })}`);
}
