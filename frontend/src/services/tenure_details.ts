import { apiGet, apiPost, apiPut, toQuery } from './http';

import type { CodeOption } from './codeLists';

/**
 * The tenure page's Details tab — legacy FTA100, from `FTA_100_TENURE.GET`.
 * Mirrors the backend `TenureDetailsDto`.
 *
 * `values` holds every value the screen shows, keyed by the package parameter's
 * name in camelCase without its `p_` (`p_file_status_st` → `fileStatusSt`):
 * dates are yyyy-mm-dd, blanks are "".
 */
export interface TenureDetails {
  forestFileId: string;
  values: Record<string, string>;
  /** "CODE - description" for each coded value, by the same keys. */
  descriptions: Record<string, string>;
  layout: TenureDetailsLayout;
  rules: TenureDetailsRules;
  /** The optimistic lock — sent back unchanged with the save. */
  revisions: Record<string, string>;
  /** The package's warnings on reading the file. */
  notices: string[];
}

/** Which sections and fields legacy shows for this file, and its labels. */
export interface TenureDetailsLayout {
  licenseeLabel: string;
  recreation: boolean;
  oldRecreation: boolean;
  privateMark: boolean;
  showDistrict: boolean;
  showFnAgreement: boolean;
  showMapNotation: boolean;
  showZone: boolean;
  showSalvage: boolean;
  showSfl: boolean;
  showFup: boolean;
  showTerm: boolean;
  showTenureTerm: boolean;
  showExtension: boolean;
  showReplaceable: boolean;
  showDesignate: boolean;
  extensionCountLabel: string;
  showEstTotalVol: boolean;
  showPulpwoodAac: boolean;
  showLocationArea: boolean;
  showDepositPurpose: boolean;
  showMarkPurpose: boolean;
  /** `purposeCode` or `b05Purpose`. */
  purposeField: string;
  annualRentLabel: string;
  showDeposits: boolean;
  showTimberLicenceAreas: boolean;
  /** A06: Gross rather than Initial Area, and no Other Area. */
  grossArea: boolean;
  showMinor: boolean;
  showBcts: boolean;
  showBctsFund: boolean;
  showPaymentMethod: boolean;
  showSpatialLater: boolean;
  showAac: boolean;
  showMark: boolean;
  /** The mark as legacy shows it: the mark, "ORG" for decked timber, or "" when hidden. */
  markDisplay: string;
  showWasteAssess: boolean;
  showFrz: boolean;
  showMarkAdditional: boolean;
  showQuotaType: boolean;
  cruiseBasedLabel: string;
  showOccupancy: boolean;
  showPulpwoodAgreement: boolean;
}

/** What may be changed — the backend applies the same rules to the save. */
export interface TenureDetailsRules {
  editable: boolean;
  /** Why not, when `editable` is false. */
  reason: string | null;
  /** The value keys that open for editing. */
  fields: string[];
  /** Keys that open only on another value (FUP FN fields, Pulpwood File). */
  conditionalFields: string[];
  /** Target statuses whose change needs a District Override Reason. */
  overrideStatuses: string[];
  /** Option list for Status: `statuses` or `recreationStatuses`. */
  statusList: string;
  /** Option list for Purpose. */
  purposeList: string;
  /** FTA100's Delete is enabled. */
  deletable: boolean;
  /** FTA100's Retire is enabled. */
  retirable: boolean;
  /** FTA100's Assign Mark Designate is shown and enabled. */
  assignsMarkDesignate: boolean;
  /** FTA100's Change Mark Designate (FTA101) is shown and enabled. */
  changesMarkDesignate: boolean;
}

/** GET /api/fta/tenures/{id}/details */
export function getTenureDetails(forestFileId: string): Promise<TenureDetails> {
  return apiGet<TenureDetails>(`/api/fta/tenures/${encodeURIComponent(forestFileId)}/details`);
}

/** The edit form's dropdowns: current codes by list name. */
export function getTenureDetailsOptions(): Promise<Record<string, CodeOption[]>> {
  return apiGet<Record<string, CodeOption[]>>('/api/fta/tenure-lookups/tenure-details');
}

/** PUT /api/fta/tenures/{id}/details — legacy FTA100 Save (FTA_ADMIN). */
export function updateTenureDetails(
  forestFileId: string,
  revisions: Record<string, string>,
  values: Record<string, string>,
): Promise<{ warnings: string[] }> {
  return apiPut<{ warnings: string[] }>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/details`,
    { revisions, values },
  );
}

const detailsAction = (forestFileId: string, action: string, revisions: Record<string, string>) =>
  apiPost<void>(`/api/fta/tenures/${encodeURIComponent(forestFileId)}/details/${action}`, {
    revisions,
  });

/** FTA100 Delete (FTA_ADMIN): removes the file and everything under it. */
export const deleteTenure = (forestFileId: string, revisions: Record<string, string>) =>
  detailsAction(forestFileId, 'delete', revisions);

/** FTA100 Retire (FTA_ADMIN). */
export const retireTenure = (forestFileId: string, revisions: Record<string, string>) =>
  detailsAction(forestFileId, 'retire', revisions);

/** FTA100 Assign Mark Designate (FTA_ADMIN). */
export const assignTenureMarkDesignate = (
  forestFileId: string,
  revisions: Record<string, string>,
) => detailsAction(forestFileId, 'assign-mark-designate', revisions);

/** FTA101 Change Mark Designate's view: the source file, its designate and the lock. */
export interface MarkDesignate {
  forestFileId: string;
  sourceFileId: string;
  markDesignate: string;
  /** This file's TIMBER_TENURE revision count, sent back on save. */
  revisionCount: string;
}

/** FTA101 Go (FTA_ADMIN): the source file's designate — this file's when blank. */
export const getMarkDesignate = (forestFileId: string, sourceFileId: string) =>
  apiGet<MarkDesignate>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/details/mark-designate${toQuery({
      sourceFileId,
    })}`,
  );

/** FTA101 Save Designate (FTA_ADMIN): copies the source file's designate onto this file. */
export const changeTenureMarkDesignate = (
  forestFileId: string,
  sourceFileId: string,
  revisionCount: string,
) =>
  apiPost<void>(
    `/api/fta/tenures/${encodeURIComponent(forestFileId)}/details/change-mark-designate`,
    { sourceFileId, revisionCount },
  );
