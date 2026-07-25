// Mock tenure data standing in for the FTA backend (FOREST_FILE / tenure
// spine) until the Spring Boot APIs exist. Shapes mirror the legacy FTA001
// search results and FTA100 tenure detail fields.

export interface TenureSummary {
  fileId: string;
  fileType: string;
  status: 'Active' | 'Pending' | 'Expired' | 'Archived';
  orgUnit: string;
  licensee: string;
  clientNumber: string;
  issueDate: string; // ISO yyyy-mm-dd
  expiryDate: string;
}

export interface CuttingPermit {
  cpId: string;
  timberMark: string;
  status: string;
  issueDate: string;
  volume: number; // m³
}

export interface CutBlock {
  blockId: string;
  cpId: string;
  status: string;
  areaHa: number;
}

export interface AssociatedClient {
  clientNumber: string;
  name: string;
  relationship: string;
  location: string;
}

export interface RoadSection {
  roadId: string;
  name: string;
  status: string;
  lengthKm: number;
  tenureType: string;
}

export interface AssociatedFile {
  fileId: string;
  relationship: string;
  fileType: string;
  status: string;
}

export interface SaleInfo {
  saleType: string;
  upsetRate: number; // $/m³
  bonusBid: number; // $/m³
  totalUpset: number; // $
  cashSale: boolean;
}

export interface TenureDetailRecord extends TenureSummary {
  managementUnit: string;
  aacCubicMetres: number;
  cuttingPermits: CuttingPermit[];
  cutBlocks: CutBlock[];
  associatedClients: AssociatedClient[];
  notes: { date: string; author: string; text: string }[];
  roads: RoadSection[];
  associatedFiles: AssociatedFile[];
  saleInfo: SaleInfo;
}

export const MOCK_TENURES: TenureSummary[] = [
  {
    fileId: 'A19201',
    fileType: 'Forest Licence',
    status: 'Active',
    orgUnit: 'DCC — Cariboo-Chilcotin',
    licensee: 'Northwood Timber Ltd.',
    clientNumber: '00001012',
    issueDate: '2016-03-01',
    expiryDate: '2036-02-28',
  },
  {
    fileId: 'A20115',
    fileType: 'Forest Licence',
    status: 'Active',
    orgUnit: 'DPG — Prince George',
    licensee: 'Cascade Forest Products Ltd.',
    clientNumber: '00010003',
    issueDate: '2018-07-15',
    expiryDate: '2038-07-14',
  },
  {
    fileId: 'A88355',
    fileType: 'Woodlot Licence',
    status: 'Pending',
    orgUnit: 'DND — Nadina',
    licensee: 'Silverpine Forest Products Ltd.',
    clientNumber: '00021144',
    issueDate: '2024-01-10',
    expiryDate: '2044-01-09',
  },
  {
    fileId: 'A15002',
    fileType: 'Community Forest Agreement',
    status: 'Active',
    orgUnit: 'DSQ — Sea to Sky',
    licensee: 'Riverbend Community Forest',
    clientNumber: '00033201',
    issueDate: '2009-11-01',
    expiryDate: '2034-10-31',
  },
  {
    fileId: 'A77410',
    fileType: 'Forest Licence',
    status: 'Expired',
    orgUnit: 'DVA — Campbell River',
    licensee: 'Westshore Timber Corp.',
    clientNumber: '00044876',
    issueDate: '1998-05-20',
    expiryDate: '2018-05-19',
  },
  {
    fileId: 'A62009',
    fileType: 'Tree Farm Licence',
    status: 'Active',
    orgUnit: 'DMK — Mackenzie',
    licensee: 'Northpoint Fibre Ltd.',
    clientNumber: '00055120',
    issueDate: '2012-09-01',
    expiryDate: '2037-08-31',
  },
];

const DETAIL_EXTRAS = {
  managementUnit: 'TSA 27 — Williams Lake',
  aacCubicMetres: 1_450_000,
  cuttingPermits: [
    { cpId: 'CP-01', timberMark: '52/1234', status: 'Issued', issueDate: '2021-04-01', volume: 42000 },
    { cpId: 'CP-02', timberMark: '52/1235', status: 'Issued', issueDate: '2022-06-15', volume: 38500 },
    { cpId: 'CP-03', timberMark: '52/1236', status: 'Pending', issueDate: '2024-02-01', volume: 51000 },
  ],
  cutBlocks: [
    { blockId: 'BLK-001', cpId: 'CP-01', status: 'Harvested', areaHa: 24.6 },
    { blockId: 'BLK-002', cpId: 'CP-01', status: 'Active', areaHa: 31.2 },
    { blockId: 'BLK-003', cpId: 'CP-02', status: 'Active', areaHa: 18.9 },
  ],
  associatedClients: [
    { clientNumber: '00001012', name: 'Northwood Timber Ltd.', relationship: 'Licensee', location: '00' },
    { clientNumber: '00088204', name: 'Ridgeline Logging Co.', relationship: 'Contractor', location: '01' },
  ],
  notes: [
    { date: '2023-08-14', author: 'M. Clarke', text: 'Annual rent invoice issued.' },
    { date: '2022-11-02', author: 'L. Ferris', text: 'AAC apportionment reviewed — no change.' },
  ],
  roads: [
    { roadId: 'RD-4001', name: 'Beaver Creek FSR', status: 'Active', lengthKm: 12.4, tenureType: 'Road Permit' },
    { roadId: 'RD-4002', name: 'Ridge Mainline', status: 'Active', lengthKm: 8.1, tenureType: 'Road Permit' },
    { roadId: 'RD-4003', name: 'Spur 12', status: 'Retired', lengthKm: 2.7, tenureType: 'Section 115' },
  ],
  associatedFiles: [
    { fileId: 'A20115', relationship: 'Adjacent', fileType: 'Forest Licence', status: 'Active' },
    { fileId: 'A62009', relationship: 'Overlapping', fileType: 'Tree Farm Licence', status: 'Active' },
  ],
  saleInfo: {
    saleType: 'Timber Sale Licence',
    upsetRate: 8.45,
    bonusBid: 3.12,
    totalUpset: 122_500,
    cashSale: false,
  },
};

export const MOCK_ROADS: RoadSection[] = DETAIL_EXTRAS.roads;

export function findRoad(roadId: string): (RoadSection & { fileId: string }) | undefined {
  const road = MOCK_ROADS.find((r) => r.roadId.toLowerCase() === roadId.toLowerCase());
  return road ? { ...road, fileId: 'A19201' } : undefined;
}

export function findTenure(fileId: string): TenureDetailRecord | undefined {
  const summary = MOCK_TENURES.find((t) => t.fileId.toLowerCase() === fileId.toLowerCase());
  if (!summary) return undefined;
  return { ...summary, ...DETAIL_EXTRAS };
}

export interface TenureSearchCriteria {
  fileId?: string;
  licensee?: string;
  orgUnit?: string;
  status?: string;
}

export function searchTenures(criteria: TenureSearchCriteria): TenureSummary[] {
  return MOCK_TENURES.filter((t) => {
    if (criteria.fileId && !t.fileId.toLowerCase().includes(criteria.fileId.toLowerCase())) return false;
    if (criteria.licensee && !t.licensee.toLowerCase().includes(criteria.licensee.toLowerCase())) return false;
    if (criteria.orgUnit && !t.orgUnit.toLowerCase().includes(criteria.orgUnit.toLowerCase())) return false;
    if (criteria.status && t.status !== criteria.status) return false;
    return true;
  });
}
