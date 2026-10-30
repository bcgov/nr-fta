import { apiDelete, apiGet, apiPost, apiPut, toQuery } from './http';

// Mirrors the backend SuspensionDtos (ca.bc.gov.nrs.fta.tenure.suspension).

export interface SuspensionCode {
  code: string;
  description: string;
}

export interface PermitBlock {
  cbSkey: number;
  cutBlockId: string;
  statusCode: string;
  statusDescription: string | null;
  plannedHarvestDate: string | null;
  plannedGrossArea: number | null;
  plannedNetArea: number | null;
  selectable: boolean;
}

export interface PermitSuspension {
  forestFileId: string;
  cuttingPermitId: string;
  effectiveDate: string | null;
  expiryDate: string | null;
  extendDate: string | null;
  blocks: PermitBlock[];
  partitionCodes: SuspensionCode[];
}

// Mirrors the backend SuspendBlocksRequest (ca.bc.gov.nrs.fta.tenure.dto).
// The cutting permit id is supplied via the URL path, not this body.
export interface SuspendBlocksRequest {
  forestFileId: string;
  cbSkeys: number[];
  suspendAllBlocks: boolean;
  partitionCode: string;
  suspOrderNumber: string;
  suspStartDate: string; // ISO date
  suspEndDate: string; // ISO date
}

/** GET /api/fta/cutting-permits/{cpId}/suspend-blocks — what FTA912 lists. */
export function getPermitSuspension(cpId: string, forestFileId: string): Promise<PermitSuspension> {
  return apiGet<PermitSuspension>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}/suspend-blocks${toQuery({ forestFileId })}`,
  );
}

/**
 * POST /api/fta/cutting-permits/{cpId}/suspend-blocks — suspend cut blocks on a
 * cutting permit. Returns the number of blocks suspended.
 */
export function suspendBlocks(
  cpId: string,
  req: SuspendBlocksRequest,
): Promise<{ suspended: number }> {
  return apiPost<{ suspended: number }>(
    `/api/fta/cutting-permits/${encodeURIComponent(cpId)}/suspend-blocks`,
    req,
  );
}

export interface Suspension {
  suspensionId: number;
  partitionCode: string;
  orderNumber: string;
  startDate: string | null;
  endDate: string | null;
  preStatusCode: string | null;
  preStatusDescription: string | null;
  monthsRemaining: number | null;
  revisionCount: number;
}

export interface BlockSuspension {
  cbSkey: number;
  forestFileId: string;
  cuttingPermitId: string | null;
  cutBlockId: string;
  statusCode: string;
  statusDescription: string | null;
  permitIssueDate: string | null;
  permitExpiryDate: string | null;
  permitExtendDate: string | null;
  suspension: Suspension | null;
  canSuspend: boolean;
  canEdit: boolean;
  partitionCodes: SuspensionCode[];
  postStatusCodes: SuspensionCode[];
}

export interface SuspensionCreateRequest {
  partitionCode: string;
  orderNumber: string;
  startDate: string; // ISO date
  endDate: string; // ISO date
}

export interface SuspensionUpdateRequest extends SuspensionCreateRequest {
  action: 'SAVE' | 'END';
  postStatusCode: string | null;
  updateAllBlocks: boolean;
  revisionCount: number;
}

const suspensionsPath = (cbSkey: string | number) =>
  `/api/fta/cut-blocks/${encodeURIComponent(cbSkey)}/suspensions`;

/** GET /api/fta/cut-blocks/{cbSkey}/suspensions — the block and its suspension (FTA914). */
export function getBlockSuspension(cbSkey: string | number): Promise<BlockSuspension> {
  return apiGet<BlockSuspension>(suspensionsPath(cbSkey));
}

/** POST /api/fta/cut-blocks/{cbSkey}/suspensions — FTA914's Suspend Block. */
export function suspendBlock(cbSkey: number, req: SuspensionCreateRequest): Promise<void> {
  return apiPost<void>(suspensionsPath(cbSkey), req);
}

/** PUT .../suspensions/{id} — FTA914's Save Suspension or End Suspension. */
export function updateSuspension(
  cbSkey: number,
  suspensionId: number,
  req: SuspensionUpdateRequest,
): Promise<void> {
  return apiPut<void>(`${suspensionsPath(cbSkey)}/${suspensionId}`, req);
}

/** DELETE .../suspensions/{id} — FTA914's Delete Suspension. */
export function deleteSuspension(
  cbSkey: number,
  suspensionId: number,
  req: { updateAllBlocks: boolean; revisionCount: number },
): Promise<void> {
  return apiDelete<void>(`${suspensionsPath(cbSkey)}/${suspensionId}`, req);
}
