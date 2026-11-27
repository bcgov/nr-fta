/**
 * Recognized CSS/Keycloak roles that map to FTA application roles. Mirrors the
 * backend's ca.bc.gov.nrs.fta.dto.Role enum — keep these two lists in sync.
 *
 * A role FAM grants for a district arrives as `<CODE>_DISTRICT-<ORG_UNIT_CODE>`;
 * authUtils.parsePrivileges records it under the bare code with its districts
 * (FTA_ADMIN and FTA_RANGE_DISTRICT_ADMIN are the ones whose scope matters).
 */
export const AVAILABLE_ROLES = [
  'FTA_ADMIN',
  'FTA_HARVEST_AUTH_SENIOR_ADMIN',
  'FTA_HARVEST_AUTH_DISTRICT_ADMIN',
  'FTA_TIMBER_MARK_HEADQUARTERS_ADMIN',
  'FTA_TIMBER_MARK_DISTRICT_ADMIN',
  'FTA_APT_ADMIN',
  'FTA_APT_VIEWER',
  'FTA_VIEWER',
  'FTA_RANGE_HEADQUARTERS_ADMIN',
  'FTA_RANGE_DISTRICT_ADMIN',
] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

// FTA is an internal ministry application — IDIR only (no BCeID Business).
export const validIdpProviders = ['IDIR'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

export type FamLoginUser = {
  providerUsername?: string;
  userName?: string;
  displayName?: string;
  email?: string;
  idpProvider?: IdpProviderType;
  roles?: ROLE_TYPE[];
  authToken?: string;
  exp?: number;
  privileges: USER_PRIVILEGE_TYPE;
  firstName?: string;
  lastName?: string;
};
