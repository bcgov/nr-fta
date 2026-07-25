import { apiGet, toQuery } from './http';

// Mirrors the backend InboxDto (ca.bc.gov.nrs.fta.tenure.dto), which mirrors the
// legacy THE.FTA_300N_INBOX rec_inbox record — one pending ESF tenure-application
// row in the FTA300 inbox worklist.
export interface InboxRow {
  tenureAppId: number | null;
  submissionId: number | null;
  maxX: number | null;
  minX: number | null;
  maxY: number | null;
  minY: number | null;
  clientNumber: string | null;
  revisionCount: number | null;
  taiRevisionCount: number | null;
  forestFileId: string | null;
  forestFileIdDisplay: string | null;
  currentAssignedTo: string | null;
  tenureApplicationType: string | null;
  orgUnitName: string | null;
  orgUnitNo: number | null;
  fileBctsOrg: number | null;
  bctsOrgCode: string | null;
  licensee: string | null;
  submissionDate: string | null; // ISO date
  adjudicationInd: string | null;
  jobMemo: string | null;
  adjReportInd: string | null;
  bctsFileInd: string | null;
  applicationTypeCode: string | null;
  tenureAppPurpCode: string | null;
  exhAImageInd: string | null;
  fileActionLink: string | null;
  approveEnabledInd: string | null;
  rejectEnabledInd: string | null;
  esfHyperlinkInd: string | null;
  exhAActionInd: string | null;
  bctsEsfHyperlinkInd: string | null;
  bctsExhAActionInd: string | null;
  fileBubbleHelp: string | null;
  regenInProgressInd: string | null;
  imageMimeTypeCode: string | null;
  hvaSkey: number | null;
  hvaId: string | null;
}

// Mirrors the THE.FTA_300N_INBOX inbox (GET) filter inputs; every field optional.
export interface InboxSearchParams {
  forestFileId?: string;
  orgUnit?: string;
  applTypeCode?: string;
  fileTypeCode?: string;
  clientNumber?: string;
  clientLocnCode?: string;
  harvestTypeCode?: string;
  dateFrom?: string;
  dateTo?: string;
  exACleared?: string;
  sortBy?: string;
}

/** GET /api/fta/inbox — FTA300 inbox worklist (FTA_300N_INBOX). */
export function searchInbox(params: InboxSearchParams): Promise<InboxRow[]> {
  return apiGet<InboxRow[]>(`/api/fta/inbox${toQuery({ ...params })}`);
}
