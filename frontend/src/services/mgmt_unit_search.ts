import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

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
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/management-units — management-unit-type code list (PKG_SIL_CODE_LISTS). */
export function searchManagementUnits(
  params: MgmtUnitSearchParams,
): Promise<PageableResponse<MgmtUnitSearch>> {
  return apiGet<PageableResponse<MgmtUnitSearch>>(
    `/api/fta/management-units${toQuery({ ...params })}`,
  );
}
