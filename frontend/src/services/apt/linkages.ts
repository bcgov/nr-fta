import { apiDelete, apiGet, apiPost, toQuery } from '../http';

/**
 * Linkages: which corporation controls which licensee (APT2's Related Clients, APTM9200) —
 * /api/fta/apt/related-clients. Clients are picked with FTA's forest client autocomplete; the
 * backend checks both against FOREST_CLIENT on create.
 */

/** One relationship. Mirrors the backend AptRelatedClientDto; the three codes are its key. */
export interface AptRelatedClient {
  /** The controlled licensee. */
  clientNumber: string;
  clientName: string | null;
  /** The controlling corporation. */
  parentClientNumber: string;
  parentClientName: string | null;
  relationshipCode: string;
}

export interface AptRelatedClientCreate {
  clientNumber: string;
  parentClientNumber: string;
  /** Blank means CORP (shown as CONTROLS), the only relationship APT2 created. */
  relationshipCode?: string;
}

/** The relationship APT2 created, and how it showed it. */
export const APT_RELATIONSHIP = { code: 'CORP', label: 'CONTROLS' } as const;

const RELATED = '/api/fta/apt/related-clients';

/** GET — APT_RELATED_CLIENTS_GET, ordered by corporation then licensee name. */
export function getAptRelatedClients(): Promise<AptRelatedClient[]> {
  return apiGet<AptRelatedClient[]>(RELATED);
}

/** POST — APT_RELATED_CLIENTS_INS. 400 for an unknown client, 409 for an existing link. */
export function createAptRelatedClient(
  request: AptRelatedClientCreate,
): Promise<AptRelatedClientCreate> {
  return apiPost<AptRelatedClientCreate>(RELATED, request);
}

/** DELETE — APT_RELATED_CLIENTS_DEL, keyed by all three codes. */
export function deleteAptRelatedClient(
  relationship: Pick<AptRelatedClient, 'clientNumber' | 'parentClientNumber' | 'relationshipCode'>,
): Promise<void> {
  return apiDelete<void>(
    `${RELATED}${toQuery({
      clientNumber: relationship.clientNumber,
      parentClientNumber: relationship.parentClientNumber,
      relationshipCode: relationship.relationshipCode,
    })}`,
  );
}
