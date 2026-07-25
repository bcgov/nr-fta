// Mock data for the ESF (Electronic Submission Framework) tenure-application
// inbox and adjudication workflow — the FTA300 Inbox worklist and FTA952
// application detail — pending the real backend.

export type ApplicationStatus =
  | 'Submitted'
  | 'In Review'
  | 'On Hold'
  | 'Cleared'
  | 'Rejected'
  | 'Issued';

export interface TenureApplication {
  esfId: string;
  fileId: string;
  applicationType: string;
  harvestType: string;
  client: string;
  clientNumber: string;
  orgUnit: string;
  status: ApplicationStatus;
  submittedDate: string;
  assignedTo: string | null;
  spatialSubmission: boolean;
}

export interface ApplicationEvent {
  date: string;
  actor: string;
  action: string;
  note?: string;
}

export interface TenureApplicationDetail extends TenureApplication {
  processingDays: number;
  holdDays: number;
  professionalDeclaration: boolean;
  events: ApplicationEvent[];
  exhibitAStatus: 'Not submitted' | 'Uploaded' | 'Cleared' | 'Conflicts';
}

export const MOCK_APPLICATIONS: TenureApplication[] = [
  {
    esfId: 'ESF-100234',
    fileId: 'A19201',
    applicationType: 'Cutting Permit',
    harvestType: 'Clearcut',
    client: 'Northwood Timber Ltd.',
    clientNumber: '00001012',
    orgUnit: 'DCC — Cariboo-Chilcotin',
    status: 'Submitted',
    submittedDate: '2026-06-28',
    assignedTo: null,
    spatialSubmission: true,
  },
  {
    esfId: 'ESF-100240',
    fileId: 'A20115',
    applicationType: 'Road Permit',
    harvestType: 'N/A',
    client: 'Cascade Forest Products Ltd.',
    clientNumber: '00010003',
    orgUnit: 'DPG — Prince George',
    status: 'In Review',
    submittedDate: '2026-06-20',
    assignedTo: 'M. Clarke',
    spatialSubmission: true,
  },
  {
    esfId: 'ESF-100251',
    fileId: 'A88355',
    applicationType: 'Amendment',
    harvestType: 'Partial Cut',
    client: 'Silverpine Forest Products Ltd.',
    clientNumber: '00021144',
    orgUnit: 'DND — Nadina',
    status: 'On Hold',
    submittedDate: '2026-05-30',
    assignedTo: 'L. Ferris',
    spatialSubmission: false,
  },
  {
    esfId: 'ESF-100260',
    fileId: 'A62009',
    applicationType: 'Cutting Permit',
    harvestType: 'Clearcut',
    client: 'Northpoint Fibre Ltd.',
    clientNumber: '00055120',
    orgUnit: 'DMK — Mackenzie',
    status: 'Cleared',
    submittedDate: '2026-05-14',
    assignedTo: 'M. Clarke',
    spatialSubmission: true,
  },
  {
    esfId: 'ESF-100199',
    fileId: 'A15002',
    applicationType: 'Cutting Permit',
    harvestType: 'Selection',
    client: 'Riverbend Community Forest',
    clientNumber: '00033201',
    orgUnit: 'DSQ — Sea to Sky',
    status: 'Issued',
    submittedDate: '2026-04-02',
    assignedTo: 'D. Okafor',
    spatialSubmission: true,
  },
];

const DETAIL_EXTRAS: Pick<
  TenureApplicationDetail,
  'processingDays' | 'holdDays' | 'professionalDeclaration' | 'events' | 'exhibitAStatus'
> = {
  processingDays: 21,
  holdDays: 4,
  professionalDeclaration: true,
  exhibitAStatus: 'Cleared',
  events: [
    { date: '2026-06-28', actor: 'ESF', action: 'Application received' },
    { date: '2026-06-29', actor: 'M. Clarke', action: 'Assigned to reviewer' },
    { date: '2026-07-05', actor: 'M. Clarke', action: 'Requested clarification', note: 'Boundary overlaps WHA.' },
    { date: '2026-07-12', actor: 'Northwood Timber Ltd.', action: 'Clarification provided' },
  ],
};

export interface InboxCriteria {
  esfId?: string;
  fileId?: string;
  orgUnit?: string;
  applicationType?: string;
  status?: string;
}

export function searchInbox(c: InboxCriteria): TenureApplication[] {
  return MOCK_APPLICATIONS.filter((a) => {
    if (c.esfId && !a.esfId.toLowerCase().includes(c.esfId.toLowerCase())) return false;
    if (c.fileId && !a.fileId.toLowerCase().includes(c.fileId.toLowerCase())) return false;
    if (c.orgUnit && !a.orgUnit.toLowerCase().includes(c.orgUnit.toLowerCase())) return false;
    if (c.applicationType && a.applicationType !== c.applicationType) return false;
    if (c.status && a.status !== c.status) return false;
    return true;
  });
}

export function findApplication(esfId: string): TenureApplicationDetail | undefined {
  const summary = MOCK_APPLICATIONS.find((a) => a.esfId.toLowerCase() === esfId.toLowerCase());
  if (!summary) return undefined;
  return { ...summary, ...DETAIL_EXTRAS };
}

export const APPLICATION_STATUS_TAG: Record<
  ApplicationStatus,
  'blue' | 'purple' | 'red' | 'green' | 'gray' | 'teal'
> = {
  Submitted: 'blue',
  'In Review': 'purple',
  'On Hold': 'red',
  Cleared: 'teal',
  Rejected: 'gray',
  Issued: 'green',
};
