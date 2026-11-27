import { downloadBlob } from '@/lib/downloadBlob';

import { apiGetForBlob, toQuery } from './http';

/** FTA008's criteria. Every one is optional; '' is left out of the request. */
export interface ApplicationMetricsCriteria {
  /** A District's ORG_UNIT_NO, or '' for every district. */
  orgUnitNo: string;
  /** yyyy-mm-dd, each end of each range optional. */
  submissionDateFrom: string;
  submissionDateTo: string;
  emailRecdDateFrom: string;
  emailRecdDateTo: string;
  acceptanceDateFrom: string;
  acceptanceDateTo: string;
  decisionDateFrom: string;
  decisionDateTo: string;
  issuanceDateFrom: string;
  issuanceDateTo: string;
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Legacy's file name, `yyyy-MM-dd_HHmmss_MetricsOutput.xlsx`, stamped in local time. */
export function metricsFilename(now: Date = new Date()): string {
  const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const time = `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  return `${date}_${time}_MetricsOutput.xlsx`;
}

/**
 * GET /api/fta/application-metrics/export — the FTA008 Application Metrics Export workbook
 * (a Metrics sheet and a Holds sheet), saved under legacy's file name.
 */
export async function exportApplicationMetrics(c: ApplicationMetricsCriteria): Promise<void> {
  const params = Object.fromEntries(
    Object.entries(c).map(([k, v]) => [k, v === '' ? undefined : v]),
  );
  const blob = await apiGetForBlob(`/api/fta/application-metrics/export${toQuery(params)}`);
  downloadBlob(blob, metricsFilename());
}
