import { apiGet, toQuery } from './http';

import type { PageableResponse } from './paging';

// Mirrors the backend ClientSearchDto (ca.bc.gov.nrs.fta.shared.dto), which
// mirrors the legacy THE.FTA_SIL_21_CLIENT_SEARCH_V002 rec_client_search_results
// record.
export interface ClientSearchResult {
  clientNumber: string | null;
  clientAcronym: string | null;
  displayClientNumber: string | null;
  clientName: string | null;
  legalFirstName: string | null;
  legalMiddleName: string | null;
  clientLocnCode: string | null;
  clientLocnName: string | null;
  city: string | null;
  clientStatusCode: string | null;
}

export interface ClientSearchParams {
  /** Exact match, unlike the other criteria. */
  clientNumber?: string;
  clientAcronym?: string;
  /** The client's surname / registered name. Legacy labels this "Last Name". */
  clientName?: string;
  legalFirstName?: string;
  legalMiddleName?: string;
  /** 0-indexed, following the backend. Carbon's Pagination is 1-indexed. */
  page?: number;
  size?: number;
}

/** GET /api/fta/clients — client search (FTA_SIL_21_CLIENT_SEARCH_V002). */
export function searchClients(
  params: ClientSearchParams,
): Promise<PageableResponse<ClientSearchResult>> {
  return apiGet<PageableResponse<ClientSearchResult>>(
    `/api/fta/clients${toQuery({ ...params })}`,
  );
}
