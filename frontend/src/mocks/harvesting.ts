// Mock data for the harvest-authority side of the tenure line (Cutting
// Permits / Timber Marks and Cut Blocks), standing in for the FTA backend
// until the Spring Boot APIs exist. Records reference the forest-file IDs in
// ./tenures.ts so the tenure detail tabs link through to these screens.

export interface HarvestingAuthority {
  cpId: string;
  fileId: string;
  timberMark: string;
  status: 'Issued' | 'Pending' | 'Suspended' | 'Expired';
  orgUnit: string;
  issueDate: string;
  expiryDate: string;
  volumeCubicMetres: number;
  legalDescription: string;
  issuanceConditions: string[];
}

export interface CutBlockRecord {
  blockId: string;
  cpId: string;
  fileId: string;
  timberMark: string;
  status: 'Active' | 'Harvested' | 'Suspended' | 'Amended';
  areaHa: number;
  orgUnit: string;
  netAreaHa: number;
  plannedVolume: number;
  disturbanceStart?: string;
}

/**
 * Numeric surrogate key for a cut block — mirrors the numeric {@code
 * THE.CUT_BLOCK.CB_SKEY} the backend binds via {@code TO_NUMBER}. Derived here
 * from the mock block-id digits so screens send a numeric key, not a label.
 */
export function cbSkeyFor(blockId: string): number {
  return Number(blockId.replace(/\D/g, '')) || 0;
}

/**
 * Numeric surrogate key for a harvesting authority — mirrors the numeric
 * {@code HVA_SKEY} the backend binds via {@code TO_NUMBER}.
 */
export function hvaSkeyFor(cpId: string): number {
  return Number(cpId.replace(/\D/g, '')) || 0;
}

export const MOCK_HARVESTING_AUTHORITIES: HarvestingAuthority[] = [
  {
    cpId: 'CP-01',
    fileId: 'A19201',
    timberMark: '52/1234',
    status: 'Issued',
    orgUnit: 'DCC — Cariboo-Chilcotin',
    issueDate: '2021-04-01',
    expiryDate: '2025-03-31',
    volumeCubicMetres: 42000,
    legalDescription: 'Block 1, District Lot 5312, Cariboo District',
    issuanceConditions: ['Road use permit required', 'Wildlife tree retention 7%'],
  },
  {
    cpId: 'CP-02',
    fileId: 'A19201',
    timberMark: '52/1235',
    status: 'Issued',
    orgUnit: 'DCC — Cariboo-Chilcotin',
    issueDate: '2022-06-15',
    expiryDate: '2026-06-14',
    volumeCubicMetres: 38500,
    legalDescription: 'Block 2, District Lot 5313, Cariboo District',
    issuanceConditions: ['Seasonal harvest — winter only'],
  },
  {
    cpId: 'CP-03',
    fileId: 'A19201',
    timberMark: '52/1236',
    status: 'Pending',
    orgUnit: 'DCC — Cariboo-Chilcotin',
    issueDate: '2024-02-01',
    expiryDate: '2028-01-31',
    volumeCubicMetres: 51000,
    legalDescription: 'Block 3, District Lot 5401, Cariboo District',
    issuanceConditions: [],
  },
  {
    cpId: 'CP-77',
    fileId: 'A20115',
    timberMark: '61/8890',
    status: 'Issued',
    orgUnit: 'DPG — Prince George',
    issueDate: '2020-09-10',
    expiryDate: '2024-09-09',
    volumeCubicMetres: 64000,
    legalDescription: 'Block 12, District Lot 9021, Prince George District',
    issuanceConditions: ['Riparian reserve zone 30m'],
  },
  {
    cpId: 'CP-90',
    fileId: 'A62009',
    timberMark: '48/2201',
    status: 'Suspended',
    orgUnit: 'DMK — Mackenzie',
    issueDate: '2019-05-01',
    expiryDate: '2027-04-30',
    volumeCubicMetres: 29500,
    legalDescription: 'Block 4, District Lot 2200, Mackenzie District',
    issuanceConditions: ['Suspended pending cutblock re-survey'],
  },
];

