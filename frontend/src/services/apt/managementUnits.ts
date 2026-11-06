import { apiDelete, apiGet, apiPost, apiPut, toQuery } from '../http';

/**
 * Management units, their history (AAC determinations), partitions and partition
 * apportionments — the reads under /api/fta/apt/{management-units,partitions,
 * partition-apportionments}. Field names are APT2's (nr-apt2 api.yaml), so the backend DTOs
 * mirror these exactly. Dates are ISO `YYYY-MM-DD`; timestamps ISO date-times.
 *
 * Other APT areas (commitments, reports) import the reads from here. The writes for these
 * resources live in this file too, below the reads.
 */

export interface AptManagementUnit {
  id: number;
  /** `<type> <number>` display name, e.g. "TSA 01". */
  mgmtUnitName: string | null;
  orgUnitNo: number | null;
  orgUnitName: string | null;
  /** Management unit type code (TSA, TFL…). */
  aptMgmtUnitSt: string;
  mgmtUnitNumber: string;
  /** Status code. */
  mgmtUnitStatusSt: string;
  statusEffectiveDate: string | null;
  /** The source management unit this one came from, if any. */
  muId: number | null;
  srcMgmtUnitName: string | null;
  orcsFileNumber: string | null;
  comments: string | null;
  addTimestamp: string | null;
  addUserid: string | null;
  updateTimestamp: string | null;
  updateUserid: string | null;
}

export interface AptManagementUnitSearchParams {
  id?: number;
  managementUnitType?: string;
  managementUnitNumber?: string;
  status?: string;
  effectiveDate?: string;
  orgUnitNumber?: number;
  orgUnitName?: string;
  orcsFileNumber?: string;
  sourceManagementUnitId?: number;
}

/** One AAC determination of a management unit (APT_MGMT_UNIT_HISTORY). */
export interface AptManagementUnitHistory {
  id: number;
  muId: number;
  determinationDate: string | null;
  effectiveDate: string | null;
  allowableAnnualCut: number | null;
  /** AAC in hectares. */
  allowableAnnualCutHa: number | null;
  inventoryRevDate: string | null;
  apportionmentDeterminationDt: string | null;
  apportionmentEffectiveDate: string | null;
  totalArea: number | null;
  productiveArea: number | null;
  longTermHarvestLevel: number | null;
  currentNetOperatingArea: number | null;
  futureHarvestLandbaseArea: number | null;
  comments: string | null;
  addTimestamp: string | null;
  addUserid: string | null;
  updateTimestamp: string | null;
  updateUserid: string | null;
}

export interface AptPartition {
  id: number;
  /** The management unit history this partition belongs to. */
  muhId: number;
  partitionCode: string;
  partitionDescription: string | null;
  allowableAnnualCut: number | null;
  allowableAnnualCutHa: number | null;
  comments: string | null;
  addTimestamp: string | null;
  addUserid: string | null;
  updateTimestamp: string | null;
  updateUserid: string | null;
  totalAac: number | null;
  totalAacHa: number | null;
  totalApportionmentAac: number | null;
  totalApportionmentAacHa: number | null;
}

export interface AptPartitionApportionment {
  id: number;
  partitionId: number;
  foaTypeCode: string;
  foaTypeName: string | null;
  allowableAnnualCut: number | null;
  allowableAnnualCutHa: number | null;
  comments: string | null;
  addTimestamp: string | null;
  addUserid: string | null;
  updateTimestamp: string | null;
  updateUserid: string | null;
  totalAac: number | null;
  totalAacHa: number | null;
  totalDistributionVolume: number | null;
}

const MU = '/api/fta/apt/management-units';

/** GET /api/fta/apt/management-units — APT_MANAGEMENT_UNITS_GET. */
export function searchAptManagementUnits(
  params: AptManagementUnitSearchParams = {},
): Promise<AptManagementUnit[]> {
  return apiGet<AptManagementUnit[]>(`${MU}${toQuery({ ...params })}`);
}

/** GET /api/fta/apt/management-units/{id} — 404 when there is none. */
export function getAptManagementUnit(id: number | string): Promise<AptManagementUnit> {
  return apiGet<AptManagementUnit>(`${MU}/${id}`);
}

/** GET /api/fta/apt/management-units/{id}/history — APT_MGMT_UNIT_HISTORIES_GET. */
export function getAptManagementUnitHistory(
  muId: number | string,
  historyId?: number | string,
): Promise<AptManagementUnitHistory[]> {
  return apiGet<AptManagementUnitHistory[]>(`${MU}/${muId}/history${toQuery({ historyId })}`);
}

/** GET /api/fta/apt/partitions — APT_PARTITIONS_GET, by id or by MU history. */
export function getAptPartitions(params: {
  id?: number | string;
  managementUnitHistoryId?: number | string;
}): Promise<AptPartition[]> {
  return apiGet<AptPartition[]>(`/api/fta/apt/partitions${toQuery({ ...params })}`);
}

/** GET /api/fta/apt/partition-apportionments — APT_PART_APPORTIONMENTS_GET. */
export function getAptPartitionApportionments(params: {
  id?: number | string;
  partitionId?: number | string;
}): Promise<AptPartitionApportionment[]> {
  return apiGet<AptPartitionApportionment[]>(
    `/api/fta/apt/partition-apportionments${toQuery({ ...params })}`,
  );
}

// ── Writes (owned by the management-unit area; add them below) ──

