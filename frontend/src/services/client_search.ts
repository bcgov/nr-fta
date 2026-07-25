import { apiGet, toQuery } from './http';

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
  clientNumber?: string;
  clientAcronym?: string;
  clientName?: string;
  legalFirstName?: string;
  legalMiddleName?: string;
}

/** GET /api/fta/clients — client search (FTA_SIL_21_CLIENT_SEARCH_V002). */
export function searchClients(params: ClientSearchParams): Promise<ClientSearchResult[]> {
  return apiGet<ClientSearchResult[]>(`/api/fta/clients${toQuery({ ...params })}`);
}
