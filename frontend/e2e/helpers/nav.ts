import { expect, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * Every top-level page reachable from the SideNav, keyed by a short id.
 *
 * `navId` is the SideNav link's `data-testid` suffix (`side-nav-link-<navId>`),
 * which is the entry's `label` in `src/routes/routePaths.ts`. `heading` is the
 * page's `<h1>` text, used as the "page rendered" signal.
 *
 * Kept in one place so specs agree on paths + headings, and so a renamed route
 * or heading fails in exactly one spot. Mirrors `NAV` in
 * `src/routes/routePaths.ts` — when that changes, change this.
 *
 * Detail routes (`/tenures/:fileId`, `/cut-block/:blockId`, `/range/:id`, …)
 * are deliberately absent: they need a real record id, so specs that exercise
 * them navigate from the corresponding search screen.
 */
export interface PageDef {
  path: string;
  heading: string | RegExp;
  navId?: string;
  /** Present when the page is gated to a role — see routes/access.ts. */
  role?: 'FTA_ADMIN';
}

export const PAGES = {
  // Home
  welcome: { path: '/welcome', heading: /Welcome/i },

  // Search
  tenureSearch: { path: '/search/tenure', heading: 'Tenure Search', navId: 'Tenure Search' },
  harvestingSearch: {
    path: '/search/harvesting-authority',
    heading: 'Harvesting Authority Search',
    navId: 'Harvesting Authority Search',
  },
  timberMarkSearch: {
    path: '/search/timber-mark',
    heading: 'Timber Mark Search',
    navId: 'Timber Mark Search',
  },
  cutBlockSearch: { path: '/search/cut-block', heading: 'Cut Block Search', navId: 'Cut Block Search' },
  rangeTenureSearch: {
    path: '/search/range-tenure',
    heading: 'Range Tenure Search',
    navId: 'Range Tenure Search',
  },
  rangeUnitSearch: {
    path: '/search/range-unit',
    heading: 'Range Unit Search',
    navId: 'Range Unit Search',
  },
  applicationMetrics: {
    path: '/search/metrics',
    heading: /Application Metrics/i,
    navId: 'Application Metrics Export',
  },
  clientSearch: { path: '/search/client', heading: 'Client Search', navId: 'Client Search' },
  managementUnitSearch: {
    path: '/search/management-unit',
    heading: 'Management Unit Search',
    navId: 'Management Unit Search',
  },

  // Inbox
  inbox: { path: '/inbox', heading: 'Inbox', navId: 'Inbox' },

  // Tenures
  addTenure: { path: '/tenures/add', heading: /Add Tenure/i, navId: 'Add Tenure' },
  tenures: { path: '/tenures', heading: /Tenure/i, navId: 'Tenure' },

  // Private Marks
  markList: { path: '/marks', heading: /Application\/Amendment List/i, navId: 'Application/Amendment List' },
  markApplication: {
    path: '/marks/application',
    heading: /Mark Application/i,
    navId: 'Mark Application',
  },

  // Admin — FTA_ADMIN only
  auditReport: { path: '/admin/audit', heading: 'Audit Report', navId: 'Audit Report', role: 'FTA_ADMIN' },
  rentsFees: {
    path: '/admin/rents-fees',
    heading: /Annual Rents & Fees/i,
    navId: 'Annual Rents & Fees',
    role: 'FTA_ADMIN',
  },
  markTransfer: {
    path: '/admin/mark-transfer',
    heading: /Timber Mark Transfer/i,
    navId: 'Timber Mark Transfer',
    role: 'FTA_ADMIN',
  },
  manageRangeZone: {
    path: '/admin/range-zone',
    heading: /Manage Range Zone/i,
    navId: 'Manage Range Zone',
    role: 'FTA_ADMIN',
  },
  orgUnit: {
    path: '/admin/org-unit',
    heading: /Org Unit Maintenance/i,
    navId: 'Org Unit Maintenance',
    role: 'FTA_ADMIN',
  },
  ratesFees: {
    path: '/admin/rates-fees',
    heading: /Rates & Fees Maintenance/i,
    navId: 'Rates & Fees Maintenance',
    role: 'FTA_ADMIN',
  },
  archiveTenures: {
    path: '/admin/archive',
    heading: /Archive Tenures/i,
    navId: 'Archive Tenures',
    role: 'FTA_ADMIN',
  },
} as const satisfies Record<string, PageDef>;

/** Navigate to a page and assert its `<h1>` rendered. */
export const gotoPage = async (page: Page, def: PageDef): Promise<void> => {
  await gotoProtected(page, def.path);
  await expect(page.getByRole('heading', { level: 1, name: def.heading })).toBeVisible();
};
