// Mock data for the Range line (grazing / hay cutting) — range agreements,
// range units/pastures, rotations, and associated land base. Stands in for the
// FTA backend until the Spring Boot APIs exist.

export interface RangeAgreementSummary {
  agreementId: string;
  agreementType: 'Grazing Licence' | 'Grazing Permit' | 'Hay Cutting Licence';
  status: 'Active' | 'Pending' | 'Expired' | 'Suspended';
  holderName: string;
  holderClient: string;
  orgUnit: string;
  authorizedAums: number; // animal unit months
  issueDate: string;
  expiryDate: string;
}

export interface Rotation {
  year: number;
  unit: string;
  kind: 'Grazing' | 'Hay Cutting';
  startDate: string;
  endDate: string;
  aums: number;
}

export interface LandBaseParcel {
  parcelId: string;
  description: string;
  areaHa: number;
  tenureType: string;
}

export interface RangeAgreementDetail extends RangeAgreementSummary {
  managementUnit: string;
  rotations: Rotation[];
  landBase: LandBaseParcel[];
  usageHistory: { year: number; authorizedAums: number; actualAums: number }[];
}

export interface RangeUnitSummary {
  unitId: string;
  name: string;
  zone: string;
  pastures: number;
  areaHa: number;
  orgUnit: string;
}

export const MOCK_RANGE_AGREEMENTS: RangeAgreementSummary[] = [
  { agreementId: 'RAN076543', agreementType: 'Grazing Licence', status: 'Active', holderName: 'Willowfield Ranch Ltd.', holderClient: '00120890', orgUnit: 'DCC — Cariboo-Chilcotin', authorizedAums: 1240, issueDate: '2015-04-01', expiryDate: '2035-03-31' },
  { agreementId: 'RAN081200', agreementType: 'Grazing Permit', status: 'Active', holderName: 'Grasslands Cattle Co.', holderClient: '00121455', orgUnit: 'DKA — Kamloops', authorizedAums: 860, issueDate: '2019-05-01', expiryDate: '2029-04-30' },
  { agreementId: 'RAN078120', agreementType: 'Hay Cutting Licence', status: 'Pending', holderName: 'R. Fielding', holderClient: '00133011', orgUnit: 'DND — Nadina', authorizedAums: 210, issueDate: '2024-03-01', expiryDate: '2034-02-28' },
  { agreementId: 'RAN060044', agreementType: 'Grazing Licence', status: 'Expired', holderName: 'Uplands Grazing Assoc.', holderClient: '00099210', orgUnit: 'DCS — Cascades', authorizedAums: 1540, issueDate: '1998-04-01', expiryDate: '2018-03-31' },
];

const DETAIL_EXTRAS: Pick<RangeAgreementDetail, 'managementUnit' | 'rotations' | 'landBase' | 'usageHistory'> = {
  managementUnit: 'RU 0576 — Big Creek',
  rotations: [
    { year: 2025, unit: 'RU-0576-A', kind: 'Grazing', startDate: '2025-06-01', endDate: '2025-09-15', aums: 620 },
    { year: 2025, unit: 'RU-0576-B', kind: 'Grazing', startDate: '2025-06-15', endDate: '2025-10-01', aums: 480 },
    { year: 2024, unit: 'RU-0576-A', kind: 'Grazing', startDate: '2024-06-01', endDate: '2024-09-15', aums: 610 },
  ],
  landBase: [
    { parcelId: 'LB-01', description: 'Big Creek allotment — north', areaHa: 4200, tenureType: 'Crown Range' },
    { parcelId: 'LB-02', description: 'Big Creek allotment — south', areaHa: 3150, tenureType: 'Crown Range' },
  ],
  usageHistory: [
    { year: 2025, authorizedAums: 1240, actualAums: 1100 },
    { year: 2024, authorizedAums: 1240, actualAums: 1185 },
    { year: 2023, authorizedAums: 1200, actualAums: 1190 },
  ],
};

export const MOCK_RANGE_UNITS: RangeUnitSummary[] = [
  { unitId: 'RU-0576', name: 'Big Creek', zone: 'Zone 3 — Chilcotin', pastures: 4, areaHa: 7350, orgUnit: 'DCC — Cariboo-Chilcotin' },
  { unitId: 'RU-0812', name: 'Nicola Valley', zone: 'Zone 5 — Thompson', pastures: 6, areaHa: 5120, orgUnit: 'DKA — Kamloops' },
  { unitId: 'RU-0781', name: 'Nadina Bench', zone: 'Zone 8 — Bulkley', pastures: 3, areaHa: 2980, orgUnit: 'DND — Nadina' },
];

export interface RangeSearchCriteria {
  agreementId?: string;
  holderName?: string;
  orgUnit?: string;
  status?: string;
}

export function searchRangeAgreements(c: RangeSearchCriteria): RangeAgreementSummary[] {
  return MOCK_RANGE_AGREEMENTS.filter((a) => {
    if (c.agreementId && !a.agreementId.toLowerCase().includes(c.agreementId.toLowerCase())) return false;
    if (c.holderName && !a.holderName.toLowerCase().includes(c.holderName.toLowerCase())) return false;
    if (c.orgUnit && !a.orgUnit.toLowerCase().includes(c.orgUnit.toLowerCase())) return false;
    if (c.status && a.status !== c.status) return false;
    return true;
  });
}

export function findRangeAgreement(agreementId: string): RangeAgreementDetail | undefined {
  const summary = MOCK_RANGE_AGREEMENTS.find((a) => a.agreementId.toLowerCase() === agreementId.toLowerCase());
  if (!summary) return undefined;
  return { ...summary, ...DETAIL_EXTRAS };
}

export interface RangeUnitCriteria {
  unitId?: string;
  name?: string;
  zone?: string;
}

export function searchRangeUnits(c: RangeUnitCriteria): RangeUnitSummary[] {
  return MOCK_RANGE_UNITS.filter((u) => {
    if (c.unitId && !u.unitId.toLowerCase().includes(c.unitId.toLowerCase())) return false;
    if (c.name && !u.name.toLowerCase().includes(c.name.toLowerCase())) return false;
    if (c.zone && !u.zone.toLowerCase().includes(c.zone.toLowerCase())) return false;
    return true;
  });
}

export function findRangeUnit(unitId: string): RangeUnitSummary | undefined {
  return MOCK_RANGE_UNITS.find((u) => u.unitId.toLowerCase() === unitId.toLowerCase());
}

export const RANGE_STATUS_TAG: Record<RangeAgreementSummary['status'], 'green' | 'blue' | 'gray' | 'red'> = {
  Active: 'green',
  Pending: 'blue',
  Expired: 'gray',
  Suspended: 'red',
};
