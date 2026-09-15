import { parseRoleName, SCOPE_DISTRICT } from './roleScope';
import {
  AVAILABLE_ROLES,
  validIdpProviders,
  type FamLoginUser,
  type IdpProviderType,
  type ROLE_TYPE,
  type USER_PRIVILEGE_TYPE,
} from './types';

/**
 * The claims FTA reads off a BC Gov SSO access token.
 *
 * Names follow the SSO identity-mappers reference for the *IDIR - MFA*
 * integration:
 * https://bcgov.github.io/sso-docs/advanced/identity-mappers#idir---mfa
 *
 * All of these ride the access token, which is the change that let the backend
 * drop its per-request `/oauth2/userInfo` call — and let this module stop
 * reading two different tokens out of two different cookies.
 */
export type KeycloakProfile = {
  /** `<guid>@azureidir`. The OIDC subject, stable per user per provider. */
  preferred_username?: string;
  idir_username?: string;
  idir_user_guid?: string;
  /** `azureidir` for the IDIR - MFA integration. */
  identity_provider?: string;
  display_name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  name?: string;
  /** Roles CSS attaches for the client the token was issued to. */
  client_roles?: string[];
  resource_access?: Record<string, { roles?: string[] }>;
  azp?: string;
  [claim: string]: unknown;
};

/**
 * FAM's own bookkeeping roles, which reach the token like any other role.
 *
 * Per-grant expiry dates are recorded in CSS as roles assigned to the person —
 * `FAM:EXPIRES:2026-09-30:FTA_ADMIN` — because a role is a name and nothing
 * else, so it is the only way CSS can record something about one grant. FTA
 * never matches one, but they must not be mistaken for privileges.
 */
const FAM_SIDECAR_PREFIX = 'FAM:';

/**
 * Normalises the realm's provider alias to the one name FTA knows.
 *
 * The standard realm federates IDIR through Azure AD and reports `azureidir`;
 * the legacy alias is `idir`. Both are IDIR as far as this application is
 * concerned. Anything else is left undefined rather than guessed at.
 *
 * Falls back to the `preferred_username` suffix (`<guid>@azureidir`), which the
 * identity-mappers reference documents and which is always present —
 * `identity_provider` is added by the broker rather than by a mapper.
 */
export const parseIdpProvider = (profile: KeycloakProfile): IdpProviderType | undefined => {
  const raw = profile.identity_provider ?? profile.preferred_username?.split('@')[1] ?? '';

  const normalized =
    raw.toLowerCase() === 'azureidir' || raw.toLowerCase() === 'idir' ? 'IDIR' : '';

  return validIdpProviders.includes(normalized as IdpProviderType)
    ? (normalized as IdpProviderType)
    : undefined;
};

/**
 * Reads the caller's roles for the client the token was issued to.
 *
 * Under CSS these arrive as `client_roles`. Falls back to
 * `resource_access.<azp>.roles`, which is where stock Keycloak puts them —
 * which one appears depends on the realm's mappers, so both are read.
 */
export const extractRoles = (profile: KeycloakProfile | undefined): string[] => {
  if (!profile) return [];

  const clientRoles = profile.client_roles;
  if (Array.isArray(clientRoles) && clientRoles.length > 0) {
    return clientRoles;
  }

  const clientId = profile.azp;
  if (!clientId) return [];

  const roles = profile.resource_access?.[clientId]?.roles;
  return Array.isArray(roles) ? roles : [];
};

/**
 * Parses role strings into a user privilege object.
 *
 * Recognises both spellings a role can arrive in, because FAM puts the scope in
 * the role name and nowhere else:
 *
 * - **Unscoped** — `FTA_ADMIN`, recorded as `null`, meaning "not narrowed".
 * - **District-scoped** — `FTA_ADMIN_DISTRICT-DCC`, recorded as `['DCC']`.
 *
 * A scoped holder **never carries the bare code**, so exact matching alone would
 * read a district-scoped administrator as holding no role at all and send them
 * to the Unauthorized page. (An earlier version matched exactly, on the
 * reasoning that nothing on a CSS token carries a suffix. That was true only
 * while FTA's roles were registered unscoped in FAM.)
 *
 * Several grants of one role merge: three districts arrive as three role names
 * and collapse to one entry with three values. An unscoped grant wins over
 * scoped ones — it is strictly broader, so narrowing it would be wrong.
 *
 * FAM's `FAM:`-prefixed bookkeeping roles are dropped explicitly rather than
 * left to fall through, so they cannot be read as privileges.
 */
export function parsePrivileges(input: string[]): USER_PRIVILEGE_TYPE {
  const result: USER_PRIVILEGE_TYPE = {};
  for (const item of input) {
    if (item.startsWith(FAM_SIDECAR_PREFIX)) continue;

    const { baseRole, scopes } = parseRoleName(item);
    if (!AVAILABLE_ROLES.includes(baseRole as ROLE_TYPE)) continue;

    const role = baseRole as ROLE_TYPE;
    const districts = scopes[SCOPE_DISTRICT] ?? [];

    if (districts.length === 0) {
      result[role] = null; // unscoped: broader than any scoped grant
      continue;
    }
    if (result[role] === null) continue; // already unscoped; do not narrow
    result[role] = [...(result[role] ?? []), ...districts];
  }
  return result;
}

/**
 * Role precedence, highest first. FTA_ADMIN (full CRUD) outranks
 * FTA_VIEWER (read-only). Mirrors the backend Role enum ordering.
 */
const ROLE_PRECEDENCE: ROLE_TYPE[] = ['FTA_ADMIN', 'FTA_VIEWER'];

/**
 * Collapses a user's granted roles to their single effective role (no
 * stacking). Admin wins over Viewer. Returns undefined when no role is held.
 */
export function highestRole(roles: ROLE_TYPE[]): ROLE_TYPE | undefined {
  return ROLE_PRECEDENCE.find((role) => roles.includes(role));
}

/**
 * Parses an oidc-client-ts profile into FTA's FamLoginUser shape.
 *
 * `given_name` and `family_name` are real claims on this realm, so the name is
 * read directly rather than split out of the display name — Cognito carried only
 * `custom:idp_display_name`, which forced a guess at whether "Smith, Jane" or
 * "Jane Smith" was intended.
 */
export const parseToken = (profile: KeycloakProfile | undefined): FamLoginUser | undefined => {
  if (!profile) return undefined;

  const idpProvider = parseIdpProvider(profile);
  const userName = profile.idir_username ?? '';
  const firstName = profile.given_name ?? '';
  const lastName = profile.family_name ?? '';
  const displayName =
    profile.display_name ?? profile.name ?? [firstName, lastName].filter(Boolean).join(' ');

  const privileges = parsePrivileges(extractRoles(profile));
  // No role stacking: collapse to the single highest effective role, which is
  // what routes/access.ts reads as `roles[0]`.
  const derivedRoles = Object.keys(privileges) as ROLE_TYPE[];
  const effectiveRole = highestRole(derivedRoles);

  return {
    userName,
    displayName,
    email: profile.email ?? '',
    idpProvider,
    privileges,
    roles: effectiveRole ? [effectiveRole] : [],
    firstName,
    lastName,
    providerUsername: idpProvider ? `${idpProvider}\\${userName}` : undefined,
  };
};
