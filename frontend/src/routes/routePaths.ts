import {
  Search,
  Document,
  Tag,
  Settings,
  Task,
} from '@carbon/icons-react';
import type { ComponentType } from 'react';

// Each menu entry is either a leaf (renders as <SideNavLink>) or a parent
// with children (renders as <SideNavMenu> + <SideNavMenuItem>s). Top-level
// `roles` gate the whole branch; if absent, the entry is shown to every
// authenticated user.
//
// Roles use the canonical FTA names from context/auth/types.ts (mirrors the
// backend ca.bc.gov.nrs.fta.dto.Role enum: FTA_ADMIN, FTA_VIEWER).
export type MenuLeaf = {
  id: string;
  label: string;
  path: string;
  icon?: ComponentType;
  roles?: string[];
};

export type MenuParent = {
  id: string;
  label: string;
  icon?: ComponentType;
  roles?: string[];
  children: MenuLeaf[];
};

export type MenuItem = MenuLeaf | MenuParent;

export function isMenuParent(item: MenuItem): item is MenuParent {
  return 'children' in item;
}

// Source of truth for the SideNav. Mirrors the top-level menu of the legacy
// FTA app (menuLinks.js: Search / Tenures / Private Marks / Recreation /
// Admin). Carbon's SideNav supports a single level of nesting, so each
// top-level menu is a parent with one level of leaf children.
//
// Detail / tab screens (tenure detail, cutting-permit detail, etc.) are
// reached contextually from a search-result list, not from the SideNav, so
// they don't appear here.
const NAV: MenuItem[] = [
  {
    id: 'search',
    label: 'Search',
    icon: Search,
    children: [
      { id: 'search-tenure', label: 'Tenure Search', path: '/search/tenure' },
      {
        id: 'search-harvesting-authority',
        label: 'Harvesting Authority Search',
        path: '/search/harvesting-authority',
      },
      { id: 'search-timber-mark', label: 'Timber Mark Search', path: '/search/timber-mark' },
      { id: 'search-cut-block', label: 'Cut Block Search', path: '/search/cut-block' },
      { id: 'search-range-tenure', label: 'Range Tenure Search', path: '/search/range-tenure' },
      { id: 'search-range-unit', label: 'Range Unit Search', path: '/search/range-unit' },
      { id: 'search-metrics', label: 'Application Metrics Export', path: '/search/metrics' },
      { id: 'search-client', label: 'Client Search', path: '/search/client' },
      {
        id: 'search-management-unit',
        label: 'Management Unit Search',
        path: '/search/management-unit',
      },
    ],
  },
  {
    id: 'inbox',
    label: 'Inbox',
    path: '/inbox',
    icon: Task,
  },
  {
    id: 'tenures',
    label: 'Tenures',
    icon: Document,
    children: [
      { id: 'tenure-add', label: 'Add Tenure', path: '/tenures/add' },
      { id: 'tenure-detail', label: 'Tenure', path: '/tenures' },
    ],
  },
  {
    id: 'marks',
    label: 'Private Marks',
    icon: Tag,
    children: [
      { id: 'marks-list', label: 'Application/Amendment List', path: '/marks' },
      { id: 'marks-application', label: 'Mark Application', path: '/marks/application' },
    ],
  },
  {
    // Admin functions — FTA_ADMIN only (read-only viewers never see it).
    id: 'admin',
    label: 'Admin',
    icon: Settings,
    roles: ['FTA_ADMIN'],
    children: [
      { id: 'admin-audit', label: 'Audit Report', path: '/admin/audit' },
      { id: 'admin-rents-fees', label: 'Annual Rents & Fees', path: '/admin/rents-fees' },
      { id: 'admin-mark-transfer', label: 'Timber Mark Transfer', path: '/admin/mark-transfer' },
      { id: 'admin-range-zone', label: 'Manage Range Zone', path: '/admin/range-zone' },
      { id: 'admin-org-unit', label: 'Org Unit Maintenance', path: '/admin/org-unit' },
      { id: 'admin-billing-tenure', label: 'Tenure Billing Instructions', path: '/admin/billing/tenure' },
      { id: 'admin-billing-invoice', label: 'Invoice Preview', path: '/admin/billing/invoice-preview' },
      { id: 'admin-billing-pre', label: 'Pre Billing Report', path: '/admin/billing/pre-billing' },
      { id: 'admin-billing-post', label: 'Post Billing Report', path: '/admin/billing/post-billing' },
      { id: 'admin-billing-approval', label: 'Tenure Approval Submission', path: '/admin/billing/approval' },
      { id: 'admin-rates-fees', label: 'Rates & Fees Maintenance', path: '/admin/rates-fees' },
      { id: 'admin-archive', label: 'Archive Tenures', path: '/admin/archive' },
    ],
  },
];

/**
 * The visible nav for the user's effective role. Every entry may carry a
 * `roles` allow-list; an entry with none is shown to every authenticated
 * user. With the no-stacking model {@code userRoles} is a single-element
 * array, so an entry shows iff its allow-list contains that role (or is
 * absent).
 *
 * @param userRoles  the user's canonical FTA role(s).
 */
export function getMenuEntries(userRoles: string[]): MenuItem[] {
  const has = (required?: string[]) =>
    !required || required.length === 0 || required.some((r) => userRoles.includes(r));
  return NAV.filter((item) => has(item.roles));
}
