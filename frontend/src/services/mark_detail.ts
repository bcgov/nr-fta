import { apiGet, apiGetForBlob, apiPost, apiPostForBlob, apiPut } from './http';

// Mirrors the backend MarkDetailDto (ca.bc.gov.nrs.fta.mark.dto), which ports
// the legacy THE.FTA_510_PRIVATE_MARK.GET record enriched with the land index
// (FTA_511), associated clients (FTA_513), amendment history and the notes on
// the mark's forest file (FTA_970).

export interface MarkLandIndex {
  primaryLandIndexCode: string | null;
  secondaryLandIndexCode: string | null;
  primaryLandIndexCodeDesc: string | null;
  secondaryLandIndexCodeDesc: string | null;
  markLandIndexDesc: string | null;
  indexDeactivateDate: string | null; // ISO date
  markLandIndexSkey: number | null;
  revisionCount: number | null;
}

export interface MarkAssociatedClient {
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  clientCity: string | null;
  forClientLinkSkey: number | null;
  fileClientType: string | null;
  fileClientTypeDesc: string | null;
  licenseeStartDt: string | null; // ISO date
  licenseeEndDate: string | null; // ISO date
  revisionCount: number | null;
}

export interface MarkAmendment {
  amendRequestDate: string | null; // ISO date
  prvMrkAmdStsSt: string | null;
  /** "<code> - <description>" from PRIVATE_MARK_AMEND_STATUS_CODE. */
  prvMrkAmdStsDesc: string | null;
  revisionCount: number | null;
  requestingUserid: string | null;
  /** Requested area, hectares. */
  permitBlockArea: number | null;
  /** The requested amendment changes (P_OF_C_OR_LEGAL). */
  requestedChanges: string | null;
}

/** One note on the mark's forest file — PROVFOREST_NOTE, via FTA_970_FOREST_NOTE. */
export interface MarkNote {
  entryUserid: string | null;
  entryTimestamp: string | null; // ISO date-time
  note: string | null;
}

export interface MarkDetail {
  /** Null until the application is issued — the record is then known by its certificate. */
  timberMark: string | null;
  certificate: string | null;
  /** Null until the application is given a forest file; notes need one. */
  forestFileId: string | null;
  fileTypeCode: string | null;
  markStatusCode: string | null;
  markStatusDate: string | null; // ISO date
  markApplicationDate: string | null;
  markIssueDate: string | null;
  markExpiryDate: string | null;
  markCancelDate: string | null;
  /** Initial term in months. */
  tenureTerm: number | null;
  forestDistrict: string | null;
  orgUnitCode: string | null;
  clientNumber: string | null;
  clientLocnCode: string | null;
  clientName: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  crownGrantedAcqDesc: string | null;
  grantedAcqrdDate: string | null;
  permitBlockLocn: string | null;
  permitBlockArea: number | null;
  /** Legacy "Legal": the legal description, or proof of Crown grant. */
  proofOfCrownOrLegal: string | null;
  // "<code> - <description>" for the coded fields above; null when unresolved.
  markStatusDesc: string | null;
  fileTypeDesc: string | null;
  districtDesc: string | null;
  regionDesc: string | null;
  markingMethodDesc: string | null;
  markingInstrumentDesc: string | null;
  /** Legacy "LTO PID" — stored as BCAA_FOLIO_NUMBER. */
  bcaaFolioNumber: string | null;
  mgmtUnitTypeCode: string | null;
  mgmtUnitId: string | null;
  mgmtUnitDesc: string | null;
  cascadeSplitCode: string | null;
  cascadeSplitDesc: string | null;
  /** Legacy "Reg/Comp" — the two halves of MAP_REFERENCE_ID. */
  mapReferenceReg: string | null;
  mapReferenceComp: string | null;
  markExtendDate: string | null; // ISO date
  markExtendCount: number | null;
  markAmendDate: string | null; // ISO date
  /** The outstanding (PI or HN) amendment's status, if there is one. */
  outstandingAmendStatus: string | null;
  outstandingAmendStatusDesc: string | null;
  landIndex: MarkLandIndex[];
  clients: MarkAssociatedClient[];
  amendments: MarkAmendment[];
  notes: MarkNote[];
  /** Sent back on save, for optimistic locking. */
  revisionCount: number | null;
  amendRevisionCount: number | null;
  /** What the current user may change — computed by the backend from the legacy FTA510 rules. */
  editRules: MarkEditRules | null;
}

