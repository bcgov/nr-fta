import { expect, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * Every top-level page reachable from the SideNav, keyed by a short id.
 *
 * `navId` is the SideNav link's `data-testid` suffix (`side-nav-link-<navId>`),
 * which is the entry's `id` — not its label — in `src/routes/routePaths.ts`.
 * `heading` is the page's `<h1>` text, used as the "page rendered" signal, and
 * is the title passed to `PageLayout`. Several of these differ from the nav
 * label (the Private Marks screen is listed as "Application/Amendment List"
 * but titled "Private Marks"), so take the heading from the page, not the menu.
 *
 * Kept in one place so specs agree on paths + headings, and so a renamed route
 * or heading fails in exactly one spot. Mirrors `NAV` in
 * `src/routes/routePaths.ts` — when that changes, change this. That nav is a
 * flat list: every entry renders as a `SideNavLink`, so there are no submenu
 * test ids.
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
  // Home — titled for the application, with the greeting as its subtitle.
  welcome: { path: '/welcome', heading: /Forest Tenure Administration/i },

  // Search
  tenureSearch: { path: '/search/tenure', heading: 'Tenure Search', navId: 'search-tenure' },
  harvestingSearch: {
    path: '/search/harvesting-authority',
    heading: 'Harvesting Authority Search',
    navId: 'search-harvesting-authority',
  },
  timberMarkSearch: {
    path: '/search/timber-mark',
    heading: 'Timber Mark Search',
    navId: 'search-timber-mark',
  },
  cutBlockSearch: {
    path: '/search/cut-block',
    heading: 'Cut Block Search',
    navId: 'search-cut-block',
  },
  rangeTenureSearch: {
    path: '/search/range-tenure',
    heading: 'Range Tenure Search',
    navId: 'search-range-tenure',
  },
  rangeUnitSearch: {
    path: '/search/range-unit',
    heading: /Range Unit \/ Pasture Search/i,
    navId: 'search-range-unit',
  },
  applicationMetrics: {
    path: '/search/metrics',
    heading: /Application Metrics/i,
    navId: 'search-metrics',
  },
  clientSearch: { path: '/search/client', heading: 'Client Search', navId: 'search-client' },
  managementUnitSearch: {
    path: '/search/management-unit',
    heading: 'Management Unit Search',
    navId: 'search-management-unit',
  },

  // Inbox
  inbox: { path: '/inbox', heading: 'Inbox', navId: 'inbox' },

  // Tenures
  addTenure: { path: '/tenures/add', heading: /Add New Tenure/i, navId: 'tenure-add' },
  tenures: { path: '/tenures', heading: /Tenure/i, navId: 'tenure-detail' },

  // Private Marks — nav label and page title differ; this is the title.
  markList: {
    path: '/marks',
    heading: /Private Marks/i,
    navId: 'marks-list',
  },
  markApplication: {
    path: '/marks/application',
    heading: /Mark Application/i,
    navId: 'marks-application',
  },

  // Admin — FTA_ADMIN only
  auditReport: {
    path: '/admin/audit',
    heading: 'Audit Report',
    navId: 'admin-audit',
    role: 'FTA_ADMIN',
  },
  rentsFees: {
    path: '/admin/rents-fees',
    heading: /Annual Rents & Fees/i,
    navId: 'admin-rents-fees',
    role: 'FTA_ADMIN',
  },
  markTransfer: {
    path: '/admin/mark-transfer',
    heading: /Timber Mark Transfer/i,
    navId: 'admin-mark-transfer',
    role: 'FTA_ADMIN',
  },
  manageRangeZone: {
    path: '/admin/range-zone',
    heading: /Manage Range Zone/i,
    navId: 'admin-range-zone',
    role: 'FTA_ADMIN',
  },
  orgUnit: {
    path: '/admin/org-unit',
    heading: /Org Unit Maintenance/i,
    navId: 'admin-org-unit',
    role: 'FTA_ADMIN',
  },
  ratesFees: {
    path: '/admin/rates-fees',
    heading: /Rates & Fees Maintenance/i,
    navId: 'admin-rates-fees',
    role: 'FTA_ADMIN',
  },
  archiveTenures: {
    path: '/admin/archive',
    heading: /Archive Tenures/i,
    navId: 'admin-archive',
    role: 'FTA_ADMIN',
  },
} as const satisfies Record<string, PageDef>;

/** Navigate to a page and assert its `<h1>` rendered. */
export const gotoPage = async (page: Page, def: PageDef): Promise<void> => {
  await gotoProtected(page, def.path);
  await expect(page.getByRole('heading', { level: 1, name: def.heading })).toBeVisible();
};
