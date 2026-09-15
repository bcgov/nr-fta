import { apiGet } from './http';

/** Mirrors the backend RecreationChildDtos.Tombstone. */
export interface RecreationTombstone {
  fileStatusCode: string | null;
  fileStatusDesc: string | null;
  fileStatusDate: string | null;
  fileTypeCode: string | null;
  fileTypeDesc: string | null;
  adminOrgCode: string | null;
  adminOrgName: string | null;
}

/**
 * Mirrors the backend RecreationProjectDto.
 *
 * Five values are derived rather than stored and cannot be edited:
 * `projectTypeCode`/`projectType` and `projectLength`/`projectArea` are summed
 * over the project's current map features, and `definedCampsites` is a count.
 */
export interface RecreationProject {
  forestFileId: string | null;
  projectName: string | null;
  projectTypeCode: string | null;
  projectType: string | null;
  projectLength: number | null;
  projectArea: number | null;
  definedCampsites: number | null;
  assocFilesExist: string | null;
  riskRatingCode: string | null;
  projectEstablishedDate: string | null;
  /** Labelled "Closest Community"; stored upper-cased. */
  siteLocation: string | null;
  utmZone: number | null;
  utmNorthing: number | null;
  utmEasting: number | null;
  rightOfWay: number | null;
  featureCode: string | null;
  userDaysCode: string | null;
  maintainStdCode: string | null;
  campHostInd: string | null;
  overflowCampsites: number | null;
  lowMobilityAccessInd: string | null;
  recreationViewInd: string | null;
  resourceFeatureInd: string | null;
  controlAccessCode: string | null;
  lastRecInspectionDate: string | null;
  lastHzrdTreeAssessDate: string | null;
  archImpactAssessInd: string | null;
  archImpactDate: string | null;
  bordenNo: string | null;
  /** Held in RECREATION_COMMENT under the AIA type, not on the project row. */
  aiaComment: string | null;
  /** Labelled "Field Note". */
  siteDescription: string | null;
  revisionCount: number | null;
}

export interface RecreationDistrict {
  districtCode: string | null;
  description: string | null;
}

export interface RecreationFee {
  feeId: number | null;
  feeAmount: number | null;
  feeStartDate: string | null;
  feeEndDate: string | null;
  feeCode: string | null;
  feeDescription: string | null;
  mondayInd: string | null;
  tuesdayInd: string | null;
  wednesdayInd: string | null;
  thursdayInd: string | null;
  fridayInd: string | null;
  saturdayInd: string | null;
  sundayInd: string | null;
  revisionCount: number | null;
}

export interface RecreationAccess {
  accessCode: string | null;
  accessDescription: string | null;
  subAccessCode: string | null;
  subAccessDescription: string | null;
  revisionCount: number | null;
}

export interface RecreationAttachment {
  attachmentId: number | null;
  fileName: string | null;
  revisionCount: number | null;
}

/**
 * What the current user may change.
 *
 * An unscoped FTA_ADMIN plays the legacy "Recreation headquarters" part and may
 * save any file; a district-scoped one may save only files their district
 * administers. Two rules close saving regardless: the file must be in `HI`
 * status and the project must have a spatial description.
 */
export interface RecreationPermissions {
  parentSaveEnabled: boolean;
  childSaveEnabled: boolean;
  /** Project Established date and the establishment order are headquarters-only. */
  establishedFieldsLocked: boolean;
}

export interface RecreationProjectDetail {
  forestFileId: string;
  tombstone: RecreationTombstone;
  /** Null when the file exists but has no project row yet — a real state. */
  project: RecreationProject | null;
  districts: RecreationDistrict[];
  fees: RecreationFee[];
  access: RecreationAccess[];
  attachments: RecreationAttachment[];
  permissions: RecreationPermissions;
}

/** The days a fee applies on, in display order. */
export const FEE_DAYS = [
  { key: 'mondayInd', label: 'M' },
  { key: 'tuesdayInd', label: 'Tu' },
  { key: 'wednesdayInd', label: 'W' },
  { key: 'thursdayInd', label: 'Th' },
  { key: 'fridayInd', label: 'F' },
  { key: 'saturdayInd', label: 'Sa' },
  { key: 'sundayInd', label: 'Su' },
] as const;

/** Trail projects, where Right of Way becomes mandatory rather than read-only. */
const TRAIL_PROJECT_TYPES = ['RTR', 'IFT', 'TBL', 'RTE'];

/** Whether the project is a trail, which changes how Right of Way behaves. */
export function isTrailProject(project: RecreationProject | null): boolean {
  return Boolean(project?.projectTypeCode && TRAIL_PROJECT_TYPES.includes(project.projectTypeCode));
}

/** GET /api/fta/recreation/{fileId} — FTA701 recreation project detail. */
export function getRecreationProject(fileId: string): Promise<RecreationProjectDetail> {
  return apiGet<RecreationProjectDetail>(`/api/fta/recreation/${encodeURIComponent(fileId)}`);
}
