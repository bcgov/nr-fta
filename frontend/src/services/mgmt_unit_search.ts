import { apiGet, toQuery } from './http';

// Mirrors the backend MgmtUnitSearchDto (ca.bc.gov.nrs.fta.shared.dto), which
// mirrors the legacy THE.PKG_SIL_CODE_LISTS.GET_MGMT_UNIT_TYPE_CODE
// rec_mgmt_unit_type_results record.
export interface MgmtUnitSearch {
  mgmtUnitTypeCode: string;
  description: string | null;
  effectiveDate: string | null; // ISO date
  expiryDate: string | null; // ISO date
}

export interface MgmtUnitSearchParams {
  mgmtUnitTypeCode?: string;
  description?: string;
}

/** GET /api/fta/management-units — management-unit-type code list (PKG_SIL_CODE_LISTS). */
export function searchManagementUnits(params: MgmtUnitSearchParams): Promise<MgmtUnitSearch[]> {
  return apiGet<MgmtUnitSearch[]>(`/api/fta/management-units${toQuery({ ...params })}`);
}
