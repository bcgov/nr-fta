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
 *   FTA_HARVEST_AUTH_       all except Admin              cutting permit and cut block writes,
 *     SENIOR_ADMIN                                        in any district; read-only elsewhere
 *   FTA_HARVEST_AUTH_       all except Admin              as senior, without suspensions,
 *     DISTRICT_ADMIN                                      conditions, postponements, relabel,
 *                                                         delete, or post-issue terms and rollbacks
 *   FTA_APT_ADMIN           all except Admin              apportionment (APT) writes, any org
 *                                                         unit; read-only elsewhere
 *   FTA_VIEWER              all except Admin              read-only (GET only)
 *
 * The one exception to "no stacking" is APT: FTA_APT_ADMIN is checked against every role the
 * user holds (canEditApt, and APT pages in isPathAllowedForUser), so a harvest or timber mark
 * administrator who is also an APT administrator keeps both.
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
 * Whether the user may add a note to a tenure — FTA_ADMIN or either timber mark role. Legacy
 * FTA970 let anyone who could open the file add a note; every other tenure write is
 * FTA_ADMIN's ({@link canEdit}).
 */
export function canAddTenureNotes(user: FamLoginUser | null | undefined): boolean {
  return canEditMarks(user) || canEditHarvest(user);
}

/**
 * Whether the user may add and edit cutting permits and cut blocks — FTA_ADMIN or either harvest
 * role, in any district.
 */
export function canEditHarvest(user: FamLoginUser | null | undefined): boolean {
  const r = effectiveRole(user);
  return (
    r === 'FTA_ADMIN' ||
    r === 'FTA_HARVEST_AUTH_SENIOR_ADMIN' ||
    r === 'FTA_HARVEST_AUTH_DISTRICT_ADMIN'
  );
}

/**
 * Whether the user holds legacy's Senior Admin and Feature Admin rights over harvest
 * authorizations — FTA_ADMIN or FTA_HARVEST_AUTH_SENIOR_ADMIN: suspensions, conditions,
 * postponements, relabel, delete, and changing terms or rolling back a status after issue.
 */
export function canManageHarvest(user: FamLoginUser | null | undefined): boolean {
  const r = effectiveRole(user);
  return r === 'FTA_ADMIN' || r === 'FTA_HARVEST_AUTH_SENIOR_ADMIN';
}

/**
 * Whether the user may create, edit and delete apportionment (APT) records — FTA_ADMIN or
 * FTA_APT_ADMIN, in any org unit. Unlike the checks above this reads every role the user holds,
 * not just the effective one, so a higher role does not hide APT editing.
 */
export function canEditApt(user: FamLoginUser | null | undefined): boolean {
  const held = user?.privileges ?? {};
  return 'FTA_ADMIN' in held || 'FTA_APT_ADMIN' in held;
}

/** Where the APT pages live; matched as a prefix (exact or `prefix + "/"`). */
export const APT_PATH_PREFIX = '/apt';

function isAptPath(pathname: string): boolean {
  return pathname === APT_PATH_PREFIX || pathname.startsWith(`${APT_PATH_PREFIX}/`);
}

/**
 * The two timber mark roles. FTA_TIMBER_MARK_DISTRICT_ADMIN has exactly
 * FTA_TIMBER_MARK_HEADQUARTERS_ADMIN's pages; on a private mark it may change only the
 * marking requirements and instrument, and a cancelled (HX) mark's status to HI — the
 * backend's MarkEditRules decides. A district's new application starts PA, and its
 * certificate print marks the mark issued (HN → HI).
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

/**
 * Pages that exist only to make a change. A user who may not make it is not shown the page at
 * all — no menu entry, no button to it, and the route guard answers Forbidden — rather than a
 * page of disabled fields. React Router patterns, matched against the whole path.
 */
const WRITE_PAGES: ReadonlyArray<{
  pattern: string;
  mayOpen: (user: FamLoginUser | null | undefined) => boolean;
}> = [
  { pattern: '/tenures/add', mayOpen: canEdit },
  { pattern: '/harvesting-authority/:cpId/assign-marks', mayOpen: canEditHarvest },
  { pattern: '/harvesting-authority/:cpId/suspend-blocks', mayOpen: canManageHarvest },
  { pattern: '/cut-block/:blockId/relabel', mayOpen: canManageHarvest },
  { pattern: '/cut-block/:blockId/suspension', mayOpen: canManageHarvest },
  { pattern: '/marks/application', mayOpen: canEditMarks },
  { pattern: '/apt/management-units/new', mayOpen: canEditApt },
  { pattern: '/apt/management-units/:muId/edit', mayOpen: canEditApt },
];

/** False when `pathname` is a write-only page the user may not use. */
function mayOpenWritePage(user: FamLoginUser | null | undefined, pathname: string): boolean {
  const page = WRITE_PAGES.find(({ pattern }) => matchPath(pattern, pathname) !== null);
  return !page || page.mayOpen(user);
}

export function isPathAllowedForUser(
  user: FamLoginUser | null | undefined,
  pathname: string,
): boolean {
  if (!mayOpenWritePage(user, pathname)) return false;
  // APT pages are open to every role that reads them, and to an APT administrator whatever
  // role outranks it (a timber mark role's allow-list would otherwise shut them out).
  if (isAptPath(pathname) && canEditApt(user)) return true;
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
