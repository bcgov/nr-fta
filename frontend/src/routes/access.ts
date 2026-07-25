import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

/**
 * Access rules, single-effective-role model (no stacking — a user resolves to
 * exactly one role; see authUtils.highestRole). Mirrored on the backend by
 * ApiAuthorizationCustomizer (URL-level hasAuthority checks).
 *
 *   Role         Menus                    Capability
 *   FTA_ADMIN    all (incl. Admin)        full CRUD — create / edit / delete
 *   FTA_VIEWER   all except Admin         read-only (GET only)
 */

/** The user's single effective role (or undefined when none). */
export function effectiveRole(
  user: FamLoginUser | null | undefined,
): ROLE_TYPE | undefined {
  return user?.roles?.[0];
}

/** @return true when the user's effective role is FTA_ADMIN. */
export function isAdministrator(user: FamLoginUser | null | undefined): boolean {
  return effectiveRole(user) === 'FTA_ADMIN';
}

/**
 * Whether the user may create / modify / delete content. FTA_ADMIN can write;
 * FTA_VIEWER is read-only. Screens use this to show or hide edit affordances
 * (the backend enforces it authoritatively via URL-level authority checks).
 */
export function canEdit(user: FamLoginUser | null | undefined): boolean {
  return isAdministrator(user);
}

/**
 * Page access by role — the source of truth for both the route guard
 * (App.tsx) and which paths a role may load. Prefix-matched (exact or
 * `prefix + "/"`). Only `/admin` is restricted; every other authenticated
 * page is readable by both roles.
 */
const PAGE_ROLES: ReadonlyArray<{ prefix: string; roles: ROLE_TYPE[] }> = [
  { prefix: '/admin', roles: ['FTA_ADMIN'] },
];

export function isPathAllowedForUser(
  user: FamLoginUser | null | undefined,
  pathname: string,
): boolean {
  const r = effectiveRole(user);
  const match = PAGE_ROLES.find(
    (p) => pathname === p.prefix || pathname.startsWith(`${p.prefix}/`),
  );
  if (!match) return true; // open to any authenticated role
  return !!r && match.roles.includes(r);
}

/**
 * Where to land the user on `/`, `/auth/callback`, or any page pulled out from
 * under them. Everyone lands on the Welcome home page — the legacy app's
 * post-login landing (fta00Welcome).
 */
export function defaultRouteForUser(_user: FamLoginUser | null | undefined): string {
  return '/welcome';
}
