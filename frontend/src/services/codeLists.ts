import { apiGet } from './http';

/**
 * One option in a dropdown. Mirrors the backend `CodeOptionDto`: every `THE`
 * code table shares the same code/description shape, so one type serves all of
 * them.
 */
export interface CodeOption {
  code: string;
  description: string;
}

/**
 * How long a fetched code list is reused before it is requested again.
 *
 * The backend caches these lists too and clears its cache hourly, so a code-table edit can take
 * up to that long plus this to reach an open browser. Code tables change rarely enough that the
 * saving — no requests at all when moving between search screens — is worth it.
 */
const CODE_LIST_TTL_MS = 15 * 60 * 1000;

const cache = new Map<string, { expires: number; request: Promise<CodeOption[]> }>();

/**
 * Fetches a code list once and shares it until it expires.
 *
 * The promise itself is cached, not just the result, so screens that ask for the same list at
 * the same moment — as several search screens do on mount — share one request. A failed request
 * is dropped from the cache straight away, so the next caller retries rather than inheriting the
 * failure for the rest of the interval.
 */
function cached(path: string): Promise<CodeOption[]> {
  const hit = cache.get(path);
  if (hit && hit.expires > Date.now()) {
    return hit.request;
  }
  const request = apiGet<CodeOption[]>(path);
  cache.set(path, { expires: Date.now() + CODE_LIST_TTL_MS, request });
  request.catch(() => {
    if (cache.get(path)?.request === request) cache.delete(path);
  });
  return request;
}

const list = (name: string) => cached(`/api/fta/code-lists/${name}`);

/**
 * Administrative org units. The `code` is the numeric `ORG_UNIT_NO`, which is
 * what the search filters on; the description carries the familiar
 * "DCC - Cariboo Chilcotin" label.
 */
export const getOrgUnits = () => list('org-units');

export const getFileTypes = () => list('file-types');

/** Tenure file statuses — `TENURE_FILE_STATUS_CODE`, not `FILE_STATUS_CODE`. */
export const getFileStatuses = () => list('file-statuses');

/** Client types: A (main licensee), B (secondary), and so on. */
export const getFileClientTypes = () => list('file-client-types');

/** Sources for the associated-file filter. */
export const getFileSources = () => list('file-sources');

/** Map notation types — only relevant when the file type is `M01`. */
export const getMapNotationTypes = () => list('map-notation-types');

/** Range unit statuses, for the FTA006 range unit / pasture search. */
export const getRangeUnitStatuses = () => list('range-unit-statuses');

/** Cut block statuses, for the FTA003 cut block search. */
export const getBlockStatuses = () => list('block-statuses');

/**
 * Range zones, for the FTA001R range tenure search. The only code list that
 * takes a filter — pass a district org-unit number to narrow it, as the legacy
 * screen does when Admin Org Unit changes.
 */
export const getRangeZones = (adminDistrictNo?: string) =>
  cached(
    `/api/fta/code-lists/range-zones${adminDistrictNo ? `?adminDistrictNo=${encodeURIComponent(adminDistrictNo)}` : ''}`,
  );

/** Land districts, for the FTA002 timber mark search. */
export const getLandDistricts = () => list('land-districts');

/** Primary IDs, for the FTA002 timber mark search. */
export const getPrimaryIds = () => list('primary-ids');

/** Salvage types, shared by the FTA002 and FTA005 searches. */
export const getSalvageTypes = () => list('salvage-types');

/** Harvest authority statuses — FTA002's "Mark Status" and FTA005's "CP Status". */
export const getHarvestAuthStatuses = () => list('harvest-auth-statuses');

/**
 * Private mark statuses (HN, PA, PI, DV, HI, HX), for the FTA500
 * application/amendment list. Distinct from timber-mark statuses.
 */
export const getPrivateMarkStatuses = () => list('private-mark-statuses');

/**
 * Licence-to-cut codes — the FTA005 "Purpose" dropdown. It sits in the screen's
 * oil and gas box but filters `HARVESTING_AUTHORITY.licence_to_cut_code`.
 */
export const getLicenceToCutCodes = () => list('licence-to-cut-codes');

/**
 * Harvest authority client types — the FTA005 "Client Type" dropdown. Legacy
 * defaults to `L` (licensee of the cutting permit); leaving it unset makes the
 * search fall back to the file's `A` client instead.
 */
export const getHarvestAuthClientTypes = () => list('harvest-auth-client-types');
