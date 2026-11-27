import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

import type { InvoicePreview } from './billing';
import type { CodeOption } from './codeLists';

// The range Tenure tab — legacy FTA100R, through THE.FTA_100RANGE_TENURE.mainline — and range
// Add New. Mirrors the backend's RangeTenureTabController (ca.bc.gov.nrs.fta.range.tenuretab).

const base = (forestFileId: string) => `/api/fta/range-tenures/${encodeURIComponent(forestFileId)}`;

/** A district's range zone: code, description and contact (FTA631; legacy's GetZoneContact). */
export interface RangeZone {
  code: string;
  description: string | null;
  contact: string | null;
}

/** A special condition code, with the title and standard text a new condition starts with. */
export interface RangeConditionCode {
  code: string;
  title: string | null;
  note: string | null;
}

/** The keys of the fields the Tenure tab can edit. */
export type RangeTenureField =
  | 'fileStatusSt'
  | 'fileStatusDate'
  | 'fileTypeCode'
  | 'forestDistrictNo'
  | 'mgmtUnitType'
  | 'mgmtUnitId'
  | 'districtAdminZone'
  | 'fileName'
  | 'originalIssueYear'
  | 'issueYear'
  | 'tenureTermYears'
  | 'expiryYear'
  | 'replacementTermYears';

/** GET …/tenure-tab: FTA100R's form for the file, and what the user may do on it. */
export interface RangeTenureTab {
  forestFileId: string;
  /**
   * The values by key: every RangeTenureField, plus adminOrgUnitNo, mgmtUnitName,
   * replacementCount, replacementLabel, prevFileStatusSt, prevFileStatusDate and
   * prevReplacementTermYears. Years are four-digit strings, '' when unset.
   */
  values: Record<string, string>;
  /** The revision counts the save is locked on; send back unchanged. */
  revisions: Record<string, string>;
  /** fileStatusSt and fileTypeCode as "CODE - description"; zoneDescription, zoneContact. */
  descriptions: Record<string, string>;
  /** Whether the user may change this file at all. */
  editable: boolean;
  editableFields: RangeTenureField[];
  /** E01/H01: the Historical and Replacement sections. */
  replaceable: boolean;
  /** False on a PA file, whose term is not checked. */
  showTenureTerm: boolean;
  retirable: boolean;
  cancelReplacement: boolean;
  statuses: CodeOption[];
  /** The range types of the file's own family. */
  fileTypes: CodeOption[];
  zones: RangeZone[];
  conditionCodes: RangeConditionCode[];
}

/** A successful write: legacy's confirmation and the package's warnings. */
export interface RangeWriteResult {
  message: string;
  warnings: string[];
}

export const getRangeTenureTab = (forestFileId: string) =>
  apiGet<RangeTenureTab>(`${base(forestFileId)}/tenure-tab`);

export const saveRangeTenureTab = (
  forestFileId: string,
  revisions: Record<string, string>,
  values: Record<string, string>,
) => apiPut<RangeWriteResult>(`${base(forestFileId)}/tenure-tab`, { revisions, values });

export const retireRangeTenure = (forestFileId: string, revisions: Record<string, string>) =>
  apiPost<RangeWriteResult>(`${base(forestFileId)}/tenure-tab/retire`, { revisions });

export const cancelRangeReplacement = (forestFileId: string, revisions: Record<string, string>) =>
  apiPost<RangeWriteResult>(`${base(forestFileId)}/tenure-tab/cancel-replacement`, {
    revisions,
  });

export interface RangeConditionInput {
  code: string;
  title: string;
  description: string;
  revisionCount: number | null;
}

/** Save Condition: a new special condition when `skey` is null, else a change to that one. */
export const saveRangeCondition = (
  forestFileId: string,
  skey: number | null,
  body: RangeConditionInput,
) =>
  skey === null
    ? apiPost<RangeWriteResult>(`${base(forestFileId)}/special-conditions`, body)
    : apiPut<RangeWriteResult>(`${base(forestFileId)}/special-conditions/${skey}`, body);

export const deleteRangeCondition = (
  forestFileId: string,
  skey: number,
  revisionCount: number | null,
) =>
  apiDelete<RangeWriteResult>(
    `${base(forestFileId)}/special-conditions/${skey}${toQuery({
      revisionCount: revisionCount === null ? undefined : String(revisionCount),
    })}`,
  );

/** Preview Invoice: the FTA695 invoice for the file, this year. */
export const getRangeInvoicePreview = (forestFileId: string) =>
  apiGet<InvoicePreview>(`${base(forestFileId)}/invoice-preview`);

/** A district's range zones, for the Zone dropdown. */
export const getRangeZonesWithContact = (districtNo: string) =>
  apiGet<RangeZone[]>(`/api/fta/range-tenures/lookups/zones${toQuery({ districtNo })}`);

/** The range file types Add New Tenure creates through FTA100R (E01, E02, H01, H02). */
export const getRangeCreateTypes = () =>
  apiGet<CodeOption[]>('/api/fta/range-tenures/lookups/create-types');

export interface CreateRangeTenureRequest {
  fileTypeCode: string;
  forestDistrictNo: string;
  fileStatusDate: string | null;
  mgmtUnitType: string;
  mgmtUnitId: string | null;
  districtAdminZone: string;
  fileName: string | null;
}

/** FTA100R's Add New: a PA range tenure; returns its generated file id. */
export const createRangeTenure = (req: CreateRangeTenureRequest) =>
  apiPost<{ forestFileId: string; warnings: string[] }>('/api/fta/range-tenures', req);
