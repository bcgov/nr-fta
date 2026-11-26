import { apiGet, apiPostForm, toQuery } from './http';

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

/** What Issue Permit sends — legacy's Issue Permit popup. */
export interface IssuePermitInput {
  hvaSkey: number | null;
  documentName: string;
  recipients: string[];
  attachDocument: boolean;
  file: File;
}

export interface IssuePermitResult {
  tenureAppId: number;
  message: string;
  /** Set when the permit was issued but the e-mail could not be sent. */
  emailWarning: string | null;
}

/**
 * POST (multipart) — upload the permit PDF to document management, issue the
 * permit for an approved (or issued) application and e-mail the recipients.
 */
export function issueTenureAppPermit(
  forestFileId: string,
  tenureAppId: number,
  input: IssuePermitInput,
): Promise<IssuePermitResult> {
  const form = new FormData();
  if (input.hvaSkey !== null) form.append('hvaSkey', String(input.hvaSkey));
  form.append('documentName', input.documentName);
  input.recipients.forEach((r) => form.append('recipients', r));
  form.append('attachDocument', String(input.attachDocument));
  form.append('file', input.file, input.file.name);
  return apiPostForm(`${base(forestFileId)}/${tenureAppId}/issue-permit`, form);
}