export const MOCK_CUT_BLOCKS: CutBlockRecord[] = [
  { blockId: 'BLK-001', cpId: 'CP-01', fileId: 'A19201', timberMark: '52/1234', status: 'Harvested', areaHa: 24.6, netAreaHa: 22.1, plannedVolume: 9800, orgUnit: 'DCC — Cariboo-Chilcotin', disturbanceStart: '2021-07-02' },
  { blockId: 'BLK-002', cpId: 'CP-01', fileId: 'A19201', timberMark: '52/1234', status: 'Active', areaHa: 31.2, netAreaHa: 28.4, plannedVolume: 12600, orgUnit: 'DCC — Cariboo-Chilcotin', disturbanceStart: '2023-01-15' },
  { blockId: 'BLK-003', cpId: 'CP-02', fileId: 'A19201', timberMark: '52/1235', status: 'Active', areaHa: 18.9, netAreaHa: 17.0, plannedVolume: 7200, orgUnit: 'DCC — Cariboo-Chilcotin' },
  { blockId: 'BLK-114', cpId: 'CP-77', fileId: 'A20115', timberMark: '61/8890', status: 'Amended', areaHa: 40.1, netAreaHa: 36.8, plannedVolume: 15400, orgUnit: 'DPG — Prince George', disturbanceStart: '2021-02-20' },
  { blockId: 'BLK-220', cpId: 'CP-90', fileId: 'A62009', timberMark: '48/2201', status: 'Suspended', areaHa: 12.3, netAreaHa: 11.1, plannedVolume: 4100, orgUnit: 'DMK — Mackenzie' },
];

export interface HarvestingSearchCriteria {
  cpId?: string;
  fileId?: string;
  timberMark?: string;
  status?: string;
}

export function searchHarvestingAuthorities(c: HarvestingSearchCriteria): HarvestingAuthority[] {
  return MOCK_HARVESTING_AUTHORITIES.filter((h) => {
    if (c.cpId && !h.cpId.toLowerCase().includes(c.cpId.toLowerCase())) return false;
    if (c.fileId && !h.fileId.toLowerCase().includes(c.fileId.toLowerCase())) return false;
    if (c.timberMark && !h.timberMark.toLowerCase().includes(c.timberMark.toLowerCase())) return false;
    if (c.status && h.status !== c.status) return false;
    return true;
  });
}

export function findHarvestingAuthority(cpId: string): HarvestingAuthority | undefined {
  return MOCK_HARVESTING_AUTHORITIES.find((h) => h.cpId.toLowerCase() === cpId.toLowerCase());
}

export function cutBlocksForCp(cpId: string): CutBlockRecord[] {
  return MOCK_CUT_BLOCKS.filter((b) => b.cpId.toLowerCase() === cpId.toLowerCase());
}

export interface CutBlockSearchCriteria {
  blockId?: string;
  fileId?: string;
  cpId?: string;
  status?: string;
}

export function searchCutBlocks(c: CutBlockSearchCriteria): CutBlockRecord[] {
  return MOCK_CUT_BLOCKS.filter((b) => {
    if (c.blockId && !b.blockId.toLowerCase().includes(c.blockId.toLowerCase())) return false;
    if (c.fileId && !b.fileId.toLowerCase().includes(c.fileId.toLowerCase())) return false;
    if (c.cpId && !b.cpId.toLowerCase().includes(c.cpId.toLowerCase())) return false;
    if (c.status && b.status !== c.status) return false;
    return true;
  });
}

export function findCutBlock(blockId: string): CutBlockRecord | undefined {
  return MOCK_CUT_BLOCKS.find((b) => b.blockId.toLowerCase() === blockId.toLowerCase());
}