/** Mirrors the backend MarkEditRules: which groups of fields are open, and to what. */
export interface MarkEditRules {
  editable: boolean;
  /** Why not, when `editable` is false. */
  reason: string | null;
  applicationDate: boolean;
  term: boolean;
  /** District and the Location section's fields. */
  location: boolean;
  /** Marking Requirements and Marking Instrument. */
  marking: boolean;
  /** Mark Type — saving one issues the mark (a timber mark is generated for it). */
  markType: boolean;
  /** Issued, Expired, Extended, Cancelled, Crown Granted Date and Description. */
  branch: boolean;
  status: boolean;
  statusOptions: string[];
  amendmentStatus: boolean;
  amendmentStatusOptions: string[];
  /** Adding a land index (FTA511) — open at any status but HX, DV and DD, and not for B15/B16. */
  landIndex: boolean;
  /** Why not, when `landIndex` is false — shown beside the disabled Add button. */
  landIndexReason: string | null;
  /** Adding an associated client (FTA513) — only at HI, PI or PA, and not for B15/B16. */
  clients: boolean;
  /** Why not, when `clients` is false. */
  clientsReason: string | null;
  /** Whether an amendment may be requested (FTA512: HI, none outstanding). */
  amendments: boolean;
  /** Why not, when `amendments` is false. */
  amendmentsReason: string | null;
  /** Whether the user may Submit to HQ (FTA510: a district, a PA application, a client). */
  submit: boolean;
  /** Why not, when `submit` is false. */
  submitReason: string | null;
}

/** Mirrors the backend MarkClientRequest — the FTA513 add row. Dates are ISO yyyy-mm-dd. */
export interface MarkClientRequest {
  clientNumber: string;
  clientLocnCode: string;
  /** FILE_CLIENT_TYPE_CODE: A main licensee, B licensee, C previous licensee, … */
  fileClientType: string;
  licenseeStartDate: string | null;
  licenseeEndDate: string | null;
}

/**
 * POST /api/fta/marks/{id}/clients — add an associated client (FTA513). `note`
 * is set when adding a main licensee made the current one the previous licensee.
 */
export function addClient(
  id: string,
  request: MarkClientRequest,
  byCertificate = false,
): Promise<{ note?: string }> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPost<{ note?: string }>(
    `/api/fta/marks/${encodeURIComponent(id)}/clients${query}`,
    request,
  );
}

/** Mirrors the backend MarkLandIndexRequest — the FTA511 add row. */
export interface MarkLandIndexRequest {
  /** Land District/Island (PRIMARY_LAND_INDEX_CODE); required. */
  primaryLandIndexCode: string;
  /** Primary ID (SECONDARY_LAND_INDEX_CODE); optional. */
  secondaryLandIndexCode: string | null;
  /** Up to 40 characters; optional. */
  markLandIndexDesc: string | null;
}

/** POST /api/fta/marks/{id}/land-index — add a land index to the mark (FTA511). */
export function addLandIndex(
  id: string,
  request: MarkLandIndexRequest,
  byCertificate = false,
): Promise<void> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPost<void>(`/api/fta/marks/${encodeURIComponent(id)}/land-index${query}`, request);
}

/** Mirrors the backend MarkAmendmentRequest — the FTA512 form. */
export interface MarkAmendmentRequest {
  /** Requested area, hectares: 0 to 9999.9, one decimal; null for 0.0. */
  permitBlockArea: number | null;
  /** Requested amendment changes, up to 2000 characters; required. */
  requestedChanges: string;
}

