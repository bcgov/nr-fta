import { downloadBlob } from '@/lib/downloadBlob';

import { apiGetForBlob, toQuery } from './http';

import type { RangeTenureSearchParams } from './range_tenure_search';

/** Legacy's file name for the FTA001 Range Tenure Export; the server sends the same. */
const FILENAME = 'FTA001_RangeTenureExport.xlsx';

/**
 * GET /api/fta/range-tenures/export — the FTA001 Range Tenure Export workbook
 * for a range tenure search's criteria (legacy FTA001R's Export button).
 *
 * Paging and the column sort are dropped: the workbook is legacy's report,
 * which has its own sheets and order.
 */
export async function exportRangeTenureSearch(params: RangeTenureSearchParams): Promise<void> {
  const blob = await apiGetForBlob(
    `/api/fta/range-tenures/export${toQuery({ ...params, page: undefined, size: undefined, sort: undefined })}`,
  );
  downloadBlob(blob, FILENAME);
}

/**
 * GET /api/fta/range-tenures/{id}/export — the same workbook for one file
 * (legacy's Export button on the 100R, 613R, 615R and 920R tabs).
 */
export async function exportRangeTenureFile(forestFileId: string): Promise<void> {
  const blob = await apiGetForBlob(
    `/api/fta/range-tenures/${encodeURIComponent(forestFileId)}/export`,
  );
  downloadBlob(blob, FILENAME);
}
