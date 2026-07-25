// Mock reference / code-table data (clients, management units, rates & fees,
// range zones, org units) for the FTA search and admin screens.

export interface Client {
  clientNumber: string;
  name: string;
  type: 'Corporation' | 'Individual' | 'Association' | 'First Nation' | 'Government';
  status: 'Active' | 'Deactivated';
  location: string;
}

export interface ManagementUnit {
  muId: string;
  name: string;
  muType: 'TSA' | 'TFL' | 'Woodlot';
  region: string;
  aacCubicMetres: number;
}

export interface RateFee {
  code: string;
  description: string;
  unit: string;
  rate: number;
  effectiveDate: string;
}

export interface RangeZone {
  zoneId: string;
  name: string;
  district: string;
  units: number;
}

export const MOCK_CLIENTS: Client[] = [
  { clientNumber: '00001012', name: 'Northwood Timber Ltd.', type: 'Corporation', status: 'Active', location: 'Quesnel' },
  { clientNumber: '00010003', name: 'Cascade Forest Products Ltd.', type: 'Corporation', status: 'Active', location: 'Prince George' },
  { clientNumber: '00120890', name: 'Willowfield Ranch Ltd.', type: 'Corporation', status: 'Active', location: 'Williams Lake' },
  { clientNumber: '00133011', name: 'R. Fielding', type: 'Individual', status: 'Active', location: 'Houston' },
  { clientNumber: '00033201', name: 'Riverbend Community Forest', type: 'Association', status: 'Active', location: 'Whistler' },
  { clientNumber: '00099210', name: 'Uplands Grazing Assoc.', type: 'Association', status: 'Deactivated', location: 'Merritt' },
];

export const MOCK_MANAGEMENT_UNITS: ManagementUnit[] = [
  { muId: 'TSA27', name: 'Williams Lake TSA', muType: 'TSA', region: 'Cariboo', aacCubicMetres: 3_000_000 },
  { muId: 'TSA24', name: 'Prince George TSA', muType: 'TSA', region: 'Omineca', aacCubicMetres: 8_350_000 },
  { muId: 'TFL52', name: 'Tree Farm Licence 52', muType: 'TFL', region: 'Cariboo', aacCubicMetres: 640_000 },
  { muId: 'WL1885', name: 'Woodlot 1885', muType: 'Woodlot', region: 'Skeena', aacCubicMetres: 4_200 },
];

export const MOCK_RATES: RateFee[] = [
  { code: 'RENT-FL', description: 'Forest Licence annual rent', unit: '$/ha', rate: 0.36, effectiveDate: '2025-04-01' },
  { code: 'RENT-WL', description: 'Woodlot Licence annual rent', unit: '$/ha', rate: 0.24, effectiveDate: '2025-04-01' },
  { code: 'RENT-RAN', description: 'Grazing rent', unit: '$/AUM', rate: 3.20, effectiveDate: '2025-04-01' },
  { code: 'FEE-MARK', description: 'Private timber mark application fee', unit: '$/each', rate: 150.0, effectiveDate: '2024-04-01' },
  { code: 'FEE-CP', description: 'Cutting permit administration fee', unit: '$/each', rate: 500.0, effectiveDate: '2024-04-01' },
];

export const MOCK_ZONES: RangeZone[] = [
  { zoneId: 'Z-3', name: 'Chilcotin', district: 'DCC — Cariboo-Chilcotin', units: 14 },
  { zoneId: 'Z-5', name: 'Thompson', district: 'DKA — Kamloops', units: 22 },
  { zoneId: 'Z-8', name: 'Bulkley', district: 'DND — Nadina', units: 9 },
];

export const ORG_UNITS = [
  'DCC — Cariboo-Chilcotin',
  'DPG — Prince George',
  'DND — Nadina',
  'DSQ — Sea to Sky',
  'DVA — Campbell River',
  'DMK — Mackenzie',
  'DKA — Kamloops',
  'DCS — Cascades',
];

export function searchClients(q: { clientNumber?: string; name?: string; status?: string }): Client[] {
  return MOCK_CLIENTS.filter((c) => {
    if (q.clientNumber && !c.clientNumber.includes(q.clientNumber)) return false;
    if (q.name && !c.name.toLowerCase().includes(q.name.toLowerCase())) return false;
    if (q.status && c.status !== q.status) return false;
    return true;
  });
}

export function searchManagementUnits(q: { muId?: string; name?: string; muType?: string }): ManagementUnit[] {
  return MOCK_MANAGEMENT_UNITS.filter((m) => {
    if (q.muId && !m.muId.toLowerCase().includes(q.muId.toLowerCase())) return false;
    if (q.name && !m.name.toLowerCase().includes(q.name.toLowerCase())) return false;
    if (q.muType && m.muType !== q.muType) return false;
    return true;
  });
}
