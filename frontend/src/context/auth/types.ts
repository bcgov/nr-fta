/**
 * Recognized CSS/Keycloak roles that map to FTA application roles. Mirrors the
 * backend's ca.bc.gov.nrs.fta.dto.Role enum — keep these two lists in sync.
 *
 * The codes are unchanged from the Cognito groups they replace, and FTA scopes
 * no role by district, region or forest client — org-unit filtering is a query
 * concern, not an authorisation one. So these arrive on the token spelled
 * exactly as written here, and exact matching stays correct.
 */
export const AVAILABLE_ROLES = ['FTA_ADMIN', 'FTA_VIEWER'] as const;

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
