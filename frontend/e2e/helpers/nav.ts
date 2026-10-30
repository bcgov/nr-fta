import { expect, type Locator, type Page } from '@playwright/test';

import { gotoProtected } from '../utils';

/**
 * Every top-level page reachable from the SideNav, keyed by a short id.
 *
 * `navId` is the SideNav link's `data-testid` suffix (`side-nav-link-<navId>`),
 * which is the entry's `id` — not its label — in `src/routes/routePaths.ts`.
 * `heading` is the page's `<h1>` text, used as the "page rendered" signal, and
 * is the title passed to `PageLayout`. Several of these differ from the nav
 * label (the menu's "Tenures" under Search opens "Tenure Search"), so take the
 * heading from the page, not the menu.
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
  /** The nav section holding this page, as labelled in the menu. */
  section?: string;
}

export const PAGES = {
  // Home — titled for the application, with the greeting as its subtitle.
  welcome: { path: '/welcome', heading: /Forest Tenure Administration/i },

  // Search
  tenureSearch: {
    path: '/search/tenure',
    heading: 'Tenure Search',
    navId: 'search-tenure',
    section: 'Search',
  },
  harvestingSearch: {
    path: '/search/harvesting-authority',
    heading: 'Harvesting Authority Search',
    navId: 'search-harvesting-authority',
    section: 'Search',
  },
  timberMarkSearch: {
    path: '/search/timber-mark',
    heading: 'Timber Mark Search',
    navId: 'search-timber-mark',
    section: 'Search',
  },
  cutBlockSearch: {
    path: '/search/cut-block',
    heading: 'Cut Block Search',
    navId: 'search-cut-block',
    section: 'Search',
  },
  rangeTenureSearch: {
    path: '/search/range-tenure',
    heading: 'Range Tenure Search',
    navId: 'search-range-tenure',
    section: 'Search',
  },
  rangeUnitSearch: {
    path: '/search/range-unit',
    heading: /Range Unit \/ Pasture Search/i,
    navId: 'search-range-unit',
    section: 'Search',
  },
  applicationMetrics: {
    path: '/search/metrics',
    heading: /Application Metrics/i,
    navId: 'search-metrics',
    section: 'Search',
  },
  clientSearch: {
    path: '/search/client',
    heading: 'Client Search',
    navId: 'search-client',
    section: 'Search',
  },
  managementUnitSearch: {
    path: '/search/management-unit',
    heading: 'Management Unit Search',
    navId: 'search-management-unit',
    section: 'Search',
  },

  // Tenures
  addTenure: {
    path: '/tenures/add',
    heading: /Add New Tenure/i,
    navId: 'tenure-add',
    section: 'Tenures',
  },
  tenures: { path: '/tenures', heading: 'Tenures', navId: 'tenure-detail', section: 'Tenures' },

  // Private Marks — nav label and page title differ; this is the title.
  markList: {
    path: '/marks',
    heading: /Private Mark Applications/i,
    navId: 'marks-list',
    section: 'Private Marks',
  },

  // Administration — FTA_ADMIN only
  auditReport: {
    path: '/admin/audit',
    heading: 'Audit Report',
    navId: 'admin-audit',
    section: 'Administration',
  },
  rentsFees: {
    path: '/admin/rents-fees',
    heading: /Annual Rents & Fees/i,
    navId: 'admin-rents-fees',
    section: 'Administration',
  },
  markTransfer: {
    path: '/admin/mark-transfer',
    heading: /Timber Mark Transfer/i,
    navId: 'admin-mark-transfer',
    section: 'Administration',
  },
  manageRangeZone: {
    path: '/admin/range-zone',
    heading: /Manage Range Zone/i,
    navId: 'admin-range-zone',
    section: 'Administration',
  },
  orgUnit: {
    path: '/admin/org-unit',
    heading: /Organization Unit Maintenance/i,
    navId: 'admin-org-unit',
    section: 'Administration',
  },
  billingTenure: {
    path: '/admin/billing/tenure',
    heading: 'Tenure Billing Instructions',
    navId: 'admin-billing-tenure',
    section: 'Administration',
  },
  billingInvoice: {
    path: '/admin/billing/invoice-preview',
    heading: 'Invoice Preview',
    navId: 'admin-billing-invoice',
    section: 'Administration',
  },
  billingPre: {
    path: '/admin/billing/pre-billing',
    heading: 'Pre Billing Report Submission',
    navId: 'admin-billing-pre',
    section: 'Administration',
  },
  billingApproval: {
    path: '/admin/billing/approval',
    heading: 'Tenure Approval Submission',
    navId: 'admin-billing-approval',
    section: 'Administration',
  },
  billingPost: {
    path: '/admin/billing/post-billing',
    heading: 'Post Billing Report Submission',
    navId: 'admin-billing-post',
    section: 'Administration',
  },
  ratesFees: {
    path: '/admin/rates-fees',
    heading: /Rates & Fees Maintenance/i,
    navId: 'admin-rates-fees',
    section: 'Administration',
  },
  archiveTenures: {
    path: '/admin/archive',
    heading: /Archive Tenures/i,
    navId: 'admin-archive',
    section: 'Administration',
  },
} as const satisfies Record<string, PageDef>;

/** The page's `<h1>`, the "page rendered" signal. */
export const pageHeading = (page: Page, heading: string | RegExp): Locator =>
  page.getByRole('heading', { level: 1, name: heading });

/** Navigate to a page and assert its `<h1>` rendered. */
export const gotoPage = async (page: Page, def: PageDef): Promise<void> => {
  await gotoProtected(page, def.path);
  await expect(pageHeading(page, def.heading)).toBeVisible();
};

/** The side navigation. */
export const sideNav = (page: Page): Locator =>
  page.getByRole('navigation', { name: 'Main navigation' });

/** A section heading in the expanded nav, which is a button that shows or hides its items. */
export const navSection = (page: Page, label: string): Locator =>
  sideNav(page).getByRole('button', { name: label, exact: true });

/** Open a nav section, so its destinations become visible. A no-op when already open. */
export const openNavSection = async (page: Page, label: string): Promise<void> => {
  const heading = navSection(page, label);
  if ((await heading.getAttribute('aria-expanded')) !== 'true') {
    await heading.click();
  }
  await expect(heading).toHaveAttribute('aria-expanded', 'true');
};

/**
 * Wait for a tab's or a page's own requests to finish, so a failing one is seen
 * before the test moves on and the fixture's checks in e2e/fixtures.ts catch it.
 */
export const settled = async (page: Page): Promise<void> => {
  await page.waitForLoadState('networkidle', { timeout: 15_000 });
};

/**
 * Open every enabled tab in a tablist, in order, waiting for each one's data.
 * Panels fetch when first opened, so this is what exercises their queries.
 *
 * @return the labels of the tabs opened
 */
export const walkTabs = async (page: Page, tablist: string): Promise<string[]> => {
  const list = page.getByRole('tablist', { name: tablist });
  await expect(list).toBeVisible();
  const tabs = list.getByRole('tab');

  const opened: string[] = [];
  for (let i = 0; i < (await tabs.count()); i += 1) {
    const tab = tabs.nth(i);
    if (await tab.isDisabled()) continue;
    await tab.click();
    await expect(tab).toHaveAttribute('aria-selected', 'true');
    await settled(page);
    opened.push((await tab.innerText()).trim());
  }
  return opened;
};
