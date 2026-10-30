import { expect, test } from './fixtures';
import {
  gotoPage,
  navSection,
  openNavSection,
  PAGES,
  pageHeading,
  sideNav,
  type PageDef,
} from './helpers/nav';
import { gotoProtected } from './utils';

/** Reaching pages through the menu, and how the menu itself behaves. */

test('every menu entry opens its page', async ({ page }) => {
  await gotoProtected(page, PAGES.welcome.path);

  for (const def of Object.values(PAGES) as PageDef[]) {
    if (!def.navId || !def.section) continue;
    await openNavSection(page, def.section);
    await page.getByTestId(`side-nav-link-${def.navId}`).click();
    await expect(page).toHaveURL(new RegExp(`${def.path}$`));
    await expect(pageHeading(page, def.heading)).toBeVisible();
  }
});

test('Search entries are named for what they find', async ({ page }) => {
  await gotoProtected(page, PAGES.welcome.path);
  await openNavSection(page, 'Search');
  await expect(sideNav(page).getByRole('link')).toHaveText([
    'Tenures',
    'Harvesting Authorities',
    'Timber Marks',
    'Cut Blocks',
    'Range Tenures',
    'Range Units',
    'Application Metrics Export',
    'Clients',
    'Management Units',
  ]);
});

test('only section headings carry an icon, and only the current page is bold', async ({ page }) => {
  await gotoPage(page, PAGES.tenureSearch);

  await expect(navSection(page, 'Search').locator('svg').first()).toBeVisible();
  await expect(sideNav(page).getByRole('link').locator('svg')).toHaveCount(0);

  const weight = (id: string) => page.getByTestId(`side-nav-link-${id}`).locator('span').first();
  await expect(weight(PAGES.tenureSearch.navId)).toHaveCSS('font-weight', '600');
  await expect(weight(PAGES.cutBlockSearch.navId)).toHaveCSS('font-weight', '400');
});

test('the section holding the current page opens on load, the others stay closed', async ({
  page,
}) => {
  await gotoPage(page, PAGES.markList);
  await expect(navSection(page, 'Private Marks')).toHaveAttribute('aria-expanded', 'true');
  await expect(navSection(page, 'Search')).toHaveAttribute('aria-expanded', 'false');
});

test('New Application opens its dialog over the current page', async ({ page }) => {
  await gotoPage(page, PAGES.tenureSearch);
  await openNavSection(page, 'Private Marks');
  await page.getByTestId('side-nav-link-marks-application').click();

  const dialog = page.getByRole('dialog', { name: 'New Mark Application' });
  await expect(dialog).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${PAGES.tenureSearch.path}$`));

  await dialog.getByRole('button', { name: 'Close' }).first().click();
  await expect(dialog).toBeHidden();
});

test('a deep link to the new application opens the dialog over the list', async ({ page }) => {
  await gotoProtected(page, '/marks/application');
  await expect(page.getByRole('dialog', { name: 'New Mark Application' })).toBeVisible();
});
