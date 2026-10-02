import {
  Archive,
  Document,
  DocumentAdd,
  DocumentTasks,
  Download,
  Edit,
  Map,
  Save,
  Search,
  Settings,
  Tag,
  UserFollow,
} from '@carbon/icons-react';

import type { ROLE_TYPE } from '@/context/auth/types';

import { isPathAllowedForRole } from './access';

import type { ComponentType } from 'react';

// The SideNav is two levels: a small set of section headings, each holding a
// flat list of destinations. It mirrors the legacy FTA menu, which is a static
// JavaScript structure in `webapp/javascript/menuLinks.js` (lines 103-152)
// rendered by a DHTML menu engine — not a WebADE or Struts config, and the
// only place the menu is defined.
//
// Legacy declares five top-level menus (`NoOffFirstLineMenus=5`): Search,
// Tenures, Private Marks, Recreation and Admin. Labels and order below are
// legacy's verbatim, including where they read oddly.
//
// Four deliberate departures from legacy, each noted at the point it applies:
//   - Recreation is not here at all: that work moved to a separate internal
//     forestry application, so its menu and its Recreation Search entry are
//     dropped rather than left pointing nowhere.
//   - Legacy nests up to four levels under Admin; Carbon's SideNav supports
//     two, so Admin is flattened to a single list.
//   - Legacy's menu is not role-gated at all: every user sees all thirty items
//     and the server refuses the click. Here an entry is shown only when the
//     user's role may open its page (routes/access.ts): the admin screens are
//     hidden from viewers, and a Timber Mark Headquarters Administrator sees only Tenure
//     Search, Timber Mark Search and Private Marks.
//   - Legacy's `Links` submenu (seven external systems) is omitted: its URLs
//     come from servlet init-params per environment and have no home in this
//     app's configuration yet.
//
// Detail and tab screens (tenure detail, cutting-permit detail, mark detail
// and so on) are reached contextually from a search result, not from the nav,
// so they do not appear here.

/** One destination in the nav. */
export type MenuLeaf = {
  id: string;
  label: string;
  path: string;
  icon?: ComponentType;
  roles?: string[];
};

/** A heading with its destinations. */
export type MenuSection = {
  id: string;
  label: string;
  /**
   * Shown in the collapsed rail, which lists the sections rather than
   * every destination — clicking one opens the nav on that section.
   */
  icon: ComponentType;
  items: MenuLeaf[];
  roles?: string[];
};

export type MenuItem = MenuLeaf;

/** Admin screens are FTA_ADMIN only; read-only viewers never see them. */
const ADMIN_ONLY = ['FTA_ADMIN'];

