import { apiGet, apiPost, toQuery } from './http';

/**
 * The tenure "Tenure application" tab — legacy FTA950 (Tenure Application List):
 * the ESF smart-form CP requests, the rejected CP submissions and the spatial
 * submissions (tenure applications) for the file.
 */

/** Which spatial-submission columns legacy shows for the file type. */
export interface TenureAppColumns {
  status: boolean;
  applicationType: boolean;
  featureType: boolean;
  chart: boolean;
  cuttingPermit: boolean;
  timberMark: boolean;
  location: boolean;
  pointOfCommencement: boolean;
  length: boolean;
  area: boolean;
  /** The CP (and an A file's mark) open the cutting permit; else a mark opens its own file. */
  cpLinksToPermit: boolean;
}

export interface TenureAppRow {
  submissionId: number | null;
  submissionDate: string | null;
  orgUnitCode: string | null;
  orgUnitName: string | null;
  tenureAppId: number | null;
  statusCode: string | null;
  statusDesc: string | null;
  applicationTypeCode: string | null;
  applicationTypeDesc: string | null;
  purposeDesc: string | null;
  description: string | null;
  featureTypeDesc: string | null;
  cuttingPermitId: string | null;
  hvaSkey: number | null;
  timberMark: string | null;
  location: string | null;
  pointOfCommencement: string | null;
  chartAreaId: string | null;
  chartBlockId: string | null;
  chartVolume: number | null;
  objectLength: number | null;
  objectArea: number | null;
  mapFeatureId: number | null;
  exhibitAImage: boolean;
  regenInProgress: boolean;
  professionalDeclaration: boolean;
  statusNotificationClearance: boolean;
  decisionDate: string | null;
  issuanceDate: string | null;
  issuePermitAllowed: boolean;
  issuePermitReason: string | null;
}

export interface TenureAppCpRequest {
  requestGuid: string | null;
  hvaSkey: number | null;
  cuttingPermitId: string | null;
  requestDate: string | null;
  requestCode: string | null;
  requestDesc: string | null;
  statusCode: string | null;
  statusDesc: string | null;
  acceptedUserId: string | null;
  acceptedDate: string | null;
  rationaleDetail: string | null;
  rationaleDocument: boolean;
}

export interface TenureAppCpRejection {
  cuttingPermitId: string | null;
  submissionId: number | null;
  submissionDate: string | null;
  rejectionDate: string | null;
  rejectionMessage: string | null;
}

export interface TenureAppTab {
  fileTypeCode: string | null;
  columns: TenureAppColumns;
  warning: string | null;
  cpRequests: TenureAppCpRequest[];
  cpRejections: TenureAppCpRejection[];
  applications: TenureAppRow[];
  /** Lists this database could not read (a table it lacks). */
  unavailable: string[];
}

export interface TenureAppProfDec {
  tenureAppId: number | null;
  forestFileId: string | null;
  submissionId: number | null;
  declarationTypeCode: string | null;
  declarationTypeDesc: string | null;
  declarationDate: string | null;
  clientNumber: string | null;
  clientLocationCode: string | null;
  clientName: string | null;
  declarantName: string | null;
  certificationReferenceId: string | null;
  professionalIdentifierCode: string | null;
  phoneNumber: string | null;
  emailAddress: string | null;
  webSiteAddress: string | null;
  declarantComments: string | null;
  cutBlockIds: string | null;
}

const base = (forestFileId: string) =>
  `/api/fta/tenures/${encodeURIComponent(forestFileId)}/tenure-applications`;

/** GET — the tab's three lists and the columns for the file type. */
export function getTenureAppTab(forestFileId: string): Promise<TenureAppTab> {
  return apiGet<TenureAppTab>(base(forestFileId));
}

/** GET — the professional declarations on one application (legacy's Prof Dec popup). */
export function getTenureAppProfDecs(
  forestFileId: string,
  tenureAppId: number,
  cuttingPermitId: string | null,
  hvaSkey: number | null,
): Promise<TenureAppProfDec[]> {
  return apiGet<TenureAppProfDec[]>(
    `${base(forestFileId)}/${tenureAppId}/professional-declarations${toQuery({
      cuttingPermitId,
      hvaSkey,
    })}`,
  );
}

/** POST — issue the permit for an approved (or issued) application. */
export function issueTenureAppPermit(
  forestFileId: string,
  tenureAppId: number,
  body: { hvaSkey: number | null; documentUri: string },
): Promise<{ tenureAppId: number; message: string }> {
  return apiPost(`${base(forestFileId)}/${tenureAppId}/issue-permit`, body);
}
