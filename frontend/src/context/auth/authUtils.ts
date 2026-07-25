import { env } from '@/env';

import {
  AVAILABLE_ROLES,
  validIdpProviders,
  type FamLoginUser,
  type IdpProviderType,
  type JWT,
  type ROLE_TYPE,
  type USER_PRIVILEGE_TYPE,
} from './types';

// ── Cookie helpers ───────────────────────────────────────────────────

/** Reads a browser cookie value by name. Returns '' if not found. */
export const getCookie = (name: string): string => {
  const cookie = document.cookie
    .split(';')
    .find((cookieValue) => cookieValue.trim().startsWith(name));
  return cookie ? (cookie.split('=')[1] ?? '') : '';
};

/**
 * Reads the Cognito **access token** from cookies set by AWS Amplify's
 * CookieStorage. This is the token sent to the backend API as a Bearer
 * token.
 *
 * Access tokens carry `cognito:groups` (for authorization) and `sub` but
 * do NOT carry the `custom:idp_*` profile claims — those live only in
 * the ID token.
 */
export const getAccessTokenFromCookie = (): string | undefined => {
  const baseCookieName = `CognitoIdentityServiceProvider.${env.VITE_USER_POOLS_WEB_CLIENT_ID}`;
  const userId = encodeURIComponent(getCookie(`${baseCookieName}.LastAuthUser`));
  if (userId) {
    const token = getCookie(`${baseCookieName}.${userId}.accessToken`);
    return token || undefined;
  }
  return undefined;
};

/**
 * Reads the Cognito **ID token** from cookies set by AWS Amplify's
 * CookieStorage. Used **only** on the frontend to populate the local
 * user profile (display name, email, IDP provider, etc.). Never sent to
 * the backend.
 */
export const getIdTokenFromCookie = (): string | undefined => {
  const baseCookieName = `CognitoIdentityServiceProvider.${env.VITE_USER_POOLS_WEB_CLIENT_ID}`;
  const userId = encodeURIComponent(getCookie(`${baseCookieName}.LastAuthUser`));
  if (userId) {
    const token = getCookie(`${baseCookieName}.${userId}.idToken`);
    return token || undefined;
  }
  return undefined;
};

// ── Token parsing ────────────────────────────────────────────────────

/**
 * Maps a possibly org-suffixed Cognito group (e.g. FTA_ADMIN_DPG) to its
 * canonical role (FTA_ADMIN). Mirrors the backend's role resolution.
 */
function canonicalRoleFor(group: string): ROLE_TYPE | undefined {
  for (const role of AVAILABLE_ROLES) {
    if (group === role || group.startsWith(`${role}_`)) return role;
  }
  return undefined;
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
 * Parses a Cognito ID token JWT into FTA's FamLoginUser shape. Extracts
 * display name, IDP provider, and rolls up Cognito groups into the
 * AVAILABLE_ROLES set.
 *
 * NOTE: Must be called with the **ID token**, not the access token —
 * only the ID token carries the `custom:idp_*` profile claims.
 */
export const parseToken = (idToken: JWT | undefined): FamLoginUser | undefined => {
  if (!idToken) return undefined;
  const decodedIdToken = idToken?.payload;
  const displayName = (decodedIdToken?.['custom:idp_display_name'] as string) || '';
  const idpProvider = validIdpProviders.includes(
    (decodedIdToken?.['custom:idp_name'] as string)?.toUpperCase() as IdpProviderType,
  )
    ? ((decodedIdToken?.['custom:idp_name'] as string).toUpperCase() as IdpProviderType)
    : undefined;
  const hasComma = displayName.includes(',');
  let [lastName, firstName] = hasComma ? displayName.split(', ') : displayName.split(' ');
  if (!hasComma) [lastName, firstName] = [firstName, lastName];
  const sanitizedFirstName = hasComma ? firstName?.split(' ')[0]?.trim() : firstName || '';
  const userName = (decodedIdToken?.['custom:idp_username'] as string) || '';
  const email = (decodedIdToken?.['email'] as string) || '';
  const cognitoGroups = extractGroups(decodedIdToken);
  const privileges = parsePrivileges(cognitoGroups);
  // No role stacking: collapse to the single highest effective role.
  const derivedRoles = Object.keys(privileges) as ROLE_TYPE[];
  const effectiveRole = highestRole(derivedRoles);
  return {
    userName,
    displayName,
    email,
    idpProvider,
    privileges,
    roles: effectiveRole ? [effectiveRole] : [],
    firstName: sanitizedFirstName,
    lastName,
    providerUsername: idpProvider ? `${idpProvider}\\${userName}` : undefined,
  };
};

/**
 * Parses Cognito group strings into a user privilege object. Recognises
 * groups that match any AVAILABLE_ROLES root (with or without an org-code
 * suffix). FTA is IDIR-only, so any suffix (e.g. an org code) flattens to
 * the global role — org-unit filtering is handled server-side.
 */
export function parsePrivileges(input: string[]): USER_PRIVILEGE_TYPE {
  const result: USER_PRIVILEGE_TYPE = {};
  for (const item of input) {
    const canonical = canonicalRoleFor(item);
    if (!canonical) continue;
    if (!(canonical in result)) {
      result[canonical] = null;
    }
  }
  return result;
}

/**
 * Extracts Cognito groups from a decoded JWT payload.
 */
export function extractGroups(decodedIdToken: object | undefined): string[] {
  if (!decodedIdToken) return [];
  if ('cognito:groups' in decodedIdToken) {
    return decodedIdToken['cognito:groups'] as string[];
  }
  return [];
}
