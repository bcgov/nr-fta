import { apiGet, apiPut } from './http';

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