/** POST /api/fta/marks/{id}/amendments — request an amendment to the mark (FTA512). */
export function addAmendment(
  id: string,
  request: MarkAmendmentRequest,
  byCertificate = false,
): Promise<void> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPost<void>(`/api/fta/marks/${encodeURIComponent(id)}/amendments${query}`, request);
}

/** Mirrors the backend MarkUpdateRequest. Dates are ISO yyyy-mm-dd; blanks are null. */
export interface MarkUpdateRequest {
  revisionCount: number | null;
  amendRevisionCount: number | null;
  applicationDate: string | null;
  tenureTerm: number | null;
  forestDistrict: string | null;
  markingMethodCode: string | null;
  markingInstrumentCode: string | null;
  permitBlockLocn: string | null;
  proofOfCrownOrLegal: string | null;
  bcaaFolioNumber: string | null;
  permitBlockArea: number | null;
  mgmtUnitTypeCode: string | null;
  mgmtUnitId: string | null;
  cascadeSplitCode: string | null;
  mapReferenceReg: string | null;
  mapReferenceComp: string | null;
  markIssueDate: string | null;
  markExpiryDate: string | null;
  markExtendDate: string | null;
  markCancelDate: string | null;
  grantedAcqrdDate: string | null;
  crownGrantedAcqDesc: string | null;
  markStatusCode: string | null;
  amendStatusCode: string | null;
  /** Mark Type. Sent for a mark that has none, it issues the mark on save. */
  fileTypeCode: string | null;
}

/**
 * POST /api/fta/marks/{id}/print — legacy FTA510's Print: the FTA402 Registered
 * Timber Mark Certificate as a PDF. For FTA_TIMBER_MARK_DISTRICT_ADMIN it also
 * marks the mark issued (HN → HI), so re-read the mark afterwards.
 */
export function printMarkCertificate(id: string, byCertificate = false): Promise<Blob> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPostForBlob(`/api/fta/marks/${encodeURIComponent(id)}/print${query}`);
}

/**
 * GET /api/fta/marks/{id}/snapshot — the mark as it stands now, as a PDF stamped
 * with the time and the user: a point-in-time record. Changes nothing.
 */
export function getMarkSnapshot(id: string, byCertificate = false): Promise<Blob> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiGetForBlob(`/api/fta/marks/${encodeURIComponent(id)}/snapshot${query}`);
}

/**
 * PUT /api/fta/marks/{id} — save the mark (the FTA510 "Save"). Returns the saved
 * record, with its new revision counts and the rules for its new status.
 */
export function updateMark(
  id: string,
  request: MarkUpdateRequest,
  byCertificate = false,
): Promise<MarkDetail> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPut<MarkDetail>(`/api/fta/marks/${encodeURIComponent(id)}${query}`, request);
}

/**
 * POST /api/fta/marks/{id}/submit — FTA510's Submit to HQ: a district's PA
 * application goes to Headquarters as PI. Returns the re-read mark.
 */
export function submitMark(
  id: string,
  revisionCount: number | null,
  byCertificate = false,
): Promise<MarkDetail> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiPost<MarkDetail>(`/api/fta/marks/${encodeURIComponent(id)}/submit${query}`, {
    revisionCount,
  });
}

/**
 * GET /api/fta/marks/{id} — private mark detail (FTA_510/511/513). The id is a
 * timber mark, or with `byCertificate` the certificate of an application that
 * has no timber mark yet.
 */
export function getMarkDetail(id: string, byCertificate = false): Promise<MarkDetail> {
  const query = byCertificate ? '?by=certificate' : '';
  return apiGet<MarkDetail>(`/api/fta/marks/${encodeURIComponent(id)}${query}`);
}

/** The detail route for a list row: by timber mark, else by certificate. */
export function markDetailPath(
  timberMark: string | null,
  certificate: string | null,
): string | null {
  if (timberMark) return `/marks/${encodeURIComponent(timberMark)}`;
  if (certificate) return `/marks/${encodeURIComponent(certificate)}?by=certificate`;
  return null;
}
