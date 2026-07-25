// Mock billing / rents & fees data for the FTA admin & billing screens
// (FTA670/680/685/686/690/695), pending the real backend/batch engine.

export interface BillingLine {
  fileId: string;
  client: string;
  clientNumber: string;
  rentDue: number;
  feeDue: number;
  total: number;
  status: 'Draft' | 'Submitted' | 'Approved' | 'Invoiced';
  orgUnit: string;
  /** Numeric org-unit key (THE.ORG_UNIT.ORG_UNIT_NO) sent to the billing API. */
  orgUnitNo: string;
}

export interface AuditEntry {
  timestamp: string;
  user: string;
  action: string;
  entity: string;
  detail: string;
}

export const MOCK_BILLING: BillingLine[] = [
  { fileId: 'A19201', client: 'Northwood Timber Ltd.', clientNumber: '00001012', rentDue: 18420.0, feeDue: 500.0, total: 18920.0, status: 'Draft', orgUnit: 'DCC — Cariboo-Chilcotin', orgUnitNo: '1010' },
  { fileId: 'A20115', client: 'Cascade Forest Products Ltd.', clientNumber: '00010003', rentDue: 24110.5, feeDue: 500.0, total: 24610.5, status: 'Submitted', orgUnit: 'DPG — Prince George', orgUnitNo: '1020' },
  { fileId: 'RAN076543', client: 'Willowfield Ranch Ltd.', clientNumber: '00120890', rentDue: 3968.0, feeDue: 0.0, total: 3968.0, status: 'Draft', orgUnit: 'DCC — Cariboo-Chilcotin', orgUnitNo: '1010' },
  { fileId: 'A62009', client: 'Northpoint Fibre Ltd.', clientNumber: '00055120', rentDue: 15230.0, feeDue: 500.0, total: 15730.0, status: 'Approved', orgUnit: 'DMK — Mackenzie', orgUnitNo: '1050' },
  { fileId: 'A15002', client: 'Riverbend Community Forest', clientNumber: '00033201', rentDue: 6410.0, feeDue: 250.0, total: 6660.0, status: 'Invoiced', orgUnit: 'DSQ — Sea to Sky', orgUnitNo: '1040' },
];

export const MOCK_AUDIT: AuditEntry[] = [
  { timestamp: '2026-07-21 14:22', user: 'mclarke', action: 'UPDATE', entity: 'Tenure A19201', detail: 'AAC apportionment changed' },
  { timestamp: '2026-07-21 11:05', user: 'lferris', action: 'CREATE', entity: 'Cutting Permit CP-03', detail: 'New permit added to A19201' },
  { timestamp: '2026-07-20 16:47', user: 'dokafor', action: 'APPROVE', entity: 'Application ESF-100199', detail: 'Cutting permit issued' },
  { timestamp: '2026-07-20 09:14', user: 'mclarke', action: 'DELETE', entity: 'Cut Block BLK-999', detail: 'Draft block removed' },
];

export function billingTotal(lines: BillingLine[]): number {
  return lines.reduce((s, l) => s + l.total, 0);
}
