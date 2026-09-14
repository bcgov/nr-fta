import {
  Add,
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
  Task,
  UserFollow,
} from '@carbon/icons-react';

import type { ComponentType } from 'react';

// Every menu entry is a leaf that renders as a <SideNavLink>. The nav is a
// single flat list — no <SideNavMenu> nesting — so each destination is one
// click from anywhere.
//
// Roles use the canonical FTA names from context/auth/types.ts (mirrors the
// backend ca.bc.gov.nrs.fta.dto.Role enum: FTA_ADMIN, FTA_VIEWER). An entry
// with no `roles` is shown to every authenticated user.
export type MenuLeaf = {
  id: string;
  label: string;
  path: string;
  icon?: ComponentType;
  roles?: string[];
};

export type MenuItem = MenuLeaf;

/** Admin screens are FTA_ADMIN only; read-only viewers never see them. */
const ADMIN_ONLY = ['FTA_ADMIN'];

// Source of truth for the SideNav. Mirrors the destinations of the legacy FTA
// app (menuLinks.js), previously grouped under Search / Tenures / Private
// Marks / Admin parents and now promoted to primary items.
//
// Detail / tab screens (tenure detail, cutting-permit detail, etc.) are
// reached contextually from a search-result list, not from the SideNav, so
// they don't appear here.
const NAV: MenuLeaf[] = [
  { id: 'inbox', label: 'Inbox', path: '/inbox', icon: Task },
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
  { id: 'search-range-unit', label: 'Range Unit Search', path: '/search/range-unit', icon: Map },
  { id: 'search-client', label: 'Client Search', path: '/search/client', icon: UserFollow },
  {
    id: 'search-management-unit',
    label: 'Management Unit Search',
    path: '/search/management-unit',
    icon: Document,
  },
  {
    id: 'search-metrics',
    label: 'Application Metrics Export',
    path: '/search/metrics',
    icon: Download,
  },
  { id: 'tenure-detail', label: 'Tenure', path: '/tenures', icon: Document },
  { id: 'tenure-add', label: 'Add Tenure', path: '/tenures/add', icon: DocumentAdd },
  { id: 'marks-list', label: 'Application/Amendment List', path: '/marks', icon: Tag },
  { id: 'marks-application', label: 'Mark Application', path: '/marks/application', icon: Add },
  {
    id: 'admin-audit',
    label: 'Audit Report',
    path: '/admin/audit',
    icon: DocumentTasks,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-rents-fees',
    label: 'Annual Rents & Fees',
    path: '/admin/rents-fees',
    icon: Document,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-mark-transfer',
    label: 'Timber Mark Transfer',
    path: '/admin/mark-transfer',
    icon: Tag,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-range-zone',
    label: 'Manage Range Zone',
    path: '/admin/range-zone',
    icon: Map,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-org-unit',
    label: 'Org Unit Maintenance',
    path: '/admin/org-unit',
    icon: Settings,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-billing-tenure',
    label: 'Tenure Billing Instructions',
    path: '/admin/billing/tenure',
    icon: Document,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-billing-invoice',
    label: 'Invoice Preview',
    path: '/admin/billing/invoice-preview',
    icon: Document,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-billing-pre',
    label: 'Pre Billing Report',
    path: '/admin/billing/pre-billing',
    icon: Document,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-billing-post',
    label: 'Post Billing Report',
    path: '/admin/billing/post-billing',
    icon: Document,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-billing-approval',
    label: 'Tenure Approval Submission',
    path: '/admin/billing/approval',
    icon: Edit,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-rates-fees',
    label: 'Rates & Fees Maintenance',
    path: '/admin/rates-fees',
    icon: Save,
    roles: ADMIN_ONLY,
  },
  {
    id: 'admin-archive',
    label: 'Archive Tenures',
    path: '/admin/archive',
    icon: Archive,
    roles: ADMIN_ONLY,
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
export function getMenuEntries(userRoles: string[]): MenuLeaf[] {
  const has = (required?: string[]) =>
    !required || required.length === 0 || required.some((r) => userRoles.includes(r));
  return NAV.filter((item) => has(item.roles));
}
