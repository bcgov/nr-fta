import { apiGet, apiPost, apiPut } from './http';

import type { CodeOption } from './codeLists';

// The FTA613R Range Usage editors (backend RangeUsageWriteController). The amounts go as text,
// as legacy's form took them, so the backend answers a bad value with legacy's own message.

/** The term's years, the current reason codes, and whether an application is in the Inbox. */
export interface RangeUsageOptions {
  years: number[];
  increaseReasons: CodeOption[];
  nonUseReasons: CodeOption[];
  applicationPending: boolean;
}

/** One year's usage as entered (legacy Save). */
export interface RangeUsageSave {
  authorizedUse: string;
  tempIncrease: string;
  rangeIncreaseReasonCode: string;
  nonUseAgreementNo: string;
  nonUseBillable: string;
  nonUseNonbillable: string;
  rangeNonUseReasonCode: string;
  /** The row's revision as read; null for a year with no usage yet. */
  revisionCount: number | null;
}

/** Legacy Copy: every remaining year of the term, or From to To. */
export interface RangeUsageCopy {
  allRemainingYears: boolean;
  copyFromYear: string;
  copyToYear: string;
}

const base = (forestFileId: string) =>
  `/api/fta/range-tenures/${encodeURIComponent(forestFileId)}/usage`;

/** GET .../usage/options. */
export function getRangeUsageOptions(forestFileId: string): Promise<RangeUsageOptions> {
  return apiGet<RangeUsageOptions>(`${base(forestFileId)}/options`);
}

/** PUT .../usage/{year} — add the year or update it. */
export function saveRangeUsage(
  forestFileId: string,
  calendarYear: number,
  body: RangeUsageSave,
): Promise<{ message: string }> {
  return apiPut(`${base(forestFileId)}/${calendarYear}`, body);
}

/** POST .../usage/{year}/copy — copy the year's usage to other years. */
export function copyRangeUsage(
  forestFileId: string,
  calendarYear: number,
  body: RangeUsageCopy,
): Promise<{ message: string }> {
  return apiPost(`${base(forestFileId)}/${calendarYear}/copy`, body);
}
