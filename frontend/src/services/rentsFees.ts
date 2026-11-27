import { apiGet, apiGetForBlob, apiPost, toQuery } from './http';

/**
 * FTA670 Annual Rents and Fees Preparation — /api/fta/admin/rents-fees (FTA_ADMIN only), on
 * legacy THE.FTA_670_RENTFEE.
 */

/** Go's criteria. Rollup is ignored for a district-scoped administrator. */
export interface RentsFeesCriteria {
  orgUnitNo: string;
  rollup: boolean;
}

/** One row; mirrors the backend RentFeeRowDto. Amounts are the package's formatted text. */
export interface RentFeeRow {
  forestFileId: string;
  calendarYear: string;
  /** The Opt indicator: '', 'R' or 'D'. */
  invoicePrepInd: string;
  fileTypeCode: string;
  orgCode: string;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  anniversaryDate: string;
  unitsBillable: string | null;
  unitType: string;
  unitRate: string | null;
  amount: string | null;
  /** The optimistic lock Prepare hands back. */
  updateTimestamp: string;
}

export interface RentFeeList {
  billFrom: string | null;
  billTo: string | null;
  rows: RentFeeRow[];
}

/** The Opt dropdown's values, as legacy offered them. */
export const RENT_FEE_INDICATORS = ['', 'R', 'D'] as const;

export const getRentsFees = (c: RentsFeesCriteria) =>
  apiGet<RentFeeList>(`/api/fta/admin/rents-fees${toQuery({ ...c })}`);

/** Print: the FTA406 Rents and Fees Preparation Report for Go's criteria, as a PDF. */
export const getRentsFeesReport = (c: RentsFeesCriteria) =>
  apiGetForBlob(`/api/fta/admin/rents-fees/report${toQuery({ ...c })}`);

/** The name the report is saved under (the backend's Content-Disposition). */
export const RENTS_FEES_REPORT_FILENAME = 'FTA406-rents-and-fees.pdf';

/**
 * Prepare: every row of the last Go, in the order shown, with the Opt chosen for each. Rows
 * whose Opt changed are saved (D deletes the row); any refusal saves none.
 */
export const prepareRentsFees = (
  criteria: RentsFeesCriteria,
  rows: { row: RentFeeRow; invoicePrepIndNew: string }[],
) =>
  apiPost<{ saved: number; message: string }>('/api/fta/admin/rents-fees/prepare', {
    orgUnitNo: criteria.orgUnitNo,
    rollup: criteria.rollup,
    rows: rows.map(({ row, invoicePrepIndNew }) => ({
      forestFileId: row.forestFileId,
      calendarYear: row.calendarYear,
      invoicePrepInd: row.invoicePrepInd,
      invoicePrepIndNew,
      updateTimestamp: row.updateTimestamp,
    })),
  });
