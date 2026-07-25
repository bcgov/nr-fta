// Mock data for the Private Timber Marks line (FTA500/510/511/512/513),
// standing in for the FTA backend until the Spring Boot APIs exist.

export interface MarkSummary {
  markNumber: string;
  markType: string;
  status: 'Active' | 'Pending' | 'Amended' | 'Cancelled';
  holderName: string;
  holderClient: string;
  orgUnit: string;
  issueDate: string;
}

export interface LandIndexEntry {
  parcelId: string;
  legalDescription: string;
  areaHa: number;
  pin: string;
}

export interface MarkClient {
  clientNumber: string;
  name: string;
  role: string;
}

export interface MarkAmendment {
  date: string;
  type: string;
  description: string;
}

export interface MarkDetailRecord extends MarkSummary {
  timberOrigin: string;
  landIndex: LandIndexEntry[];
  clients: MarkClient[];
  amendments: MarkAmendment[];
}

export const MOCK_MARKS: MarkSummary[] = [
  { markNumber: '12 3456', markType: 'Private Timber Mark', status: 'Active', holderName: 'Aspen Holdings Ltd.', holderClient: '00120045', orgUnit: 'DSQ — Sea to Sky', issueDate: '2019-05-14' },
  { markNumber: '12 7788', markType: 'Private Timber Mark', status: 'Active', holderName: 'Willowfield Ranch Ltd.', holderClient: '00120890', orgUnit: 'DCC — Cariboo-Chilcotin', issueDate: '2021-02-03' },
  { markNumber: '13 0091', markType: 'Private Timber Mark', status: 'Pending', holderName: 'R. Fielding', holderClient: '00133011', orgUnit: 'DND — Nadina', issueDate: '2024-03-20' },
  { markNumber: '11 5540', markType: 'Private Timber Mark', status: 'Amended', holderName: 'Harbourview Woodlands Inc.', holderClient: '00098772', orgUnit: 'DVA — Campbell River', issueDate: '2016-09-01' },
  { markNumber: '10 2200', markType: 'Private Timber Mark', status: 'Cancelled', holderName: 'Heritage Estates Ltd.', holderClient: '00077120', orgUnit: 'DMK — Mackenzie', issueDate: '2011-07-19' },
];

const DETAIL_EXTRAS: Pick<MarkDetailRecord, 'timberOrigin' | 'landIndex' | 'clients' | 'amendments'> = {
  timberOrigin: 'Private Land — fee simple',
  landIndex: [
    { parcelId: 'PID-013-482-119', legalDescription: 'Lot 4, Plan 12345, Land District 36', areaHa: 42.1, pin: '013482119' },
    { parcelId: 'PID-013-482-127', legalDescription: 'Lot 5, Plan 12345, Land District 36', areaHa: 18.7, pin: '013482127' },
  ],
  clients: [
    { clientNumber: '00120045', name: 'Aspen Holdings Ltd.', role: 'Mark Holder' },
    { clientNumber: '00088204', name: 'Ridgeline Logging Co.', role: 'Agent' },
  ],
  amendments: [
    { date: '2022-06-10', type: 'Land Index', description: 'Added PID-013-482-127 to the mark.' },
    { date: '2020-01-15', type: 'Holder', description: 'Agent designation updated.' },
  ],
};

export interface MarkSearchCriteria {
  markNumber?: string;
  holderName?: string;
  orgUnit?: string;
  status?: string;
}

export function searchMarks(c: MarkSearchCriteria): MarkSummary[] {
  return MOCK_MARKS.filter((m) => {
    if (c.markNumber && !m.markNumber.toLowerCase().includes(c.markNumber.toLowerCase())) return false;
    if (c.holderName && !m.holderName.toLowerCase().includes(c.holderName.toLowerCase())) return false;
    if (c.orgUnit && !m.orgUnit.toLowerCase().includes(c.orgUnit.toLowerCase())) return false;
    if (c.status && m.status !== c.status) return false;
    return true;
  });
}

export function findMark(markNumber: string): MarkDetailRecord | undefined {
  const norm = (s: string) => s.replace(/\s+/g, '').toLowerCase();
  const summary = MOCK_MARKS.find((m) => norm(m.markNumber) === norm(markNumber));
  if (!summary) return undefined;
  return { ...summary, ...DETAIL_EXTRAS };
}

export const MARK_STATUS_TAG: Record<MarkSummary['status'], 'green' | 'blue' | 'purple' | 'gray'> = {
  Active: 'green',
  Pending: 'blue',
  Amended: 'purple',
  Cancelled: 'gray',
};