const NAV: MenuSection[] = [
  {
    id: 'search',
    label: 'Search',
    icon: Search,
    items: [
      { id: 'search-tenure', label: 'Tenure Search', path: '/search/tenure', icon: Search },
      {
        id: 'search-harvesting-authority',
        label: 'Harvesting Authority Search',
        path: '/search/harvesting-authority',
        icon: DocumentTasks,
      },
      {
        id: 'search-timber-mark',
        label: 'Timber Mark Search',
        path: '/search/timber-mark',
        icon: Tag,
      },
      { id: 'search-cut-block', label: 'Cut Block Search', path: '/search/cut-block', icon: Map },
      {
        id: 'search-range-tenure',
        label: 'Range Tenure Search',
        path: '/search/range-tenure',
        icon: Document,
      },
      // Not on the legacy menu: FTA006 was reachable only from within the
      // Range screens. It has a search screen here, so it is offered directly.
      {
        id: 'search-range-unit',
        label: 'Range Unit Search',
        path: '/search/range-unit',
        icon: Map,
      },
      {
        id: 'search-metrics',
        label: 'Application Metrics Export',
        path: '/search/metrics',
        icon: Download,
      },
      // Legacy nests these two under a `Code Tables Search` submenu. Carbon
      // gives us two levels, and the submenu held nothing else, so they sit
      // directly under Search.
      { id: 'search-client', label: 'Client Search', path: '/search/client', icon: UserFollow },
      {
        id: 'search-management-unit',
        label: 'Management Unit Search',
        path: '/search/management-unit',
        icon: Document,
      },
    ],
  },
  {
    id: 'tenures',
    label: 'Tenures',
    icon: Document,
    items: [
      { id: 'tenure-add', label: 'Add Tenure', path: '/tenures/add', icon: DocumentAdd },
      { id: 'tenure-detail', label: 'Tenure', path: '/tenures', icon: Document },
    ],
  },
  {
    id: 'private-marks',
    label: 'Private Marks',
    icon: Tag,
    items: [
      // Legacy labels these "Application/Amendment List" and "Mark Application";
      // shortened here, as the section heading already says Private Marks.
      { id: 'marks-list', label: 'Application List', path: '/marks', icon: Tag },
      {
        id: 'marks-application',
        label: 'New Application',
        path: '/marks/application',
        icon: DocumentAdd,
      },
    ],
  },
  {
    id: 'admin',
    label: 'Administration',
    icon: Settings,
    roles: ADMIN_ONLY,
    items: [
      { id: 'admin-audit', label: 'Audit Report', path: '/admin/audit', icon: DocumentTasks },
      {
        id: 'admin-rents-fees',
        label: 'Annual Rents and Fees Preparation',
        path: '/admin/rents-fees',
        icon: Document,
      },
      {
        id: 'admin-mark-transfer',
        label: 'Timber Mark Transfer',
        path: '/admin/mark-transfer',
        icon: Tag,
      },
      // Legacy: Admin > Range Admin Functions > Manage Zone.
      { id: 'admin-range-zone', label: 'Manage Zone', path: '/admin/range-zone', icon: Map },
      {
        id: 'admin-org-unit',
        label: 'Organization Unit Maintenance',
        path: '/admin/org-unit',
        icon: Settings,
      },
      // Legacy: Admin > Range Billing Menu > Calculate Range Bills > these four.
      {
        id: 'admin-billing-tenure',
        label: 'Tenure Billing Instructions',
        path: '/admin/billing/tenure',
        icon: Document,
      },
      {
        id: 'admin-billing-invoice',
        label: 'Invoice Preview',
        path: '/admin/billing/invoice-preview',
        icon: Document,
      },
      {
        id: 'admin-billing-pre',
        label: 'Pre Billing Report',
        path: '/admin/billing/pre-billing',
        icon: Document,
      },
      {
        // Legacy spells this "Submision" in the menu while the screen itself
        // says "Submission". Corrected here.
        id: 'admin-billing-approval',
        label: 'Tenure Approval Submission',
        path: '/admin/billing/approval',
        icon: Edit,
      },
      // Legacy: Admin > Range Billing Menu > these three.
      {
        id: 'admin-billing-post',
        label: 'Post Billing Report',
        path: '/admin/billing/post-billing',
        icon: Document,
      },
      {
        id: 'admin-rates-fees',
        label: 'Rates and Fees Maintenance',
        path: '/admin/rates-fees',
        icon: Save,
      },
      // Legacy lists Archive Tenures twice — under Range Admin Functions and
      // again under Range Billing Menu, both pointing at FTA640. Once here.
      { id: 'admin-archive', label: 'Archive Tenures', path: '/admin/archive', icon: Archive },
    ],
  },
];

const isVisible = (userRoles: string[], required?: string[]) =>
  !required || required.length === 0 || required.some((r) => userRoles.includes(r));

/**
 * The nav sections visible to the user's effective role.
 *
 * <p>An entry is shown only when the role may open its page — the same rule
 * the route guard applies, so the menu never offers a page that answers
 * Forbidden. A section or entry may also carry its own `roles` allow-list; a
 * section whose every entry is filtered out is dropped.
 *
 * @param userRoles  the user's canonical FTA role(s); the first is effective.
 */
export function getMenuSections(userRoles: string[]): MenuSection[] {
  const role = userRoles[0] as ROLE_TYPE | undefined;
  return NAV.filter((section) => isVisible(userRoles, section.roles))
    .map((section) => ({
      ...section,
      items: section.items.filter(
        (item) => isVisible(userRoles, item.roles) && isPathAllowedForRole(role, item.path),
      ),
    }))
    .filter((section) => section.items.length > 0);
}

/** The id of the section containing `path`, or undefined if none does. */
export function sectionIdForPath(userRoles: string[], path: string): string | undefined {
  return getMenuSections(userRoles).find((section) =>
    section.items.some((item) => item.path === path),
  )?.id;
}
