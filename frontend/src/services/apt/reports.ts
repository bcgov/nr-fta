import { downloadCsv } from '../csvExport';
import { apiGetForBlob, toQuery } from '../http';

/**
 * APT reports (APT2's APTM8000) — GET /api/fta/apt/reports/...: nine PDF reports rendered in
 * the backend from JRXML templates, and two CSV data extracts. APT2 dropped APTR018, APTR055, APTR060,
 * APTR065 and the TSA exception extract.
 */

/** An input a PDF report's form asks for. */
export type AptReportInput = 'date' | 'managementUnit' | 'orgUnit';

export interface AptPdfReport {
  code: string;
  name: string;
  /** What the form asks for; a report with none runs straight away. */
  inputs: AptReportInput[];
  /** Offer only the TSAs (units whose name includes "TSA") as its management unit. */
  tsaUnitsOnly?: boolean;
}

/** APT2's report list, names and inputs as it has them. */
export const APT_PDF_REPORTS: AptPdfReport[] = [
  {
    code: 'APTR011',
    name: 'TSA AAC, Apportionment and Commitments',
    inputs: ['managementUnit', 'date'],
    tsaUnitsOnly: true,
  },
  {
    code: 'APTR012',
    name: 'Region AAC, Apport and Commit - by Partition',
    inputs: ['orgUnit', 'date'],
  },
  {
    code: 'APTR013',
    name: 'Region AAC, Apportionment and Commitments - by TSA',
    inputs: ['orgUnit', 'date'],
  },
  { code: 'APTR017', name: 'AAC Summary By TSA', inputs: [] },
  { code: 'APTR021', name: 'Tree Farm Licence Summary Report', inputs: ['date'] },
  { code: 'APTR032', name: 'Provincial Summary Report', inputs: ['date'] },
  { code: 'APTR033', name: 'Provincial Summary Report by Region', inputs: ['date'] },
  { code: 'APTR041', name: 'Linkages and Licences', inputs: [] },
  { code: 'APTR043', name: 'Licencees Annual Commitment', inputs: [] },
];

export interface AptDataExtract {
  code: string;
  label: string;
}

/** The CSV data extracts, as APT2 labelled them. */
export const APT_DATA_EXTRACTS: AptDataExtract[] = [
  { code: 'APTR041', label: 'Linkages and Licences Data' },
  { code: 'APTR013', label: 'TSA Apportionment Data' },
];

/** A PDF report's inputs; send only those the report takes. */
export interface AptReportParams {
  /** yyyy-mm-dd */
  reportDate?: string;
  managementUnitId?: number;
  /** A region's org unit number. */
  orgUnitNo?: number;
}

const BASE = '/api/fta/apt/reports';

/**
 * GET /api/fta/apt/reports/{code} — the report as a PDF, rendered in the backend. 501 until the
 * report's template has been added; the error carries the backend's message.
 */
export function getAptReportPdf(code: string, params: AptReportParams = {}): Promise<Blob> {
  return apiGetForBlob(`${BASE}/${encodeURIComponent(code)}${toQuery({ ...params })}`);
}

/**
 * GET /api/fta/apt/reports/{code}/csv — saves the extract under the server's file name (the
 * report code and the day).
 */
export function downloadAptDataExtract(code: string): Promise<void> {
  return downloadCsv(`${BASE}/${encodeURIComponent(code)}/csv`);
}

/**
 * A generated PDF's file name, as APT2 names it: the report code and the chosen management unit
 * or region (`APTR011_Arrow_TSA.pdf`), or, without one, the code and the day
 * (`APTR017_20261104.pdf`). Spaces become underscores.
 */
export function aptReportFileName(code: string, chosen?: string | null, today = new Date()) {
  const name = chosen?.trim()
    ? `${code} ${chosen.trim()}`
    : `${code}_${today.getFullYear()}${String(today.getMonth() + 1).padStart(2, '0')}${String(
        today.getDate(),
      ).padStart(2, '0')}`;
  return `${name.replace(/\s+/g, '_')}.pdf`;
}
