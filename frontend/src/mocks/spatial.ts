// Mock spatial data for the Exhibit A tenure-map / feature workflow
// (FTA304/305/315/316), standing in for the FTA geospatial service. Coordinates
// are illustrative points in the BC interior ([lat, lng]).

export type LatLng = [number, number];

export interface MapFeature {
  featureId: string;
  type: 'Cut Block' | 'Road' | 'Reserve' | 'Conflict';
  label: string;
  areaHa?: number;
  status: string;
}

export interface ExhibitA {
  esfId: string;
  fileId: string;
  /** Approximate map centre for the submission. */
  centre: LatLng;
  /** Outer boundary polygon of the tenure area. */
  boundary: LatLng[];
  /** Point features (conflicts, reserves) to plot as markers. */
  markers: { id: string; position: LatLng; label: string; kind: 'reserve' | 'conflict' }[];
  features: MapFeature[];
  conflicts: { conflictId: string; against: string; overlapHa: number; severity: 'High' | 'Medium' | 'Low' }[];
}

const CENTRE: LatLng = [52.13, -122.14]; // near Williams Lake

export const MOCK_EXHIBIT_A: ExhibitA = {
  esfId: 'ESF-100234',
  fileId: 'A19201',
  centre: CENTRE,
  boundary: [
    [52.16, -122.2],
    [52.17, -122.08],
    [52.11, -122.05],
    [52.09, -122.16],
    [52.13, -122.22],
  ],
  markers: [
    { id: 'M1', position: [52.145, -122.12], label: 'Wildlife Habitat Area', kind: 'reserve' },
    { id: 'M2', position: [52.12, -122.1], label: 'Overlap with A20115', kind: 'conflict' },
  ],
  features: [
    { featureId: 'F-001', type: 'Cut Block', label: 'BLK-001', areaHa: 24.6, status: 'Proposed' },
    { featureId: 'F-002', type: 'Cut Block', label: 'BLK-002', areaHa: 31.2, status: 'Proposed' },
    { featureId: 'F-003', type: 'Road', label: 'Beaver Creek FSR', status: 'Existing' },
    { featureId: 'F-004', type: 'Reserve', label: 'Riparian Reserve', areaHa: 8.4, status: 'Retained' },
    { featureId: 'F-005', type: 'Conflict', label: 'WHA overlap', areaHa: 2.1, status: 'Unresolved' },
  ],
  conflicts: [
    { conflictId: 'C-1', against: 'A20115 (Forest Licence)', overlapHa: 2.1, severity: 'Medium' },
    { conflictId: 'C-2', against: 'WHA 6-233', overlapHa: 0.8, severity: 'High' },
  ],
};

export function findExhibitA(esfId: string): ExhibitA | undefined {
  // Single mock submission has spatial data; others reuse it with the esfId
  // swapped so any inbox item can open a map.
  return { ...MOCK_EXHIBIT_A, esfId };
}