/** A management unit to create or update. The audit user comes from the token. */
export interface AptManagementUnitRequest {
  orgUnitNo: number;
  /** Type code (one character). */
  muType: string;
  /** Number within the type (up to 4 characters). */
  muNo: string;
  /** Status code. */
  status: string;
  /** Status effective date, `YYYY-MM-DD`. */
  effectiveDate: string;
  /** Source management unit id. */
  muId: number | null;
  orcsFileNumber: string | null;
  comments: string | null;
}

/** A code-table management unit APT has no record of yet (APT_MGMT_UNIT_UNASSIGNED_GET). */
export interface AptUnassignedManagementUnit {
  /** Type and number run together, e.g. `U01`. */
  managementUnitCode: string;
  managementUnitName: string | null;
  managementUnitType: string | null;
  managementUnitNumber: string | null;
}

/** An AAC determination to create or update. Dates are `YYYY-MM-DD`. */
export interface AptManagementUnitHistoryRequest {
  /**
   * On create only: a determination of the same unit whose partitions, apportionments and BCTS
   * distributions are copied onto the new one.
   */
  prevId?: number | null;
  determinationDate: string;
  effectiveDate: string;
  allowableAnnualCut: number;
  allowableAnnualCutHa: number | null;
  inventoryRevDate: string | null;
  apportionmentDeterminationDt: string | null;
  apportionmentEffectiveDate: string | null;
  totalArea: number | null;
  productiveArea: number | null;
  longTermHarvestLevel: number | null;
  currentNetOperatingArea: number | null;
  futureHarvestLandbaseArea: number | null;
  comments: string | null;
}

export interface AptPartitionRequest {
  /** Required on create; a partition stays with its determination on update. */
  muhId?: number;
  partitionCode: string;
  allowableAnnualCut: number;
  allowableAnnualCutHa: number | null;
  comments: string | null;
}

export interface AptPartitionApportionmentRequest {
  /** Required on create; an apportionment stays with its partition on update. */
  partitionId?: number;
  foaTypeCode: string;
  allowableAnnualCut: number;
  allowableAnnualCutHa: number | null;
  comments: string | null;
}

/** POST /api/fta/apt/management-units — 409 when that type and number already exist. */
export function createAptManagementUnit(
  body: AptManagementUnitRequest,
): Promise<AptManagementUnit> {
  return apiPost<AptManagementUnit>(MU, body);
}

/** PUT /api/fta/apt/management-units/{id}. */
export function updateAptManagementUnit(
  id: number | string,
  body: AptManagementUnitRequest,
): Promise<AptManagementUnit> {
  return apiPut<AptManagementUnit>(`${MU}/${id}`, body);
}

/** GET /api/fta/apt/unassigned-management-units — what a new management unit is picked from. */
export function getAptUnassignedManagementUnits(): Promise<AptUnassignedManagementUnit[]> {
  return apiGet<AptUnassignedManagementUnit[]>('/api/fta/apt/unassigned-management-units');
}

/** POST /api/fta/apt/management-units/{muId}/history. */
export function createAptManagementUnitHistory(
  muId: number | string,
  body: AptManagementUnitHistoryRequest,
): Promise<AptManagementUnitHistory> {
  return apiPost<AptManagementUnitHistory>(`${MU}/${muId}/history`, body);
}

/** PUT /api/fta/apt/management-units/{muId}/history/{historyId}. */
export function updateAptManagementUnitHistory(
  muId: number | string,
  historyId: number | string,
  body: AptManagementUnitHistoryRequest,
): Promise<AptManagementUnitHistory> {
  return apiPut<AptManagementUnitHistory>(`${MU}/${muId}/history/${historyId}`, body);
}

/** POST /api/fta/apt/partitions — 409 when the type is already used in the determination. */
export function createAptPartition(body: AptPartitionRequest): Promise<AptPartition> {
  return apiPost<AptPartition>('/api/fta/apt/partitions', body);
}

/** PUT /api/fta/apt/partitions/{id}. */
export function updateAptPartition(
  id: number | string,
  body: AptPartitionRequest,
): Promise<AptPartition> {
  return apiPut<AptPartition>(`/api/fta/apt/partitions/${id}`, body);
}

/** DELETE /api/fta/apt/partitions/{id} — 409 while apportionments refer to it. */
export function deleteAptPartition(id: number | string): Promise<void> {
  return apiDelete<void>(`/api/fta/apt/partitions/${id}`);
}

/** POST /api/fta/apt/partition-apportionments — 409 when the FOA type is already used. */
export function createAptPartitionApportionment(
  body: AptPartitionApportionmentRequest,
): Promise<AptPartitionApportionment> {
  return apiPost<AptPartitionApportionment>('/api/fta/apt/partition-apportionments', body);
}

/** PUT /api/fta/apt/partition-apportionments/{id}. */
export function updateAptPartitionApportionment(
  id: number | string,
  body: AptPartitionApportionmentRequest,
): Promise<AptPartitionApportionment> {
  return apiPut<AptPartitionApportionment>(`/api/fta/apt/partition-apportionments/${id}`, body);
}

/** DELETE /api/fta/apt/partition-apportionments/{id} — 409 while BCTS distributions refer to it. */
export function deleteAptPartitionApportionment(id: number | string): Promise<void> {
  return apiDelete<void>(`/api/fta/apt/partition-apportionments/${id}`);
}
