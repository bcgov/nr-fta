import { matchPath } from 'react-router-dom';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

/**
 * Access rules, single-effective-role model (no stacking — a user resolves to
 * exactly one role; see authUtils.highestRole). Mirrored on the backend by
 * ApiAuthorizationCustomizer (URL-level hasAuthority checks).
 *
 *   Role                    Menus                         Capability
 *   FTA_ADMIN               all (incl. Admin)             full CRUD — create / edit / delete
 *   FTA_TIMBER_MARK_HEADQUARTERS_ADMIN   Tenure + Timber Mark Search,  private-mark writes (mark
 *                           Private Marks, and the        application, amendment);
 *                           tenure / cutting-permit /     read-only everywhere else
 *                           mark details they open
 *   FTA_TIMBER_MARK_        as FTA_TIMBER_MARK_HEADQUARTERS_ADMIN      as FTA_TIMBER_MARK_HEADQUARTERS_ADMIN; its
 *     DISTRICT_ADMIN                                      applications start PA and go
 *                                                         to HQ by Submit to HQ; its
 *                                                         certificate print also marks
 *                                                         the mark issued (HN → HI)
 *   FTA_VIEWER              all except Admin              read-only (GET only)
 */

/** The user's single effective role (or undefined when none). */
export function effectiveRole(user: FamLoginUser | null | undefined): ROLE_TYPE | undefined {
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
 *
 * FTA_TIMBER_MARK_HEADQUARTERS_ADMIN is not an editor here: its writes are confined to
 * private marks, which ask {@link canEditMarks} instead.
 */
export function canEdit(user: FamLoginUser | null | undefined): boolean {
  return isAdministrator(user);
}

/**
 * Whether the user may submit and amend private marks — FTA_ADMIN, or
 * FTA_TIMBER_MARK_HEADQUARTERS_ADMIN, whose role exists for exactly this.
 */
export function canEditMarks(user: FamLoginUser | null | undefined): boolean {
  const r = effectiveRole(user);
  return r === 'FTA_ADMIN' || isTimberMarkRole(r);
}

/**
 * The two timber mark roles. FTA_TIMBER_MARK_DISTRICT_ADMIN has exactly
 * FTA_TIMBER_MARK_HEADQUARTERS_ADMIN's pages and rights; they differ only where legacy
 * FTA510 told a district from Headquarters, which the backend decides: a district's
 * new application starts PA and it submits it to Headquarters (Submit to HQ), and
 * its certificate print marks the mark issued (HN → HI).
 */
function isTimberMarkRole(role: ROLE_TYPE | undefined): boolean {
  return role === 'FTA_TIMBER_MARK_HEADQUARTERS_ADMIN' || role === 'FTA_TIMBER_MARK_DISTRICT_ADMIN';
}

/**
 * Pages restricted to some roles — prefix-matched (exact or `prefix + "/"`).
 * Every other authenticated page is open to FTA_ADMIN and FTA_VIEWER.
 */
const PAGE_ROLES: ReadonlyArray<{ prefix: string; roles: ROLE_TYPE[] }> = [
  { prefix: '/admin', roles: ['FTA_ADMIN'] },
];

/**
 * The only pages FTA_TIMBER_MARK_HEADQUARTERS_ADMIN may open — an allow-list, so a screen
 * added later stays out of reach until it is listed here. React Router
 * patterns, matched against the whole path.
 *
 * Timber Mark Search results open the cutting-permit detail, which is that
 * search's detail screen; the permit's own sub-screens (assign marks, suspend
 * blocks) are not included.
 */
const TIMBER_MARK_ADMIN_PAGES: readonly string[] = [
  '/welcome',
  '/search/tenure',
  '/search/timber-mark',
  '/tenures/:fileId',
  '/harvesting-authority/:cpId',
  '/marks',
  '/marks/application',
  '/marks/:markNumber',
];

/**
 * Paths a pattern above would match but that are a different screen: the
 * route table ranks `/tenures/add` above `/tenures/:fileId`, so it must not be
 * read as a tenure called "add".
 */
const TIMBER_MARK_ADMIN_EXCLUDED: readonly string[] = ['/tenures/add'];

/**
 * Page access by role — the source of truth for the route guard (App.tsx) and
 * for which nav entries a role is shown (routePaths.getMenuSections).
 */
export function isPathAllowedForRole(role: ROLE_TYPE | undefined, pathname: string): boolean {
  if (!role) return false;

  if (isTimberMarkRole(role)) {
    if (TIMBER_MARK_ADMIN_EXCLUDED.includes(pathname)) return false;
    return TIMBER_MARK_ADMIN_PAGES.some((pattern) => matchPath(pattern, pathname) !== null);
  }

  const match = PAGE_ROLES.find(
    (p) => pathname === p.prefix || pathname.startsWith(`${p.prefix}/`),
  );
  if (!match) return true; // open to any authenticated role
  return match.roles.includes(role);
}

export function isPathAllowedForUser(
  user: FamLoginUser | null | undefined,
  pathname: string,
): boolean {
  return isPathAllowedForRole(effectiveRole(user), pathname);
}

/**
 * Where to land the user on `/`, `/authCallback`, or any page pulled out from
 * under them. Everyone lands on the Welcome home page — the legacy app's
 * post-login landing (fta00Welcome).
 */
export function defaultRouteForUser(_user: FamLoginUser | null | undefined): string {
  return '/welcome';
}
